package de.gematik.demis.nps.service.validation;

/*-
 * #%L
 * notification-processing-service
 * %%
 * Copyright (C) 2025 gematik GmbH
 * %%
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the
 * European Commission – subsequent versions of the EUPL (the "Licence").
 * You may not use this work except in compliance with the Licence.
 *
 * You find a copy of the Licence in the "Licence" file or at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
 * In case of changes by gematik find details in the "Readme" file.
 *
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import static de.gematik.demis.nps.error.ErrorCode.FHIR_VALIDATION_ERROR;
import static de.gematik.demis.nps.error.ErrorCode.FHIR_VALIDATION_FATAL;
import static de.gematik.demis.nps.error.ErrorCode.LIFECYCLE_VALIDATION_ERROR;
import static de.gematik.demis.nps.error.ServiceCallErrorCode.LVS;
import static de.gematik.demis.nps.error.ServiceCallErrorCode.VS;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.nps.config.FeatureFlagsConfigProperties;
import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.error.NpsServiceException;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.service.notification.NotificationType;
import de.gematik.demis.service.base.error.ServiceCallException;
import feign.Response;
import feign.codec.Decoder;
import feign.codec.StringDecoder;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationValidator {

  private final ValidationServiceClient validationServiceClient;
  private final LifecycleValidationServiceClient lifecycleValidationServiceClient;

  private final FhirContext fhirContext;
  private final FeatureFlagsConfigProperties featureFlags;
  private final Decoder decoder = new StringDecoder();

  private boolean lvDiseaseActivated;
  private boolean relaxedValidationActivated;

  @PostConstruct
  public void init() {
    lvDiseaseActivated = featureFlags.isEnabled("lv_disease");
    relaxedValidationActivated = featureFlags.isEnabled("relaxed.validation");
  }

  private static void reduceIssuesSeverityToWarn(final OperationOutcome operationOutcome) {
    operationOutcome.getIssue().stream()
        .filter(
            issue ->
                issue.getSeverity() == IssueSeverity.FATAL
                    || issue.getSeverity() == IssueSeverity.ERROR)
        .forEach(issue -> issue.setSeverity(IssueSeverity.WARNING));
  }

  private static boolean isStatusSuccessful(final int status) {
    return status >= 200 && status < 300;
  }

  public InternalOperationOutcome validateFhir(
      final String fhirNotification, final MessageType contentType) {
    final String body;
    final int status;
    try (final Response response = callValidationService(fhirNotification, contentType)) {
      body = readResponse(response);
      status = response.status();
    }

    if (status != HttpStatus.UNPROCESSABLE_ENTITY.value() && !isStatusSuccessful(status)) {
      throw new ServiceCallException("service response: " + body, VS, status, null);
    }

    final OperationOutcome operationOutcome =
        fhirContext.newJsonParser().parseResource(OperationOutcome.class, body);

    if (isStatusSuccessful(status)) {
      log.debug("Fhir Bundle successfully validated.");
      return new InternalOperationOutcome(operationOutcome);
    } else if (relaxedValidationActivated) {
      RelaxedValidationResult validInRelaxedMode =
          isValidInRelaxedMode(fhirNotification, contentType);
      if (validInRelaxedMode.isValid) {
        log.info(
            "Fhir notification only valid with relaxed validation. Original validation output: {}",
            fhirResourceToJson(operationOutcome));
        reduceIssuesSeverityToWarn(operationOutcome);
        return new InternalOperationOutcome(operationOutcome, validInRelaxedMode.reparsedString);
      }
    }
    final boolean hasFatalIssue =
        operationOutcome.getIssue().stream()
            .anyMatch(issue -> issue.getSeverity() == IssueSeverity.FATAL);
    final ErrorCode errorCode = hasFatalIssue ? FHIR_VALIDATION_FATAL : FHIR_VALIDATION_ERROR;
    throw new NpsServiceException(errorCode, operationOutcome);
  }

  private record RelaxedValidationResult(boolean isValid, String reparsedString) {}

  private RelaxedValidationResult isValidInRelaxedMode(
      final String fhirNotification, final MessageType contentType) {
    final String parsedNotificationAsJson;
    try {
      IBaseResource resource = getParser(contentType).parseResource(fhirNotification);
      if (resource instanceof Parameters parameters) {
        resource = parameters.getParameterFirstRep().getResource();
      }
      parsedNotificationAsJson = fhirResourceToJson(resource);
    } catch (final RuntimeException e) {
      log.debug("Notification is not parseable", e);
      return new RelaxedValidationResult(false, null);
    }

    try (final Response response =
        callValidationService(parsedNotificationAsJson, MessageType.JSON)) {
      return new RelaxedValidationResult(
          isStatusSuccessful(response.status()), parsedNotificationAsJson);
    }
  }

  private String readResponse(final Response response) {
    try {
      return (String) decoder.decode(response, String.class);
    } catch (final IOException e) {
      throw new ServiceCallException("error reading response", VS, response.status(), e);
    }
  }

  private IParser getParser(final MessageType contentType) {
    return switch (contentType) {
      case JSON -> fhirContext.newJsonParser();
      case XML -> fhirContext.newXmlParser();
    };
  }

  private Response callValidationService(
      final String fhirNotification, final MessageType contentType) {
    return switch (contentType) {
      case JSON -> validationServiceClient.validateJsonBundle(fhirNotification);
      case XML -> validationServiceClient.validateXmlBundle(fhirNotification);
    };
  }

  public void validateLifecycle(final Notification notification) {
    if (notification.getType() == NotificationType.LABORATORY) {
      validateLaboratoryLifecycle(notification.getBundle());
    }
    if (notification.getType() == NotificationType.DISEASE) {
      validateDiseaseLifecycle(notification.getBundle());
    }
  }

  private void validateDiseaseLifecycle(Bundle bundle) {
    if (lvDiseaseActivated) {
      final String fhirAsJson = fhirResourceToJson(bundle);
      try (final Response response = lifecycleValidationServiceClient.validateDisease(fhirAsJson)) {
        if (isStatusSuccessful(response.status())) {
          log.debug("Disease Lifecycle of notification successfully validated.");
        } else {
          handleNotSuccessfulStatus(response);
        }
      }
    }
  }

  private void handleNotSuccessfulStatus(Response response) {
    if (response.status() == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
      final String message = readResponse(response);
      final var outcome = new OperationOutcome();
      outcome
          .addIssue()
          .setSeverity(IssueSeverity.ERROR)
          .setCode(OperationOutcome.IssueType.PROCESSING)
          .setDiagnostics(message);
      throw new NpsServiceException(LIFECYCLE_VALIDATION_ERROR, outcome);
    } else {
      throw new ServiceCallException(
          "service response: " + readResponse(response), LVS, response.status(), null);
    }
  }

  private String fhirResourceToJson(final IBaseResource bundle) {
    final IParser fhirJsonParser = fhirContext.newJsonParser();
    return fhirJsonParser.encodeResourceToString(bundle);
  }

  private void validateLaboratoryLifecycle(final Bundle bundle) {
    final String fhirAsJson = fhirResourceToJson(bundle);
    try (final Response response =
        lifecycleValidationServiceClient.validateLaboratory(fhirAsJson)) {
      if (isStatusSuccessful(response.status())) {
        log.debug("Laboratory Lifecycle of notification successfully validated.");
      } else handleNotSuccessfulStatus(response);
    }
  }
}

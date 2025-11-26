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

import static de.gematik.demis.nps.error.ErrorCode.LIFECYCLE_VALIDATION_ERROR;
import static de.gematik.demis.nps.error.ServiceCallErrorCode.LVS;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.gematik.demis.nps.config.FeatureFlagsConfigProperties;
import de.gematik.demis.nps.error.NpsServiceException;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.service.notification.NotificationType;
import de.gematik.demis.service.base.error.ServiceCallException;
import feign.Response;
import feign.codec.Decoder;
import feign.codec.StringDecoder;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.sparql.function.library.leviathan.log;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LifecycleValidationService {
  private final LifecycleValidationServiceClient lifecycleValidationServiceClient;
  private final Decoder decoder = new StringDecoder();
  private final FhirContext fhirContext;
  private final FeatureFlagsConfigProperties featureFlags;

  public void validateLifecycle(final Notification notification) {
    if (notification.getType() == NotificationType.LABORATORY) {
      validateLaboratoryLifecycle(notification.getBundle());
    }
    if (notification.getType() == NotificationType.DISEASE) {
      validateDiseaseLifecycle(notification.getBundle());
    }
  }

  private void validateDiseaseLifecycle(Bundle bundle) {
    final String fhirAsJson = fhirResourceToJson(bundle);
    try (final Response response = lifecycleValidationServiceClient.validateDisease(fhirAsJson)) {
      if (isStatusSuccessful(response.status())) {
        log.debug("Disease Lifecycle of notification successfully validated.");
      } else {
        handleNotSuccessfulStatus(response, "Disease Lifecycle of notification validation failed.");
      }
    }
  }

  private void handleNotSuccessfulStatus(Response response, String details) {
    if (response.status() == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
      final String message = readResponse(response);
      final var outcome = new OperationOutcome();
      outcome
          .addIssue()
          .setSeverity(OperationOutcome.IssueSeverity.ERROR)
          .setCode(OperationOutcome.IssueType.PROCESSING)
          .setDiagnostics(message);
      throw new NpsServiceException(LIFECYCLE_VALIDATION_ERROR, outcome, details);
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
      } else
        handleNotSuccessfulStatus(
            response, "Laboratory Lifecycle of notification validation failed.");
    }
  }

  private String readResponse(final Response response) {
    try {
      return (String) decoder.decode(response, String.class);
    } catch (final IOException e) {
      throw new ServiceCallException("error reading response", LVS, response.status(), e);
    }
  }

  private static boolean isStatusSuccessful(final int status) {
    return status >= 200 && status < 300;
  }
}

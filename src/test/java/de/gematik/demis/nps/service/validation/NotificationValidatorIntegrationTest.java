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
 * #L%
 */

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static de.gematik.demis.nps.error.ErrorCode.LIFECYCLE_VALIDATION_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity.FATAL;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.error.NpsServiceException;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.service.notification.NotificationType;
import de.gematik.demis.service.base.error.ServiceCallException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;

@SpringBootTest(
    properties = {
      "nps.client.validation=http://localhost:${wiremock.server.port}/VS",
      "nps.client.lifecycle-vs=http://localhost:${wiremock.server.port}/LVS",
      "nps.relaxed-validation=false",
      "feature.flag.lv_disease=true"
    })
@AutoConfigureWireMock(port = 0)
class NotificationValidatorIntegrationTest {
  private static final String ENDPOINT_VS = "/VS/$validate";

  private static final String REQUEST_BODY = "my body";
  private static final String RESPONSE_BODY =
      """
        {"outcome":"does not matter"}
""";

  @MockBean FhirContext fhirContext;
  @Autowired NotificationValidator underTest;

  private static void setupVS(
      final String contentType, final ResponseDefinitionBuilder responseDefBuilder) {
    stubFor(
        post(ENDPOINT_VS)
            .withHeader(CONTENT_TYPE, equalTo(contentType))
            .withHeader(ACCEPT, equalTo(APPLICATION_JSON_VALUE))
            .withRequestBody(equalTo(REQUEST_BODY))
            .willReturn(responseDefBuilder));
  }

  private IParser setupFhirJsonParserMock() {
    final IParser parser = Mockito.mock(IParser.class);
    when(fhirContext.newJsonParser()).thenReturn(parser);
    return parser;
  }

  private OperationOutcome mockParseOutcomeForResponse(final String stringToParse) {
    final IParser fhirJsonParser = setupFhirJsonParserMock();
    final OperationOutcome outcome = new OperationOutcome();
    outcome.setId("just for testing");
    when(fhirJsonParser.parseResource(OperationOutcome.class, stringToParse)).thenReturn(outcome);
    return outcome;
  }

  @ParameterizedTest
  @EnumSource(MessageType.class)
  void validationOkay(final MessageType messageType) {
    final String contentType =
        switch (messageType) {
          case JSON -> APPLICATION_JSON_VALUE;
          case XML -> APPLICATION_XML_VALUE;
        };
    setupVS(contentType, okJson(RESPONSE_BODY));
    final OperationOutcome outcome = mockParseOutcomeForResponse(RESPONSE_BODY);
    final OperationOutcome result = underTest.validateFhir(REQUEST_BODY, messageType);

    assertThat(result).isEqualTo(outcome);
  }

  @ParameterizedTest
  @EnumSource(
      value = IssueSeverity.class,
      names = {"ERROR", "FATAL"})
  void validationErrorOutcome(final IssueSeverity severity) {
    setupVS(
        APPLICATION_JSON_VALUE,
        WireMock.status(422)
            .withBody(RESPONSE_BODY)
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE));

    final OperationOutcome outcome = mockParseOutcomeForResponse(RESPONSE_BODY);
    outcome.addIssue().setSeverity(severity);

    final ErrorCode expectedErrorCode =
        severity == FATAL ? ErrorCode.FHIR_VALIDATION_FATAL : ErrorCode.FHIR_VALIDATION_ERROR;

    assertThatThrownBy(() -> underTest.validateFhir(REQUEST_BODY, MessageType.JSON))
        .isExactlyInstanceOf(NpsServiceException.class)
        .hasFieldOrPropertyWithValue("errorCode", expectedErrorCode.getCode())
        .hasFieldOrPropertyWithValue("responseStatus", expectedErrorCode.getHttpStatus())
        .hasFieldOrPropertyWithValue("operationOutcome", outcome);
  }

  @Test
  void validationCallException() {
    setupVS(APPLICATION_JSON_VALUE, WireMock.serverError());
    assertThatThrownBy(() -> underTest.validateFhir(REQUEST_BODY, MessageType.JSON))
        .isExactlyInstanceOf(ServiceCallException.class);
  }

  @Nested
  @DisplayName("Lifecycle validation for laboratory")
  class LaboratoryLifecycleValidationTest {
    private static final String ENDPOINT_LVS_LABORATORY = "/LVS/laboratory/$validate";

    private static void setupLVS(final ResponseDefinitionBuilder responseDefBuilder) {
      stubFor(
          post(ENDPOINT_LVS_LABORATORY)
              .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE))
              .withHeader(ACCEPT, equalTo(APPLICATION_JSON_VALUE))
              .withRequestBody(equalTo(REQUEST_BODY))
              .willReturn(responseDefBuilder));
    }

    private Notification setupLaboratoryRequest() {
      final var bundle = new Bundle();
      bundle.setId("my-bundle");
      final var notification =
          Notification.builder().bundle(bundle).type(NotificationType.LABORATORY).build();

      final var parser = setupFhirJsonParserMock();
      when(parser.encodeResourceToString(bundle)).thenReturn(REQUEST_BODY);

      return notification;
    }

    @Test
    void lifeCycleValidationForLaboratoryOkay() {
      final Notification notification = setupLaboratoryRequest();
      setupLVS(okJson(RESPONSE_BODY));

      Assertions.assertDoesNotThrow(() -> underTest.validateLifecycle(notification));
      WireMock.verify(postRequestedFor(urlEqualTo(ENDPOINT_LVS_LABORATORY)));
    }

    @Test
    void lifeCycleValidationForLaboratoryError() {
      final Notification notification = setupLaboratoryRequest();
      setupLVS(status(422).withBody(RESPONSE_BODY));

      final var exception =
          catchThrowableOfType(
              () -> underTest.validateLifecycle(notification), NpsServiceException.class);

      assertThat(exception)
          .isNotNull()
          .returns(LIFECYCLE_VALIDATION_ERROR.name(), NpsServiceException::getErrorCode)
          .returns(
              LIFECYCLE_VALIDATION_ERROR.getHttpStatus(), NpsServiceException::getResponseStatus);
      assertThat(exception.getOperationOutcome()).isNotNull();
      assertThat(exception.getOperationOutcome().getIssue())
          .hasSize(1)
          .first()
          .returns(IssueSeverity.ERROR, OperationOutcomeIssueComponent::getSeverity)
          .returns(IssueType.PROCESSING, OperationOutcomeIssueComponent::getCode)
          .returns(RESPONSE_BODY, OperationOutcomeIssueComponent::getDiagnostics);
    }

    @Test
    void lifecycleValidationCallException() {
      final Notification notification = setupLaboratoryRequest();
      setupLVS(serverError());
      assertThatThrownBy(() -> underTest.validateLifecycle(notification))
          .isExactlyInstanceOf(ServiceCallException.class);
    }
  }

  @Nested
  @DisplayName("Lifecycle validation for disease")
  class DiseaseLifecycleValidationTest {

    private static final String ENDPOINT_LVS_DISEASE = "/LVS/disease/$validate";

    private static void setupLVS(final ResponseDefinitionBuilder responseDefBuilder) {
      stubFor(
          post(ENDPOINT_LVS_DISEASE)
              .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE))
              .withHeader(ACCEPT, equalTo(APPLICATION_JSON_VALUE))
              .withRequestBody(equalTo(REQUEST_BODY))
              .willReturn(responseDefBuilder));
    }

    @Test
    void lifeCycleValidationForLaboratoryOkay() {
      final Notification notification = setupDiseaseRequest();
      setupLVS(okJson(RESPONSE_BODY));

      Assertions.assertDoesNotThrow(() -> underTest.validateLifecycle(notification));
      WireMock.verify(postRequestedFor(urlEqualTo(ENDPOINT_LVS_DISEASE)));
    }

    @Test
    void lifeCycleValidationForLaboratoryError() {
      final Notification notification = setupDiseaseRequest();
      setupLVS(status(422).withBody(RESPONSE_BODY));

      final var exception =
          catchThrowableOfType(
              () -> underTest.validateLifecycle(notification), NpsServiceException.class);

      assertThat(exception)
          .isNotNull()
          .returns(LIFECYCLE_VALIDATION_ERROR.name(), NpsServiceException::getErrorCode)
          .returns(
              LIFECYCLE_VALIDATION_ERROR.getHttpStatus(), NpsServiceException::getResponseStatus);
      assertThat(exception.getOperationOutcome()).isNotNull();
      assertThat(exception.getOperationOutcome().getIssue())
          .hasSize(1)
          .first()
          .returns(IssueSeverity.ERROR, OperationOutcomeIssueComponent::getSeverity)
          .returns(IssueType.PROCESSING, OperationOutcomeIssueComponent::getCode)
          .returns(RESPONSE_BODY, OperationOutcomeIssueComponent::getDiagnostics);
    }

    @Test
    void lifecycleValidationCallException() {
      final Notification notification = setupDiseaseRequest();
      setupLVS(serverError());
      assertThatThrownBy(() -> underTest.validateLifecycle(notification))
          .isExactlyInstanceOf(ServiceCallException.class);
    }

    private Notification setupDiseaseRequest() {
      final var bundle = new Bundle();
      bundle.setId("my-bundle");
      final var notification =
          Notification.builder().bundle(bundle).type(NotificationType.DISEASE).build();

      final var parser = setupFhirJsonParserMock();
      when(parser.encodeResourceToString(bundle)).thenReturn(REQUEST_BODY);

      return notification;
    }
  }
}

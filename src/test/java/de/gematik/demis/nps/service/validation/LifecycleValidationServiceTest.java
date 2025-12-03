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
 * For additional notes and disclaimer from gematik and in case of changes by gematik,
 * find details in the "Readme" file.
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
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import de.gematik.demis.nps.error.NpsServiceException;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.service.routing.RoutingData;
import de.gematik.demis.nps.test.RoutingDataUtil;
import de.gematik.demis.service.base.error.ServiceCallException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    properties = {
      "nps.client.validation=http://localhost:${wiremock.server.port}/VS",
      "nps.client.lifecycle-vs=http://localhost:${wiremock.server.port}/LVS",
      "feature.flag.relaxed_validation=false",
      "feature.flag.new_api_endpoints=false"
    })
@AutoConfigureWireMock(port = 0)
class LifecycleValidationServiceTest {

  private static final String REQUEST_BODY = "my body";
  private static final String RESPONSE_BODY =
      """
            {"outcome":"does not matter"}
            """;

  @MockitoBean private FhirContext fhirContext;

  @Autowired LifecycleValidationService underTest;

  private IParser setupFhirJsonParserMock() {
    final IParser parser = Mockito.mock(IParser.class);
    when(fhirContext.newJsonParser()).thenReturn(parser);
    return parser;
  }

  private static final String ENDPOINT_LVS_LABORATORY = "/LVS/laboratory/$validate";

  private static void setupLabLVS(final ResponseDefinitionBuilder responseDefBuilder) {
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
    final RoutingData routingData = RoutingDataUtil.emptyFor("");
    final var notification = Notification.builder().bundle(bundle).routingData(routingData).build();

    final var parser = setupFhirJsonParserMock();
    when(parser.encodeResourceToString(bundle)).thenReturn(REQUEST_BODY);

    return notification;
  }

  @Nested
  @DisplayName("Lifecycle validation for laboratory")
  class LaboratoryLifecycleValidationTest {

    @Test
    void lifeCycleValidationForLaboratoryOkay() {
      final Notification notification = setupLaboratoryRequest();
      setupLabLVS(okJson(RESPONSE_BODY));

      Assertions.assertDoesNotThrow(() -> underTest.validateLifecycle(notification));
      WireMock.verify(postRequestedFor(urlEqualTo(ENDPOINT_LVS_LABORATORY)));
    }

    @Test
    void lifeCycleValidationForLaboratoryError() {
      final Notification notification = setupLaboratoryRequest();
      setupLabLVS(status(422).withBody(RESPONSE_BODY));

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
          .returns(
              OperationOutcome.IssueSeverity.ERROR,
              OperationOutcome.OperationOutcomeIssueComponent::getSeverity)
          .returns(
              OperationOutcome.IssueType.PROCESSING,
              OperationOutcome.OperationOutcomeIssueComponent::getCode)
          .returns(RESPONSE_BODY, OperationOutcome.OperationOutcomeIssueComponent::getDiagnostics);
    }

    @Test
    void lifecycleValidationCallException() {
      final Notification notification = setupLaboratoryRequest();
      setupLabLVS(serverError());
      assertThatThrownBy(() -> underTest.validateLifecycle(notification))
          .isExactlyInstanceOf(ServiceCallException.class);
    }
  }

  private static final String ENDPOINT_LVS_DISEASE = "/LVS/disease/$validate";

  private static void setupDiseaseLVS(final ResponseDefinitionBuilder responseDefBuilder) {
    stubFor(
        post(ENDPOINT_LVS_DISEASE)
            .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE))
            .withHeader(ACCEPT, equalTo(APPLICATION_JSON_VALUE))
            .withRequestBody(equalTo(REQUEST_BODY))
            .willReturn(responseDefBuilder));
  }

  @Nested
  @DisplayName("Lifecycle validation for disease")
  class DiseaseLifecycleValidationTest {

    @Test
    void lifeCycleValidationForDiseaseOkay() {
      final Notification notification = setupDiseaseRequest();
      setupDiseaseLVS(okJson(RESPONSE_BODY));

      Assertions.assertDoesNotThrow(() -> underTest.validateLifecycle(notification));
      WireMock.verify(postRequestedFor(urlEqualTo(ENDPOINT_LVS_DISEASE)));
    }

    @Test
    void lifeCycleValidationForDiseaseError() {
      final Notification notification = setupDiseaseRequest();
      setupDiseaseLVS(status(422).withBody(RESPONSE_BODY));

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
          .returns(
              OperationOutcome.IssueSeverity.ERROR,
              OperationOutcome.OperationOutcomeIssueComponent::getSeverity)
          .returns(
              OperationOutcome.IssueType.PROCESSING,
              OperationOutcome.OperationOutcomeIssueComponent::getCode)
          .returns(RESPONSE_BODY, OperationOutcome.OperationOutcomeIssueComponent::getDiagnostics);
    }

    @Test
    void lifecycleValidationCallException() {
      final Notification notification = setupDiseaseRequest();
      setupDiseaseLVS(serverError());
      assertThatThrownBy(() -> underTest.validateLifecycle(notification))
          .isExactlyInstanceOf(ServiceCallException.class);
    }

    private Notification setupDiseaseRequest() {
      final var bundle = new Bundle();
      bundle.setId("my-bundle");
      final RoutingData routingData = RoutingDataUtil.empty61For("");
      final var notification =
          Notification.builder().bundle(bundle).routingData(routingData).build();

      final var parser = setupFhirJsonParserMock();
      when(parser.encodeResourceToString(bundle)).thenReturn(REQUEST_BODY);

      return notification;
    }
  }
}

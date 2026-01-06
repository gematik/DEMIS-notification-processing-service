package de.gematik.demis.nps.service.validation;

/*-
 * #%L
 * notification-processing-service
 * %%
 * Copyright (C) 2025 - 2026 gematik GmbH
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

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static de.gematik.demis.nps.service.validation.ValidationServiceClient.HEADER_FHIR_API_VERSION;
import static de.gematik.demis.nps.service.validation.ValidationServiceClient.HEADER_FHIR_PROFILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import de.gematik.demis.service.base.error.ServiceCallException;
import java.util.Map;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@SpringBootTest(
    properties = {
      "nps.client.validation=http://localhost:${wiremock.server.port}/VS",
      "nps.client.lifecycle-vs=http://localhost:${wiremock.server.port}/LVS",
      "feature.flag.relaxed_validation=false",
      "feature.flag.new_api_endpoints=false"
    })
@AutoConfigureWireMock(port = 0)
class NotificationValidatorIntegrationTest {
  private static final String ENDPOINT_VS = "/VS/$validate";

  private static final String REQUEST_BODY = "my body";
  private static final String RESPONSE_BODY =
      """
      {"outcome":"does not matter"}
      """;

  @MockitoBean private FhirContext fhirContext;

  @Autowired NotificationValidator underTest;

  @BeforeEach
  void beforeEach() {
    WireMock.reset();
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

  private static void setupVS(
      final String contentType,
      final String version,
      final String profile,
      final ResponseDefinitionBuilder responseDefBuilder) {
    stubFor(
        post(ENDPOINT_VS)
            .withHeader(CONTENT_TYPE, equalTo(contentType))
            .withHeader(ACCEPT, equalTo(APPLICATION_JSON_VALUE))
            .withHeader(HEADER_FHIR_API_VERSION, version == null ? absent() : equalTo(version))
            .withHeader(HEADER_FHIR_PROFILE, profile == null ? absent() : equalTo(profile))
            .withRequestBody(equalTo(REQUEST_BODY))
            .willReturn(responseDefBuilder));
  }

  private static void setupVS(
      final String contentType, final ResponseDefinitionBuilder responseDefBuilder) {
    setupVS(contentType, null, null, responseDefBuilder);
  }

  private static void setRequestHeaders(final Map<String, String> headers) {
    final var mockRequest = new MockHttpServletRequest();
    for (var entry : headers.entrySet()) {
      mockRequest.addHeader(entry.getKey(), entry.getValue());
    }
    final var attrs = new ServletRequestAttributes(mockRequest);
    RequestContextHolder.setRequestAttributes(attrs);
  }

  @Nested
  class ValidationServiceTest {

    @AfterEach
    void tearDown() {
      RequestContextHolder.resetRequestAttributes();
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
      final InternalOperationOutcome result = underTest.validateFhir(REQUEST_BODY, messageType);
      assertThat(result.operationOutcome()).isEqualTo(outcome);
    }

    @ParameterizedTest
    @EnumSource(MessageType.class)
    void validationOkayWithSpecificVersion(final MessageType messageType) {
      final String apiVersion = "6";
      final String profile = "fhir-profile-snapshots";
      setRequestHeaders(Map.of(HEADER_FHIR_API_VERSION, apiVersion, HEADER_FHIR_PROFILE, profile));
      final String contentType =
          switch (messageType) {
            case JSON -> APPLICATION_JSON_VALUE;
            case XML -> APPLICATION_XML_VALUE;
          };
      setupVS(contentType, apiVersion, null, okJson(RESPONSE_BODY));
      final OperationOutcome outcome = mockParseOutcomeForResponse(RESPONSE_BODY);
      final InternalOperationOutcome result = underTest.validateFhir(REQUEST_BODY, messageType);

      assertThat(result.operationOutcome()).isEqualTo(outcome);
    }

    @ParameterizedTest
    @EnumSource(MessageType.class)
    void validationOkayWithoutSpecificVersion(final MessageType messageType) {
      final String profile = "fhir-profile-snapshots";
      setRequestHeaders(Map.of(HEADER_FHIR_PROFILE, profile));
      final String contentType =
          switch (messageType) {
            case JSON -> APPLICATION_JSON_VALUE;
            case XML -> APPLICATION_XML_VALUE;
          };
      setupVS(contentType, null, null, okJson(RESPONSE_BODY));
      final OperationOutcome outcome = mockParseOutcomeForResponse(RESPONSE_BODY);
      final InternalOperationOutcome result = underTest.validateFhir(REQUEST_BODY, messageType);

      assertThat(result.operationOutcome()).isEqualTo(outcome);
    }

    @ParameterizedTest
    @EnumSource(
        value = IssueSeverity.class,
        names = {"ERROR", "FATAL"})
    void validationErrorOutcome(final IssueSeverity severity) {
      setupVS(
          APPLICATION_JSON_VALUE,
          status(422).withBody(RESPONSE_BODY).withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE));

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
      setupVS(APPLICATION_JSON_VALUE, serverError());
      assertThatThrownBy(() -> underTest.validateFhir(REQUEST_BODY, MessageType.JSON))
          .isExactlyInstanceOf(ServiceCallException.class);
    }
  }
}

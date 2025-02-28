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
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.service.notification.NotificationType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
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
      "feature.flag.lv_disease=false"
    })
@AutoConfigureWireMock(port = 0)
class NotificationValidatorIntegrationREGRESSIONTest {
  private static final String ENDPOINT_VS = "/VS/$validate";

  private static final String REQUEST_BODY = "my body";
  private static final String RESPONSE_BODY =
      """
        {"outcome":"does not matter"}
""";

  @MockBean FhirContext fhirContext;
  @Autowired NotificationValidator underTest;

  @Test
  void lifeCycleValidationForDisease() {
    final Notification notification = Notification.builder().type(NotificationType.DISEASE).build();
    Assertions.assertDoesNotThrow(() -> underTest.validateLifecycle(notification));
  }

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
}

package de.gematik.demis.nps.service.pseudonymization;

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
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import de.gematik.demis.nps.error.ServiceCallErrorCode;
import de.gematik.demis.service.base.error.ServiceCallException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;

@SpringBootTest(properties = "nps.client.pseudonymization=http://localhost:${wiremock.server.port}")
@AutoConfigureWireMock(port = 0)
class PseudonymizationServiceClientIntegrationTest {

  private static final String EXPECTED_REQUEST_BODY =
"""
{
    "notificationBundleId": "2a40dccb-18da-5a9f-a11e-4ef3e0fd2096",
    "type": "demisPseudonymizationRequest",
    "diseaseCode": "cvd",
    "familyName": "Mustermann",
    "firstName": "Maxime",
    "dateOfBirth": "11.02.1950"
}
""";

  private static final String RESPONSE_BODY =
"""
{
    "type": "demisPseudonym",
    "outdatedPseudonym": {
        "familyName": [
            "XXXa"
        ],
        "firstName": [
            "XXXb"
        ],
        "dateOfBirth": "XXXc",
        "diseaseCode": "cvd"
    },
    "activePseudonym": {
        "familyName": [
            "YYYa"
        ],
        "firstName": [
            "YYYb"
        ],
        "dateOfBirth": "YYYc",
        "diseaseCode": "cvd"
    }
}
    """;
  private static final PseudonymizationRequest REQUEST =
      new PseudonymizationRequest(
          "2a40dccb-18da-5a9f-a11e-4ef3e0fd2096", "cvd", "Mustermann", "Maxime", "11.02.1950");

  private static final PseudonymizationResponse EXPECTED_RESPONSE =
      new PseudonymizationResponse(
          "demisPseudonym",
          new Pseudonym(List.of("XXXa"), List.of("XXXb"), "XXXc", "cvd"),
          new Pseudonym(List.of("YYYa"), List.of("YYYb"), "YYYc", "cvd"));

  @Autowired PseudonymizationServiceClient underTest;

  private static void setupRemoteService(final ResponseDefinitionBuilder responseDefBuilder) {
    stubFor(
        post("/pseudonymization")
            .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE))
            .withHeader(ACCEPT, equalTo(APPLICATION_JSON_VALUE))
            .withRequestBody(equalToJson(EXPECTED_REQUEST_BODY))
            .willReturn(responseDefBuilder));
  }

  @Test
  void success() {
    setupRemoteService(okJson(RESPONSE_BODY));
    final PseudonymizationResponse result = underTest.generatePseudonym(REQUEST);
    assertThat(result).isEqualTo(EXPECTED_RESPONSE);
  }

  @Test
  void error() {
    setupRemoteService(serverError());

    final ServiceCallException ex =
        catchThrowableOfType(
            () -> underTest.generatePseudonym(REQUEST), ServiceCallException.class);

    assertThat(ex)
        .isNotNull()
        .returns(500, ServiceCallException::getHttpStatus)
        .returns(ServiceCallErrorCode.PS, ServiceCallException::getErrorCode);
  }
}

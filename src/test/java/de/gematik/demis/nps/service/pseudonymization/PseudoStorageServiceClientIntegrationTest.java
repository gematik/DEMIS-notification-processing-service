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
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import de.gematik.demis.nps.error.ServiceCallErrorCode;
import de.gematik.demis.service.base.error.ServiceCallException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;

@SpringBootTest(properties = "nps.client.pseudo-storage=http://localhost:${wiremock.server.port}")
@AutoConfigureWireMock(port = 0)
class PseudoStorageServiceClientIntegrationTest {

  private static final String ENDPOINT = "/demis/storage";

  private static final String EXPECTED_REQUEST_BODY =
"""
{
    "type": "demisStorageRequest",
    "pseudonym": {
        "familyName": [
            "XXXa"
        ],
        "firstName": [
            "XXXb"
        ],
        "dateOfBirth": "XXXc",
        "diseaseCode": "cvd"
    },
    "notificationBundleId": "2a40dccb-18da-5a9f-a11e-4ef3e0fd2096",
    "pho": "1.52.03"
}
""";

  private static final String RESPONSE_BODY =
"""
""";

  private static final PseudonymStorageRequest REQUEST =
      new PseudonymStorageRequest(
          new Pseudonym(List.of("XXXa"), List.of("XXXb"), "XXXc", "cvd"),
          "2a40dccb-18da-5a9f-a11e-4ef3e0fd2096",
          "1.52.03");

  @Autowired PseudoStorageServiceClient underTest;

  private static void setupRemoteService(final ResponseDefinitionBuilder responseDefBuilder) {
    stubFor(
        post(ENDPOINT)
            .withHeader(CONTENT_TYPE, equalTo("application/vnd.demis_storage+json"))
            .withHeader(ACCEPT, equalTo("application/vnd.demis_storage+json"))
            .withRequestBody(equalToJson(EXPECTED_REQUEST_BODY))
            .willReturn(responseDefBuilder));
  }

  @Test
  void success() {
    setupRemoteService(okJson(RESPONSE_BODY));
    underTest.store(REQUEST);
    WireMock.verify(postRequestedFor(urlEqualTo(ENDPOINT)));
  }

  @Test
  void error() {
    setupRemoteService(serverError());

    final ServiceCallException ex =
        catchThrowableOfType(() -> underTest.store(REQUEST), ServiceCallException.class);

    assertThat(ex)
        .isNotNull()
        .returns(500, ServiceCallException::getHttpStatus)
        .returns(ServiceCallErrorCode.PSS, ServiceCallException::getErrorCode);
  }
}

package de.gematik.demis.nps.service.routing;

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
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import de.gematik.demis.service.base.error.ServiceCallException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;

@SpringBootTest(properties = "nps.client.routing=http://localhost:${wiremock.server.port}")
@AutoConfigureWireMock(port = 0)
class NotificationRoutingServiceClientIntegrationTest {

  private static final String FHIR_JSON = "does not matter";
  private static final String RESPONSE =
      """
          {"healthOffices":
              {
               "NOTIFIED_PERSON_PRIMARY":"1.4",
               "NOTIFIED_PERSON_ORDINARY":"1.3",
               "NOTIFIED_PERSON_CURRENT":"1.2",
               "NOTIFIED_PERSON_OTHER":"1.5",
               "NOTIFIER":"1.1",
               "SUBMITTER":"1.6"
              },
           "responsible":"1.4"}
""";

  @Autowired NotificationRoutingServiceClient underTest;

  private static LegacyRoutingOutputDto getExpectedRouting() {
    final var expected = new LegacyRoutingOutputDto();
    expected.setHealthOffices(
        Map.ofEntries(
            entry(AddressOriginEnum.NOTIFIER, "1.1"),
            entry(AddressOriginEnum.SUBMITTER, "1.6"),
            entry(AddressOriginEnum.NOTIFIED_PERSON_PRIMARY, "1.4"),
            entry(AddressOriginEnum.NOTIFIED_PERSON_ORDINARY, "1.3"),
            entry(AddressOriginEnum.NOTIFIED_PERSON_CURRENT, "1.2"),
            entry(AddressOriginEnum.NOTIFIED_PERSON_OTHER, "1.5")));
    expected.setResponsible("1.4");
    return expected;
  }

  private static void setupRemoteService(final ResponseDefinitionBuilder responseDefBuilder) {
    stubFor(
        post("/routing")
            .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE))
            .withHeader(ACCEPT, equalTo(APPLICATION_JSON_VALUE))
            .withRequestBody(equalTo(FHIR_JSON))
            .willReturn(responseDefBuilder));
  }

  @Test
  void success() {
    setupRemoteService(okJson(RESPONSE));
    final LegacyRoutingOutputDto result = underTest.determineRouting(FHIR_JSON);
    assertThat(result).isEqualTo(getExpectedRouting());
  }

  @Test
  void error() {
    setupRemoteService(serverError());
    assertThatThrownBy(() -> underTest.determineRouting(FHIR_JSON))
        .isExactlyInstanceOf(ServiceCallException.class);
  }
}

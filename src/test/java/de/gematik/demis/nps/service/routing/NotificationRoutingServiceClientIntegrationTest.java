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
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import de.gematik.demis.nps.service.processing.BundleAction;
import de.gematik.demis.nps.service.processing.BundleActionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;

@SpringBootTest(properties = "nps.client.routing=http://localhost:${wiremock.server.port}")
@AutoConfigureWireMock(port = 0)
class NotificationRoutingServiceClientIntegrationTest {

  private static final String FHIR_JSON = "does not matter";

  @Autowired NotificationRoutingServiceClient underTest;

  private static void setupRemoteServiceV2(final ResponseDefinitionBuilder responseDefBuilder) {
    stubFor(
        post("/routing/v2?isTestUser=false&testUserID=test")
            .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE))
            .withHeader(ACCEPT, equalTo(APPLICATION_JSON_VALUE))
            .withRequestBody(equalTo(FHIR_JSON))
            .willReturn(responseDefBuilder));
  }

  @Test
  void throwsExceptionForDuplicateBundleActions() {
    setupRemoteServiceV2(
        okJson(
"""
{
    "bundleActions": [
        {
            "optional": false,
            "type": "create_pseudonym_record"
        },
        {
            "optional": false,
            "type": "create_pseudonym_record"
        }
    ],
    "healthOffices": {
        "NOTIFIED_PERSON_CURRENT": "1.01.0.53.",
        "NOTIFIED_PERSON_ORDINARY": "1.01.0.53.",
        "NOTIFIER": "1.01.0.53.",
        "SUBMITTER": "1.01.0.53."
    },
    "notificationCategory": "7.1",
    "responsible": "1.01.0.53.",
    "routes": [
        {
            "actions": [
                "pseudo_copy"
            ],
            "optional": false,
            "specificReceiverId": "1.",
            "type": "specific_receiver"
        }
    ],
    "type": "laboratory"
}
"""));
    final NRSRoutingResponse result = underTest.ruleBased(FHIR_JSON, false, "test");
    assertThat(result.bundleActions())
        .containsExactly(BundleAction.requiredOf(BundleActionType.CREATE_PSEUDONYM_RECORD));
  }
}

package de.gematik.demis.nps.service.storage;

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
import static com.github.tomakehurst.wiremock.client.WireMock.okForContentType;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import de.gematik.demis.nps.error.ServiceCallErrorCode;
import de.gematik.demis.service.base.error.ServiceCallException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;

@SpringBootTest(
    properties = "nps.client.fhir-storage-writer=http://localhost:${wiremock.server.port}")
@AutoConfigureWireMock(port = 0)
class FhirStorageWriterClientIntegrationTest {
  private static final String ENDPOINT = "/notification-clearing-api/fhir/";

  private static final String REQUEST_BODY =
"""
{"bundle": "does not matter"}
""";
  private static final String RESPONSE_BODY =
"""
{"bundle": "response"}
""";

  @Autowired FhirStorageWriterClient underTest;

  private static void setupRemoteService(final ResponseDefinitionBuilder responseDefBuilder) {
    stubFor(
        post(ENDPOINT)
            .withHeader(CONTENT_TYPE, equalTo("application/fhir+json"))
            .withHeader(ACCEPT, equalTo("application/fhir+json"))
            .withRequestBody(equalToJson(REQUEST_BODY))
            .willReturn(responseDefBuilder));
  }

  @Test
  void success() {
    setupRemoteService(okForContentType("application/fhir+json;charset=utf-8", RESPONSE_BODY));
    underTest.sendNotificationToFhirStorageWriter(REQUEST_BODY);
    verify(postRequestedFor(urlEqualTo(ENDPOINT)));
    final List<LoggedRequest> all =
        WireMock.findAll(
            RequestPatternBuilder.newRequestPattern(RequestMethod.POST, urlEqualTo(ENDPOINT)));
    assertThat(all).hasSize(1);
  }

  @Test
  void error() {
    setupRemoteService(serverError());

    final ServiceCallException ex =
        catchThrowableOfType(
            () -> underTest.sendNotificationToFhirStorageWriter(REQUEST_BODY),
            ServiceCallException.class);

    assertThat(ex)
        .isNotNull()
        .returns(500, ServiceCallException::getHttpStatus)
        .returns(ServiceCallErrorCode.FSW, ServiceCallException::getErrorCode);
  }
}

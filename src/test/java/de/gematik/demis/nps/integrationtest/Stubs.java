package de.gematik.demis.nps.integrationtest;

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

import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static de.gematik.demis.nps.test.TestData.readResourceAsString;
import static de.gematik.demis.nps.test.TestData.readResourceBytes;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class Stubs {
  static final String STUBS_PATH = "/integrationtest/stubs/";

  static final String FUTS = "/FUTS";
  static final UrlPattern VS = urlPathMatching("/VS/.+");
  static final UrlPattern LVS = urlPathMatching("/LVS/.+");
  static final UrlPattern NRS = urlPathMatching("/NRS/.+");
  static final UrlPattern PS = urlPathMatching("/PS/.+");
  static final UrlPattern PSS = urlPathMatching("/PSS/.+");
  static final UrlPattern PDF = urlPathMatching("/PDF/.+");
  static final UrlPattern NCAPI = urlPathMatching("/NCAPI/.+");

  static String getRequestBody(final UrlPattern urlPattern) {
    final List<LoggedRequest> requests = WireMock.findAll(WireMock.postRequestedFor(urlPattern));
    assertThat(requests).hasSize(1);
    return requests.getFirst().getBodyAsString();
  }

  static ResponseDefinitionBuilder okJsonResource(final String resourceName) {
    return okJson(readResourceAsString(STUBS_PATH + resourceName + ".json"));
  }

  static ResponseDefinitionBuilder okByteResource(final String resourceName) {
    return ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
        .withBody(readResourceBytes(STUBS_PATH + resourceName));
  }

  static ResponseDefinitionBuilder statusJsonResource(final int status, final String resourceName) {
    return jsonResponse(readResourceAsString(STUBS_PATH + resourceName + ".json"), status);
  }

  static void setupStub(
      final UrlPattern urlPattern, final ResponseDefinitionBuilder responseDefBuilder) {
    stubFor(WireMock.post(urlPattern).willReturn(responseDefBuilder));
  }

  static void setupStubForGetRequest(
      final String server,
      final String endpoint,
      final ResponseDefinitionBuilder responseDefBuilder) {
    stubFor(WireMock.get(server + endpoint).willReturn(responseDefBuilder));
  }
}

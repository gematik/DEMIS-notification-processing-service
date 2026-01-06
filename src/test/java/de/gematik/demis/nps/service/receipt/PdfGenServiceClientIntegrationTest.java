package de.gematik.demis.nps.service.receipt;

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

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import de.gematik.demis.nps.error.ServiceCallErrorCode;
import de.gematik.demis.service.base.error.ServiceCallException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpHeaders;

@SpringBootTest(
    properties = {
      "nps.client.pdfgen=http://localhost:${wiremock.server.port}",
      "feature.flag.nbl.for.notByName.enabled=true"
    })
@AutoConfigureWireMock(port = 0)
class PdfGenServiceClientIntegrationTest {
  private static final String ENDPOINT_LABORATORY = "/laboratoryReport";
  private static final String ENDPOINT_DISEASE = "/diseaseNotification";

  private static final String REQUEST_BODY =
"""
{"bundle": "does not matter"}
""";

  private static final byte[] RESPONSE_BODY = "pdf bytes".getBytes();

  @Autowired PdfGenServiceClient underTest;

  private static void setupRemoteService(
      final String endpoint, final ResponseDefinitionBuilder responseDefBuilder) {
    stubFor(
        post(endpoint)
            .withHeader(HttpHeaders.CONTENT_TYPE, equalTo("application/json"))
            .withHeader(HttpHeaders.ACCEPT, equalTo("application/pdf"))
            .withRequestBody(equalToJson(REQUEST_BODY))
            .willReturn(responseDefBuilder));
  }

  private static ResponseDefinitionBuilder okPdfBytes() {
    return ok().withHeader(HttpHeaders.CONTENT_TYPE, "application/pdf").withBody(RESPONSE_BODY);
  }

  @Test
  void success_laboratory() {
    setupRemoteService(ENDPOINT_LABORATORY, okPdfBytes());
    final byte[] pdf = underTest.createLaboratoryPdfFromJson(REQUEST_BODY);
    assertThat(pdf).isEqualTo(RESPONSE_BODY);
  }

  @Test
  void success_disease() {
    setupRemoteService(ENDPOINT_DISEASE, okPdfBytes());
    final byte[] pdf = underTest.createDiseasePdfFromJson(REQUEST_BODY);
    assertThat(pdf).isEqualTo(RESPONSE_BODY);
  }

  @Test
  void error_laboratory() {
    setupRemoteService(ENDPOINT_LABORATORY, serverError());

    assertThatThrownBy(() -> underTest.createLaboratoryPdfFromJson(REQUEST_BODY))
        .isInstanceOf(ServiceCallException.class)
        .hasFieldOrPropertyWithValue("httpStatus", 500)
        .hasFieldOrPropertyWithValue("errorCode", ServiceCallErrorCode.PDF);
  }

  @Test
  void error_disease() {
    setupRemoteService(ENDPOINT_DISEASE, serverError());

    assertThatThrownBy(() -> underTest.createDiseasePdfFromJson(REQUEST_BODY))
        .isInstanceOf(ServiceCallException.class)
        .hasFieldOrPropertyWithValue("httpStatus", 500)
        .hasFieldOrPropertyWithValue("errorCode", ServiceCallErrorCode.PDF);
  }
}

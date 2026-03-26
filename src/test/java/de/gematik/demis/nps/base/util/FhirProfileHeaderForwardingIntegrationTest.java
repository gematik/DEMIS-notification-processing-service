package de.gematik.demis.nps.base.util;

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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static de.gematik.demis.nps.base.util.FhirProfileContext.*;
import static de.gematik.demis.nps.config.NpsHeaders.HEADER_FHIR_PROFILE;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.github.tomakehurst.wiremock.client.WireMock;
import de.gematik.demis.nps.config.FeatureFlagsConfigProperties;
import de.gematik.demis.nps.service.validation.NotificationValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

class FhirProfileHeaderForwardingIntegrationTest {

  private static final String ENDPOINT_NPS = "/$process-notification";
  private static final String ENDPOINT_VS = "/$validate";
  private static final String REQUEST_BODY_DISEASE =
      """
                           {
                             "resourceType": "Bundle",
                             "meta": {
                               "profile": [
                                 "https://demis.rki.de/fhir/StructureDefinition/NotificationBundleDisease"
                               ]
                             },
                             "entry": []
                           }
                           """;
  private static final String RESPONSE_BODY = "{\"outcome\":\"does not matter\"}";

  /**
   * This class tests the header forwarding behaviour in context of an incoming HTTP request to the
   * NPS that triggers an outgoing Feign call to the validation service.
   */
  @SpringBootTest
  @AutoConfigureMockMvc
  @AutoConfigureWireMock(port = 0)
  @TestPropertySource(
      properties = {
        "nps.client.validation=http://localhost:${wiremock.server.port}",
        "feature.flag.feign_interceptor_enabled=true"
      })
  @Nested
  class FhirProfileHeaderForwardingInHttpContext {

    @Autowired MockMvc mockMvc;

    @MockitoBean FeatureFlagsConfigProperties featureFlagsConfigProperties;

    @BeforeEach
    void setup() {
      WireMock.reset();
      stubFor(WireMock.post(urlEqualTo(ENDPOINT_VS)).willReturn(okJson(RESPONSE_BODY)));
    }

    @Test
    void profile_header_forwarded_if_present() throws Exception {
      mockMvc.perform(
          post(ENDPOINT_NPS)
              .content(REQUEST_BODY_DISEASE)
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON)
              .header(HEADER_FHIR_PROFILE, "a-random-profile"));

      verify(
          postRequestedFor(urlEqualTo(ENDPOINT_VS))
              .withHeader(HEADER_FHIR_PROFILE, equalTo("a-random-profile")));
    }

    @Test
    void profile_header_set_if_not_present_ff_off() throws Exception {

      when(featureFlagsConfigProperties.isEnabled(FEATURE_FLAG_FHIR_CORE_SPLIT)).thenReturn(false);

      mockMvc.perform(
          post(ENDPOINT_NPS)
              .content(REQUEST_BODY_DISEASE)
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON));

      verify(
          postRequestedFor(urlEqualTo(ENDPOINT_VS))
              .withHeader(HEADER_FHIR_PROFILE, equalTo(LEGACY_CORE_PROFILE)));
    }

    @Test
    void profile_header_set_if_not_present_ff_on() throws Exception {

      when(featureFlagsConfigProperties.isEnabled(FEATURE_FLAG_FHIR_CORE_SPLIT)).thenReturn(true);

      mockMvc.perform(
          post(ENDPOINT_NPS)
              .content(REQUEST_BODY_DISEASE)
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON));

      verify(
          postRequestedFor(urlEqualTo(ENDPOINT_VS))
              .withHeader(HEADER_FHIR_PROFILE, equalTo(DISEASE_PROFILE)));
    }
  }

  /**
   * We make sure that feign calls outside an HTTP context (i.e. scheduler, async processing) are
   * not affected by the custom interceptor
   */
  @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
  @TestPropertySource(
      properties = {
        "nps.client.validation=http://localhost:${wiremock.server.port}",
        "feature.flag.feign_interceptor_enabled=true"
      })
  @AutoConfigureWireMock(port = 0)
  @Import(HeaderForwardingInNonHttpContextTest.DummyService.class)
  @Nested
  class HeaderForwardingInNonHttpContextTest {

    // In the legcy version (FF feign_interceptor_enabled FALSE) the NotificationValidator uses
    // directly a HttpServletRequest
    // In this test, since we're NOT in an HTTP context, we need to mock the NotificationValidator
    // to avoid exceptions
    // Remove with FF removal
    @MockitoBean NotificationValidator notificationValidator;

    @Autowired DummyService dummyValidationService;

    @BeforeEach
    void setup() {
      WireMock.reset();
      stubFor(
          WireMock.post(urlEqualTo("/dummy"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", MediaType.TEXT_PLAIN_VALUE)
                      .withBody("ok")));
    }

    @Test
    void feignCallSuccessful_noHeadersSet() {
      dummyValidationService.call();

      verify(postRequestedFor(urlEqualTo("/dummy")).withoutHeader(HEADER_FHIR_PROFILE));
    }

    @FeignClient(name = "dummy-client", url = "${nps.client.validation}")
    interface DummyValidationClient {

      @PostMapping(value = "/dummy", consumes = MediaType.TEXT_PLAIN_VALUE)
      String callDummy(@RequestBody String body);
    }

    @Service
    static class DummyService {

      private final DummyValidationClient dummyFeignClient;

      DummyService(DummyValidationClient dummyFeignClient) {
        this.dummyFeignClient = dummyFeignClient;
      }

      void call() {
        dummyFeignClient.callDummy("hello");
      }
    }
  }
}

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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static de.gematik.demis.nps.integrationtest.BundleModifier.*;
import static de.gematik.demis.nps.integrationtest.Stubs.*;
import static de.gematik.demis.nps.test.DecryptionUtil.USER_1_01_0_53;
import static de.gematik.demis.nps.test.DecryptionUtil.USER_TEST_INT;
import static de.gematik.demis.nps.test.DecryptionUtil.decryptData;
import static de.gematik.demis.nps.test.TestData.readResourceAsString;
import static de.gematik.demis.nps.test.TestData.readResourceBytes;
import static de.gematik.demis.nps.test.TestUtil.assertFhirResource;
import static de.gematik.demis.nps.test.TestUtil.getJsonParser;
import static de.gematik.demis.nps.test.TestUtil.getXmlParser;
import static de.gematik.demis.nps.test.TestUtil.toDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import de.gematik.demis.nps.api.NotificationController;
import de.gematik.demis.nps.api.TestUserPropsValueResolver;
import de.gematik.demis.nps.base.data.CertificateDataEntity;
import de.gematik.demis.nps.base.data.CertificateRepository;
import de.gematik.demis.nps.base.util.TimeProvider;
import de.gematik.demis.nps.base.util.UuidGenerator;
import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.service.notification.NotificationType;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.UseMainMethod;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.json.BasicJsonTester;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    useMainMethod = UseMainMethod.ALWAYS,
    properties = {"feature.flag.notification_pre_check=true", "feature.flag.lv_disease=true"})
@AutoConfigureWireMock(port = 0)
@ActiveProfiles("integrationtest")
@AutoConfigureMockMvc
@Slf4j
class NpsIntegrationTest {
  private static final String RESOURCE_BASE = "/integrationtest/";
  private static final String REQUEST_ID = "aaa-bbb-ccc";
  private static final String USER_ID = "LABOR-12345";
  private static final String NPS_ENDPOINT = "/fhir/$process-notification";

  @Autowired MockMvc mockMvc;
  @Autowired MeterRegistry meterRegistry;
  @MockitoBean CertificateRepository certificateRepository;

  @MockitoBean UuidGenerator uuidGenerator;

  @MockitoBean(answers = Answers.CALLS_REAL_METHODS)
  TimeProvider timeProvider;

  private CounterVerifier counterVerifier;

  private boolean isTestUser = false;
  private String testUser = null;

  private static String resource(final String name) {
    return readResourceAsString(RESOURCE_BASE + name);
  }

  @PostConstruct
  void postConstruct() {
    counterVerifier = new CounterVerifier(meterRegistry, USER_ID);
  }

  @BeforeEach
  void setup() {
    WireMock.reset();

    mockRedisRepository(List.of(USER_1_01_0_53, USER_TEST_INT));
    mockUuid();
    meterRegistry.clear();
    setupStubForGetRequest(
        FUTS,
        "/fhir-ui-data-model-translation/conceptmap/NotificationCategoryToTransmissionCategory",
        okJsonResource("futs-response-conceptmap-laboratory"));
    setupStubForGetRequest(
        FUTS,
        "/fhir-ui-data-model-translation/conceptmap/NotificationDiseaseCategoryToTransmissionCategory",
        okJsonResource("futs-response-conceptmap-disease"));
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void success(final NotificationType type) throws Exception {
    setupStub(VS, okJsonResource("vs-response-okay"));
    setupStub(LVS, ok());

    final String resourceName =
        switch (type) {
          case LABORATORY -> "nrs-response-okay-laboratory";
          case DISEASE -> "nrs-response-okay-disease";
        };
    setupStub(NRS, okJsonResource(resourceName));
    setupStub(PS, okJsonResource("ps-response-okay"));
    setupStub(PSS, ok());
    setupStub(FSW, ok());
    setupStub(PDF, okByteResource("receipt-lab.pdf"));

    final String resourceDir =
        switch (type) {
          case LABORATORY -> "laboratory/";
          case DISEASE -> "disease/";
        };

    final String input = resource(resourceDir + "input-notification.json");
    final String expectedNotificationForHealthOffice = resource(resourceDir + "expected-ga.json");

    executeTest(input, OK, resourceDir + "expected-response.json");

    removePseudonymAndResponsibleTags(expectedNotificationForHealthOffice);

    // assert requests to the called services

    assertThat(getRequestBody(VS)).isEqualTo(input);

    assertThat(getRequestBody(LVS)).isEqualToIgnoringWhitespace(input);

    assertPSGenCall(resourceDir + "expected-ps-request.json");

    assertThat(getRequestBody(NRS)).isEqualToIgnoringWhitespace(input);

    // pdf service becomes exactly the notification, which is stored for the health office
    assertThat(getRequestBody(PDF))
        .isEqualToIgnoringWhitespace(expectedNotificationForHealthOffice);

    assertFhirStorageRequest(
        rkiBundle -> assertFhirResource(rkiBundle, resource(resourceDir + "expected-rki.json")),
        healthOfficeBundle ->
            assertFhirResource(healthOfficeBundle, expectedNotificationForHealthOffice),
        USER_1_01_0_53);

    counterVerifier.assertSuccessCounter(type, "cvd");
  }

  @Test
  void process7_4Notification() throws Exception {
    setupStub(VS, okJsonResource("vs-response-okay"));
    setupStub(LVS, ok());

    final String resourceName = "nrs-response-okay-laboratory-7_4";
    setupStub(NRS, okJsonResource(resourceName));
    setupStub(PS, okJsonResource("ps-response-okay"));
    setupStub(PSS, ok());
    setupStub(FSW, ok());
    setupStub(PDF, okByteResource("receipt-lab.pdf"));

    final String resourceDir = "laboratory/";

    final String input = resource(resourceDir + "input-notification-7_4.json");
    final String expectedNotificationForRKI = resource(resourceDir + "expected-rki-7_4.json");

    executeTest(input, OK, resourceDir + "expected-response-7_4.json");

    // assert requests to the called services

    assertThat(getRequestBody(VS)).isEqualTo(input);

    assertThat(getRequestBody(LVS)).isEqualToIgnoringWhitespace(input);

    assertThat(getRequestBody(NRS)).isEqualToIgnoringWhitespace(input);

    // pdf service becomes exactly the notification, which is stored for the health office
    assertThat(getRequestBody(PDF)).isEqualToIgnoringWhitespace(expectedNotificationForRKI);

    final Bundle fswBundle = getJsonParser().parseResource(Bundle.class, getRequestBody(FSW));
    assertThat(fswBundle.getEntry()).hasSize(1);

    counterVerifier.assertSuccessCounter(NotificationType.LABORATORY, "cvd");
  }

  private void assertPSGenCall(final String resourceName) {
    final BasicJsonTester jsonTester = new BasicJsonTester(this.getClass());
    assertThat(jsonTester.from(getRequestBody(PS))).isEqualToJson(resource(resourceName));
  }

  private void assertFhirStorageRequest(
      final Consumer<Resource> anonymizedBundleAsserter,
      final Consumer<Resource> healthOfficeBundleAsserter,
      final String healthOffice)
      throws Exception {
    final Bundle fswBundle = getJsonParser().parseResource(Bundle.class, getRequestBody(FSW));

    // all testcases have no subsidiary notification, thus we expect 2 entries
    assertThat(fswBundle.getEntry()).hasSize(2);

    final BundleEntryComponent anonymizedEntry = fswBundle.getEntry().get(0);
    anonymizedBundleAsserter.accept(anonymizedEntry.getResource());

    final BundleEntryComponent encryptedEntry = fswBundle.getEntry().get(1);
    final byte[] encryptedData = ((Binary) encryptedEntry.getResource()).getData();
    final String decrypted =
        new String(decryptData(healthOffice, encryptedData), StandardCharsets.UTF_8);
    // read xml format and compare with expectation in json
    final Bundle decryptedBundle = getXmlParser().parseResource(Bundle.class, decrypted);

    healthOfficeBundleAsserter.accept(decryptedBundle);
  }

  private void assertFhirStorageRequest(
      final Consumer<Resource> healthOfficeBundleAsserter, final String responsibleDepartment)
      throws Exception {
    final Bundle fswBundle = getJsonParser().parseResource(Bundle.class, getRequestBody(FSW));

    assertThat(fswBundle.getEntry()).hasSize(1);

    final BundleEntryComponent encryptedEntry = fswBundle.getEntry().get(0);
    final byte[] encryptedData = ((Binary) encryptedEntry.getResource()).getData();
    final String decrypted =
        new String(decryptData(responsibleDepartment, encryptedData), StandardCharsets.UTF_8);
    // read xml format and compare with expectation in json
    final Bundle decryptedBundle = getXmlParser().parseResource(Bundle.class, decrypted);

    healthOfficeBundleAsserter.accept(decryptedBundle);
  }

  @Test
  void unsupportedProfile() throws Exception {
    setupStub(VS, okJsonResource("vs-response-okay"));
    executeTest(
        resource("errors/input-unsupported-report.json"),
        BAD_REQUEST,
        "errors/expected-unsupported-profile-response.json");
    counterVerifier.assertErrorCounter(ErrorCode.UNSUPPORTED_PROFILE);
  }

  @Test
  void fhirValidationError() throws Exception {
    setupStub(VS, statusJsonResource(422, "vs-response-422"));

    executeTest(UNPROCESSABLE_ENTITY, "errors/expected-fhir-validation-error-response.json");
    counterVerifier.assertErrorCounter(ErrorCode.FHIR_VALIDATION_ERROR);
  }

  @Test
  void lifeValidationError() throws Exception {
    setupStub(VS, okJsonResource("vs-response-okay"));
    setupStub(LVS, status(422).withBody("L00X"));
    setupStub(NRS, okJsonResource("nrs-response-okay-laboratory"));

    executeTest(UNPROCESSABLE_ENTITY, "errors/expected-lifecycle-validation-error-response.json");
    counterVerifier.assertErrorCounter(ErrorCode.LIFECYCLE_VALIDATION_ERROR);
  }

  @Test
  void noResponsible() throws Exception {
    setupStub(VS, okJsonResource("vs-response-okay"));
    setupStub(LVS, ok());
    setupStub(NRS, okJsonResource("nrs-response-no-healthoffice"));

    executeTest(UNPROCESSABLE_ENTITY, "errors/expected-no-responsible-response.json");
    counterVerifier.assertErrorCounter(ErrorCode.MISSING_RESPONSIBLE);
  }

  @Test
  void pseudonymError() throws Exception {
    setupStub(VS, okJsonResource("vs-response-okay"));
    setupStub(LVS, ok());
    setupStub(NRS, okJsonResource("nrs-response-okay-laboratory"));
    setupStub(PS, statusJsonResource(400, "ps-response-400"));
    setupStub(FSW, ok());
    setupStub(PDF, okByteResource("receipt-lab.pdf"));

    // 200, no pss call, storage call without pseudonym, same result
    executeTest(OK, "laboratory/expected-response.json");

    assertFhirStorageRequest(
        rkiBundle ->
            assertFhirResource(
                rkiBundle,
                modifyResource(
                    resource("laboratory/expected-rki.json"), BundleModifier::removePseudonym)),
        healthOfficeBundle ->
            assertFhirResource(
                healthOfficeBundle,
                modifyResource(
                    resource("laboratory/expected-ga.json"), BundleModifier::removePseudonym)),
        USER_1_01_0_53);
    counterVerifier.assertSuccessCounter(NotificationType.LABORATORY, "cvd");
  }

  @Test
  void noCertificate() throws Exception {
    setupStub(VS, okJsonResource("vs-response-okay"));
    setupStub(LVS, ok());
    setupStub(NRS, okJsonResource("nrs-response-healthoffice-without-certificate"));
    setupStub(PS, okJsonResource("ps-response-okay"));
    setupStub(PSS, ok());

    executeTest(INTERNAL_SERVER_ERROR, "errors/expected-no-certificate-response.json");
    counterVerifier.assertErrorCounter(ErrorCode.HEALTH_OFFICE_CERTIFICATE);
  }

  @Test
  void storageError() throws Exception {
    setupStub(VS, okJsonResource("vs-response-okay"));
    setupStub(LVS, ok());
    setupStub(NRS, okJsonResource("nrs-response-okay-laboratory"));
    setupStub(PS, okJsonResource("ps-response-okay"));
    setupStub(PSS, ok());
    setupStub(FSW, status(500));

    executeTest(BAD_GATEWAY, "errors/expected-storage-error-response.json");
  }

  @Test
  void pdfError() throws Exception {
    setupStub(VS, okJsonResource("vs-response-okay"));
    setupStub(LVS, ok());
    setupStub(NRS, okJsonResource("nrs-response-okay-laboratory"));
    setupStub(PS, okJsonResource("ps-response-okay"));
    setupStub(PSS, ok());
    setupStub(FSW, ok());
    setupStub(PDF, status(500));

    // 200, storage same, response without binary
    executeTest(OK, "errors/expected-without-pdf-response.json");
    counterVerifier.assertSuccessCounter(NotificationType.LABORATORY, "cvd");
  }

  @Test
  void testUserTestInt() throws Exception {
    isTestUser = true;
    testUser = "test-int";
    setupStub(VS, okJsonResource("vs-response-okay"));
    setupStub(LVS, ok());
    setupStub(NRS, okJsonResource("nrs-response-okay-laboratory-with-test-user"));
    setupStub(PS, okJsonResource("ps-response-okay"));
    setupStub(PSS, ok());
    setupStub(FSW, ok());
    setupStub(PDF, okByteResource("receipt-lab.pdf"));
    final String expectedNotificationForHealthOffice =
        resource("laboratory/expected-ga-test-user.json");
    executeTest(OK, "laboratory/expected-response-testuser.json");

    assertFhirStorageRequest(
        healthOfficeBundle ->
            assertFhirResource(healthOfficeBundle, expectedNotificationForHealthOffice),
        "test-int");

    counterVerifier.assertSuccessCounter(NotificationType.LABORATORY, "cvd");
  }

  private String executeTest(final HttpStatus expectedStatus, final String expectedResponseResource)
      throws Exception {
    return executeTest(
        resource("laboratory/input-notification.json"), expectedStatus, expectedResponseResource);
  }

  private String executeTest(
      final String input, final HttpStatus expectedStatus, final String expectedResponseResource)
      throws Exception {
    if (expectedStatus == OK) {
      mockTime();
    }

    final String response = executeCall(input, expectedStatus);

    assertThat(response).isEqualToIgnoringWhitespace(resource(expectedResponseResource));

    return response;
  }

  private String executeCall(final String fhirNotification, final HttpStatus expectedStatus)
      throws Exception {
    final MockHttpServletRequestBuilder requestBuilder =
        post(NPS_ENDPOINT)
            .contentType(APPLICATION_JSON_VALUE)
            .content(fhirNotification)
            .header(HttpHeaders.ACCEPT, APPLICATION_JSON_VALUE)
            .header("x-request-id", REQUEST_ID)
            .header(NotificationController.HEADER_SENDER, USER_ID)
            .header(TestUserPropsValueResolver.HEADER_IS_TEST_NOTIFICATION, isTestUser);
    if (testUser != null) {
      // MockHttpServletRequest will complain about Headers set to null
      requestBuilder.header(TestUserPropsValueResolver.HEADER_TEST_USER_RECIPIENT, testUser);
    }

    return mockMvc
        .perform(requestBuilder)
        .andExpectAll(
            status().is(expectedStatus.value()),
            content().contentTypeCompatibleWith(APPLICATION_JSON_VALUE))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private void mockUuid() {
    Mockito.when(uuidGenerator.generateUuid()).thenReturn("fee6005e-5686-4b7b-b6ee-98b0e98a9d42");
  }

  private void mockTime() {
    Mockito.when(timeProvider.now())
        .thenReturn(toDate(LocalDateTime.parse("2024-01-02T14:19:29.114")));
  }

  private void mockRedisRepository(final List<String> healthDepartments) {
    Mockito.reset(certificateRepository);
    List<CertificateDataEntity> certificateDataEntities = new ArrayList<>();
    for (var healthDepartment : healthDepartments) {
      final var certEntity = createCertEntity(healthDepartment);
      certificateDataEntities.add(certEntity);
      Mockito.when(certificateRepository.findById(healthDepartment))
          .thenReturn(Optional.of(certEntity));
    }
    Mockito.when(certificateRepository.findAll()).thenReturn(certificateDataEntities);
  }

  CertificateDataEntity createCertEntity(final String healthDepartment) {
    log.info("Configuring {}", String.format("/certificates/%s.der", healthDepartment));
    return new CertificateDataEntity(
        healthDepartment,
        readResourceBytes(String.format("/certificates/%s.der", healthDepartment)),
        LocalDateTime.now());
  }
}

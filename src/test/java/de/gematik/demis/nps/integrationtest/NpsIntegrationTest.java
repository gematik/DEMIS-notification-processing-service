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
import de.gematik.demis.service.base.error.rest.ErrorFieldProvider;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.junit.jupiter.api.Nested;
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
    properties = {
      "feature.flag.notification_pre_check=true",
      "feature.flag.lv_disease=true",
      "feature.flag.move-error-id-to-diagnostics=true"
    })
@AutoConfigureWireMock(port = 0)
@ActiveProfiles("integrationtest")
@AutoConfigureMockMvc
@Slf4j
class NpsIntegrationTest {
  // Resource paths
  private static final String RESOURCE_BASE = "/integrationtest/";
  private static final String LABORATORY_DIR = "laboratory/";
  private static final String DISEASE_DIR = "disease/";
  private static final String ERRORS_DIR = "errors/";

  // Test resources
  private static final String INPUT_NOTIFICATION_JSON = "input-notification.json";
  private static final String INPUT_NOTIFICATION_7_4_JSON = "input-notification-7_4.json";
  private static final String INPUT_UNSUPPORTED_REPORT_JSON = "input-unsupported-report.json";
  private static final String EXPECTED_RESPONSE_JSON = "expected-response.json";
  private static final String EXPECTED_RESPONSE_7_4_JSON = "expected-response-7_4.json";
  private static final String EXPECTED_RESPONSE_TESTUSER_JSON = "expected-response-testuser.json";
  private static final String EXPECTED_GA_JSON = "expected-ga.json";
  private static final String EXPECTED_GA_TEST_USER_JSON = "expected-ga-test-user.json";
  private static final String EXPECTED_RKI_JSON = "expected-rki.json";
  private static final String EXPECTED_RKI_7_4_JSON = "expected-rki-7_4.json";
  private static final String EXPECTED_PS_REQUEST_JSON = "expected-ps-request.json";

  // Stub response resources
  private static final String VS_RESPONSE_OKAY = "vs-response-okay";
  private static final String VS_RESPONSE_422 = "vs-response-422";
  private static final String NRS_RESPONSE_OKAY_LABORATORY = "nrs-response-okay-laboratory";
  private static final String NRS_RESPONSE_OKAY_LABORATORY_7_4 = "nrs-response-okay-laboratory-7_4";
  private static final String NRS_RESPONSE_OKAY_LABORATORY_WITH_TEST_USER =
      "nrs-response-okay-laboratory-with-test-user";
  private static final String NRS_RESPONSE_OKAY_DISEASE = "nrs-response-okay-disease";
  private static final String NRS_RESPONSE_NO_HEALTHOFFICE = "nrs-response-no-healthoffice";
  private static final String NRS_RESPONSE_HEALTHOFFICE_WITHOUT_CERTIFICATE =
      "nrs-response-healthoffice-without-certificate";
  private static final String PS_RESPONSE_OKAY = "ps-response-okay";
  private static final String PS_RESPONSE_400 = "ps-response-400";
  private static final String RECEIPT_LAB_PDF = "receipt-lab.pdf";
  private static final String FUTS_CONCEPTMAP_LABORATORY = "futs-response-conceptmap-laboratory";
  private static final String FUTS_CONCEPTMAP_DISEASE = "futs-response-conceptmap-disease";

  // Error response resources
  private static final String EXPECTED_UNSUPPORTED_PROFILE_RESPONSE =
      "expected-unsupported-profile-response.json";
  private static final String EXPECTED_FHIR_VALIDATION_ERROR_RESPONSE =
      "expected-fhir-validation-error-response.json";
  private static final String EXPECTED_LABORATORY_LIFECYCLE_VALIDATION_ERROR_RESPONSE =
      "expected-laboratory-lifecycle-validation-error-response.json";
  private static final String EXPECTED_DISEASE_LIFECYCLE_VALIDATION_ERROR_RESPONSE =
      "expected-disease-lifecycle-validation-error-response.json";
  private static final String EXPECTED_NO_RESPONSIBLE_RESPONSE =
      "expected-no-responsible-response.json";
  private static final String EXPECTED_NO_CERTIFICATE_RESPONSE =
      "expected-no-certificate-response.json";
  private static final String EXPECTED_STORAGE_ERROR_RESPONSE =
      "expected-storage-error-response.json";
  private static final String EXPECTED_WITHOUT_PDF_RESPONSE = "expected-without-pdf-response.json";

  // FUTS endpoints
  private static final String FUTS_CONCEPTMAP_NOTIFICATION_CATEGORY_ENDPOINT =
      "/fhir-ui-data-model-translation/conceptmap/NotificationCategoryToTransmissionCategory";
  private static final String FUTS_CONCEPTMAP_DISEASE_CATEGORY_ENDPOINT =
      "/fhir-ui-data-model-translation/conceptmap/NotificationDiseaseCategoryToTransmissionCategory";

  // Mock values
  private static final String MOCK_UUID = "fee6005e-5686-4b7b-b6ee-98b0e98a9d42";
  private static final String MOCK_TIMESTAMP = "2024-01-02T14:19:29.114";
  private static final String LIFECYCLE_VALIDATION_ERROR_BODY = "L00X";
  private static final String TEST_USER_NAME = "test-int";
  private static final String DISEASE_CODE = "cvd";
  private static final String CERTIFICATE_PATH_FORMAT = "/certificates/%s.der";

  // HTTP Headers
  private static final String REQUEST_ID = "aaa-bbb-ccc";
  private static final String USER_ID = "LABOR-12345";
  private static final String NPS_ENDPOINT = "/fhir/$process-notification";

  @Autowired MockMvc mockMvc;
  @Autowired MeterRegistry meterRegistry;
  @MockitoBean CertificateRepository certificateRepository;

  @MockitoBean UuidGenerator uuidGenerator;

  @MockitoBean(answers = Answers.CALLS_REAL_METHODS)
  ErrorFieldProvider errorFieldProvider;

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
    mockErrorUuid();
    meterRegistry.clear();
    setupStubForGetRequest(
        FUTS,
        FUTS_CONCEPTMAP_NOTIFICATION_CATEGORY_ENDPOINT,
        okJsonResource(FUTS_CONCEPTMAP_LABORATORY));
    setupStubForGetRequest(
        FUTS, FUTS_CONCEPTMAP_DISEASE_CATEGORY_ENDPOINT, okJsonResource(FUTS_CONCEPTMAP_DISEASE));
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void success(final NotificationType type) throws Exception {
    setupStub(VS, okJsonResource(VS_RESPONSE_OKAY));
    setupStub(LVS, ok());

    final String resourceName =
        switch (type) {
          case LABORATORY -> NRS_RESPONSE_OKAY_LABORATORY;
          case DISEASE -> NRS_RESPONSE_OKAY_DISEASE;
        };
    setupStub(NRS, okJsonResource(resourceName));
    setupStub(PS, okJsonResource(PS_RESPONSE_OKAY));
    setupStub(PSS, ok());
    setupStub(FSW, ok());
    setupStub(PDF, okByteResource(RECEIPT_LAB_PDF));

    final String resourceDir =
        switch (type) {
          case LABORATORY -> LABORATORY_DIR;
          case DISEASE -> DISEASE_DIR;
        };

    final String input = resource(resourceDir + INPUT_NOTIFICATION_JSON);
    final String expectedNotificationForHealthOffice = resource(resourceDir + EXPECTED_GA_JSON);

    executeTest(input, OK, resourceDir + EXPECTED_RESPONSE_JSON);

    removePseudonymAndResponsibleTags(expectedNotificationForHealthOffice);

    // assert requests to the called services

    assertThat(getRequestBody(VS)).isEqualTo(input);

    assertThat(getRequestBody(LVS)).isEqualToIgnoringWhitespace(input);

    assertPSGenCall(resourceDir + EXPECTED_PS_REQUEST_JSON);

    assertThat(getRequestBody(NRS)).isEqualToIgnoringWhitespace(input);

    // pdf service becomes exactly the notification, which is stored for the health office
    assertThat(getRequestBody(PDF))
        .isEqualToIgnoringWhitespace(expectedNotificationForHealthOffice);

    assertFhirStorageRequest(
        rkiBundle -> assertFhirResource(rkiBundle, resource(resourceDir + EXPECTED_RKI_JSON)),
        healthOfficeBundle ->
            assertFhirResource(healthOfficeBundle, expectedNotificationForHealthOffice),
        USER_1_01_0_53);

    counterVerifier.assertSuccessCounter(type, DISEASE_CODE);
  }

  @Test
  void process7_4Notification() throws Exception {
    setupStub(VS, okJsonResource(VS_RESPONSE_OKAY));
    setupStub(LVS, ok());

    setupStub(NRS, okJsonResource(NRS_RESPONSE_OKAY_LABORATORY_7_4));
    setupStub(PS, okJsonResource(PS_RESPONSE_OKAY));
    setupStub(PSS, ok());
    setupStub(FSW, ok());
    setupStub(PDF, okByteResource(RECEIPT_LAB_PDF));

    final String input = resource(LABORATORY_DIR + INPUT_NOTIFICATION_7_4_JSON);
    final String expectedNotificationForRKI = resource(LABORATORY_DIR + EXPECTED_RKI_7_4_JSON);

    executeTest(input, OK, LABORATORY_DIR + EXPECTED_RESPONSE_7_4_JSON);

    // assert requests to the called services

    assertThat(getRequestBody(VS)).isEqualTo(input);

    assertThat(getRequestBody(LVS)).isEqualToIgnoringWhitespace(input);

    assertThat(getRequestBody(NRS)).isEqualToIgnoringWhitespace(input);

    // pdf service becomes exactly the notification, which is stored for the health office
    assertThat(getRequestBody(PDF)).isEqualToIgnoringWhitespace(expectedNotificationForRKI);

    final Bundle fswBundle = getJsonParser().parseResource(Bundle.class, getRequestBody(FSW));
    assertThat(fswBundle.getEntry()).hasSize(1);

    counterVerifier.assertSuccessCounter(NotificationType.LABORATORY, DISEASE_CODE);
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

  @Nested
  class ErrorContentIsDelivered {

    @Test
    void unsupportedProfile() throws Exception {
      setupStub(VS, okJsonResource(VS_RESPONSE_OKAY));
      executeTest(
          resource(ERRORS_DIR + INPUT_UNSUPPORTED_REPORT_JSON),
          BAD_REQUEST,
          ERRORS_DIR + EXPECTED_UNSUPPORTED_PROFILE_RESPONSE);
      counterVerifier.assertErrorCounter(ErrorCode.UNSUPPORTED_PROFILE);
    }

    @Test
    void validationServiceReturnsUnprocessableEntity_shouldReturnFhirValidationError()
        throws Exception {
      setupStub(VS, statusJsonResource(422, VS_RESPONSE_422));

      executeTest(UNPROCESSABLE_ENTITY, ERRORS_DIR + EXPECTED_FHIR_VALIDATION_ERROR_RESPONSE);
      counterVerifier.assertErrorCounter(ErrorCode.FHIR_VALIDATION_ERROR);
    }

    @Test
    void laboratoryLifecycleValidationServiceReturnsError_shouldReturnLifecycleValidationError()
        throws Exception {
      setupStub(VS, okJsonResource(VS_RESPONSE_OKAY));
      setupStub(LVS, status(422).withBody(LIFECYCLE_VALIDATION_ERROR_BODY));
      setupStub(NRS, okJsonResource(NRS_RESPONSE_OKAY_LABORATORY));

      String laboratoryInput =
          Files.readString(Path.of("src/test/resources/bundles/laboratory_cvdp_bundle.json"));
      executeTest(
          laboratoryInput,
          UNPROCESSABLE_ENTITY,
          ERRORS_DIR + EXPECTED_LABORATORY_LIFECYCLE_VALIDATION_ERROR_RESPONSE);
      counterVerifier.assertErrorCounter(ErrorCode.LIFECYCLE_VALIDATION_ERROR);
    }

    @Test
    void diseaseLifecycleValidationServiceReturnsError_shouldReturnLifecycleValidationError()
        throws Exception {
      setupStub(VS, okJsonResource(VS_RESPONSE_OKAY));
      setupStub(LVS, status(422).withBody(LIFECYCLE_VALIDATION_ERROR_BODY));
      setupStub(NRS, okJsonResource(NRS_RESPONSE_OKAY_DISEASE));

      String diseaseInput =
          Files.readString(Path.of("src/test/resources/bundles/disease_bundle_max.json"));
      executeTest(
          diseaseInput,
          UNPROCESSABLE_ENTITY,
          ERRORS_DIR + EXPECTED_DISEASE_LIFECYCLE_VALIDATION_ERROR_RESPONSE);
      counterVerifier.assertErrorCounter(ErrorCode.LIFECYCLE_VALIDATION_ERROR);
    }

    @Test
    void notificationRoutingServiceReturnsNoResponsible_shouldReturnMissingResponsibleError()
        throws Exception {
      setupStub(VS, okJsonResource(VS_RESPONSE_OKAY));
      setupStub(LVS, ok());
      setupStub(NRS, okJsonResource(NRS_RESPONSE_NO_HEALTHOFFICE));

      executeTest(UNPROCESSABLE_ENTITY, ERRORS_DIR + EXPECTED_NO_RESPONSIBLE_RESPONSE);
      counterVerifier.assertErrorCounter(ErrorCode.MISSING_RESPONSIBLE);
    }

    @Test
    void pseudonymServiceReturnsBadRequest_shouldStillSucceedWithoutPseudonym() throws Exception {
      setupStub(VS, okJsonResource(VS_RESPONSE_OKAY));
      setupStub(LVS, ok());
      setupStub(NRS, okJsonResource(NRS_RESPONSE_OKAY_LABORATORY));
      setupStub(PS, statusJsonResource(400, PS_RESPONSE_400));
      setupStub(FSW, ok());
      setupStub(PDF, okByteResource(RECEIPT_LAB_PDF));

      // 200, no pss call, storage call without pseudonym, same result
      executeTest(OK, LABORATORY_DIR + EXPECTED_RESPONSE_JSON);

      assertFhirStorageRequest(
          rkiBundle ->
              assertFhirResource(
                  rkiBundle,
                  modifyResource(
                      resource(LABORATORY_DIR + EXPECTED_RKI_JSON),
                      BundleModifier::removePseudonym)),
          healthOfficeBundle ->
              assertFhirResource(
                  healthOfficeBundle,
                  modifyResource(
                      resource(LABORATORY_DIR + EXPECTED_GA_JSON),
                      BundleModifier::removePseudonym)),
          USER_1_01_0_53);
      counterVerifier.assertSuccessCounter(NotificationType.LABORATORY, DISEASE_CODE);
    }

    @Test
    void
        notificationRoutingServiceReturnsHealthOfficeWithoutCertificate_shouldReturnInternalServerError()
            throws Exception {
      setupStub(VS, okJsonResource(VS_RESPONSE_OKAY));
      setupStub(LVS, ok());
      setupStub(NRS, okJsonResource(NRS_RESPONSE_HEALTHOFFICE_WITHOUT_CERTIFICATE));
      setupStub(PS, okJsonResource(PS_RESPONSE_OKAY));
      setupStub(PSS, ok());

      executeTest(INTERNAL_SERVER_ERROR, ERRORS_DIR + EXPECTED_NO_CERTIFICATE_RESPONSE);
      counterVerifier.assertErrorCounter(ErrorCode.HEALTH_OFFICE_CERTIFICATE);
    }

    @Test
    void fhirStorageServiceReturnsInternalServerError_shouldReturnBadGateway() throws Exception {
      setupStub(VS, okJsonResource(VS_RESPONSE_OKAY));
      setupStub(LVS, ok());
      setupStub(NRS, okJsonResource(NRS_RESPONSE_OKAY_LABORATORY));
      setupStub(PS, okJsonResource(PS_RESPONSE_OKAY));
      setupStub(PSS, ok());
      setupStub(FSW, status(500));

      executeTest(BAD_GATEWAY, ERRORS_DIR + EXPECTED_STORAGE_ERROR_RESPONSE);
    }

    @Test
    void pdfServiceReturnsInternalServerError_shouldStillSucceedWithoutPdf() throws Exception {
      setupStub(VS, okJsonResource(VS_RESPONSE_OKAY));
      setupStub(LVS, ok());
      setupStub(NRS, okJsonResource(NRS_RESPONSE_OKAY_LABORATORY));
      setupStub(PS, okJsonResource(PS_RESPONSE_OKAY));
      setupStub(PSS, ok());
      setupStub(FSW, ok());
      setupStub(PDF, status(500));

      // 200, storage same, response without binary
      executeTest(OK, ERRORS_DIR + EXPECTED_WITHOUT_PDF_RESPONSE);
      counterVerifier.assertSuccessCounter(NotificationType.LABORATORY, DISEASE_CODE);
    }

    @Test
    void testUserTestInt() throws Exception {
      isTestUser = true;
      testUser = TEST_USER_NAME;
      setupStub(VS, okJsonResource(VS_RESPONSE_OKAY));
      setupStub(LVS, ok());
      setupStub(NRS, okJsonResource(NRS_RESPONSE_OKAY_LABORATORY_WITH_TEST_USER));
      setupStub(PS, okJsonResource(PS_RESPONSE_OKAY));
      setupStub(PSS, ok());
      setupStub(FSW, ok());
      setupStub(PDF, okByteResource(RECEIPT_LAB_PDF));
      final String expectedNotificationForHealthOffice =
          resource(LABORATORY_DIR + EXPECTED_GA_TEST_USER_JSON);
      executeTest(OK, LABORATORY_DIR + EXPECTED_RESPONSE_TESTUSER_JSON);

      assertFhirStorageRequest(
          healthOfficeBundle ->
              assertFhirResource(healthOfficeBundle, expectedNotificationForHealthOffice),
          TEST_USER_NAME);

      counterVerifier.assertSuccessCounter(NotificationType.LABORATORY, DISEASE_CODE);
    }
  }

  private String executeTest(final HttpStatus expectedStatus, final String expectedResponseResource)
      throws Exception {
    return executeTest(
        resource(LABORATORY_DIR + INPUT_NOTIFICATION_JSON),
        expectedStatus,
        expectedResponseResource);
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
    Mockito.when(uuidGenerator.generateUuid()).thenReturn(MOCK_UUID);
  }

  private void mockErrorUuid() {
    Mockito.when(errorFieldProvider.generateId()).thenReturn(MOCK_UUID);
  }

  private void mockTime() {
    Mockito.when(timeProvider.now()).thenReturn(toDate(LocalDateTime.parse(MOCK_TIMESTAMP)));
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
    final String certificatePath = String.format(CERTIFICATE_PATH_FORMAT, healthDepartment);
    log.info("Configuring {}", certificatePath);
    return new CertificateDataEntity(
        healthDepartment, readResourceBytes(certificatePath), LocalDateTime.now());
  }
}

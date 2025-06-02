package de.gematik.demis.nps.service.encryption;

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

import static de.gematik.demis.nps.base.profile.DemisSystems.RESPONSIBLE_HEALTH_DEPARTMENT_CODING_SYSTEM;
import static de.gematik.demis.nps.test.TestUtil.toDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import de.gematik.demis.nps.base.util.TimeProvider;
import de.gematik.demis.nps.config.FhirConfiguration;
import de.gematik.demis.nps.service.healthoffice.SubsidiaryService;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.test.TestData;
import de.gematik.demis.nps.test.TestUtil;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.util.Optional;
import org.assertj.core.api.SoftAssertions;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(
    classes = {EncryptionService.class, FhirConfiguration.class, BinaryCreator.class},
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    useMainMethod = SpringBootTest.UseMainMethod.NEVER)
class EncryptionServiceIntegrationTest {

  private static final String RESPONSIBLE_HO = "1.23";
  private static final String ENCRYPTED_CONTENT = "ENCRYPTED";

  private static final String EXPECTED_BINARY =
"""
{
  "resourceType" : "Binary",
  "meta" : {
    "lastUpdated" : "2023-12-22T00:00:00.000+01:00",
    "tag" : [ {
      "system" : "https://demis.rki.de/fhir/CodeSystem/ResponsibleDepartment",
      "code" : "%TARGET_HO%"
    }, {
      "system" : "https://demis.rki.de/fhir/CodeSystem/RelatedNotificationBundle",
      "code" : "a5e00874-bb26-45ac-8eea-0bde76456703",
      "display" : "Relates to message with identifier: a5e00874-bb26-45ac-8eea-0bde76456703"
    } ]
  },
  "contentType" : "application/cms",
  "data" : "RU5DUllQVEVE"
}
""";

  @MockBean DataEncryption dataEncryption;
  @MockBean CertificateProvider certificateProvider;
  @MockBean TimeProvider timeProvider;
  @MockBean SubsidiaryService subsidiaryService;

  @Autowired EncryptionService underTest;
  @Autowired FhirContext fhirContext;

  private static Notification createNotification() {
    final Bundle bundle = TestData.laboratoryBundle();
    bundle
        .getMeta()
        .addTag()
        .setSystem(RESPONSIBLE_HEALTH_DEPARTMENT_CODING_SYSTEM)
        .setCode(RESPONSIBLE_HO);
    return Notification.builder().bundle(bundle).build();
  }

  @Test
  void encryptForResponsibleHealthOffice() {
    final Notification notification = createNotification();

    setupMocks(RESPONSIBLE_HO);

    final Binary binary = underTest.encryptForResponsibleHealthOffice(notification);

    assertBinary(binary, notification, RESPONSIBLE_HO);
  }

  @Test
  void encryptForSubsidiary_Okay() {
    final String SUBSIDIARY_HO = "2.98";
    when(subsidiaryService.getSubsidiaryId(RESPONSIBLE_HO)).thenReturn(Optional.of(SUBSIDIARY_HO));
    when(certificateProvider.hasCertificate(SUBSIDIARY_HO)).thenReturn(true);
    setupMocks(SUBSIDIARY_HO);

    final Notification notification = createNotification();
    final Optional<Binary> result = underTest.encryptForSubsidiary(notification);

    assertThat(result).isPresent();

    assertBinary(result.get(), notification, SUBSIDIARY_HO);
  }

  @Test
  void encryptForSubsidiary_NoSubsidiary() {
    when(subsidiaryService.getSubsidiaryId(RESPONSIBLE_HO)).thenReturn(Optional.empty());
    final Optional<Binary> result = underTest.encryptForSubsidiary(createNotification());
    assertThat(result).isEmpty();
  }

  @Test
  void encryptForSubsidiary_MissingCertificate() {
    final String SUBSIDIARY_HO = "2.98";
    when(subsidiaryService.getSubsidiaryId(RESPONSIBLE_HO)).thenReturn(Optional.of(SUBSIDIARY_HO));
    when(certificateProvider.hasCertificate(SUBSIDIARY_HO)).thenReturn(false);

    final Optional<Binary> result = underTest.encryptForSubsidiary(createNotification());

    assertThat(result).isEmpty();
    verify(certificateProvider, Mockito.never()).getCertificate(any());
  }

  private void setupMocks(final String targetHealthOffice) {
    final X509Certificate certificate = mock(X509Certificate.class);
    when(certificateProvider.hasCertificate(targetHealthOffice)).thenReturn(true);
    when(certificateProvider.getCertificate(targetHealthOffice)).thenReturn(certificate);

    when(dataEncryption.encryptData(any(), eq(certificate)))
        .thenReturn(ENCRYPTED_CONTENT.getBytes());

    when(timeProvider.now()).thenReturn(toDate(LocalDate.of(2023, 12, 22)));
  }

  private void assertBinary(
      final Binary binary, final Notification notification, final String targetHealthOffice) {
    final SoftAssertions assertions = new SoftAssertions();

    final String expectedBinary = EXPECTED_BINARY.replace("%TARGET_HO%", targetHealthOffice);
    assertions
        .assertThat(TestUtil.fhirResourceToJson(binary))
        .as("full comparison")
        .isEqualToIgnoringWhitespace(expectedBinary);

    assertions
        .assertThat(binary.getContent())
        .as("binary content")
        .asString()
        .isEqualTo(ENCRYPTED_CONTENT);

    final String notificationToEncrypt = captureDataToEncrypt();
    final String expectedXml =
        fhirContext.newXmlParser().encodeResourceToString(notification.getBundle());
    assertions
        .assertThat(notificationToEncrypt)
        .as("data encryption call")
        .isEqualToIgnoringWhitespace(expectedXml);

    assertions.assertAll();
  }

  private String captureDataToEncrypt() {
    final var argumentCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(dataEncryption).encryptData(argumentCaptor.capture(), any());
    return new String(argumentCaptor.getValue(), StandardCharsets.UTF_8);
  }
}

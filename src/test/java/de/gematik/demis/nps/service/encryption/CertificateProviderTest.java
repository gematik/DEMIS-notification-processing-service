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

import static de.gematik.demis.nps.error.ErrorCode.HEALTH_OFFICE_CERTIFICATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import de.gematik.demis.nps.base.data.CertificateDataEntity;
import de.gematik.demis.nps.base.data.CertificateRepository;
import de.gematik.demis.nps.error.NpsServiceException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

class CertificateProviderTest {
  private static final String CERT_DIR = "src/test/resources/certificates";
  private static final String HEALTH_OFFICE_VALID_CERT = "1.01.0.53.";
  private CertificateProvider underTest;
  @Mock private CertificateRepository certificateRepository;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
    Mockito.reset(certificateRepository);
    underTest = new CertificateProvider(certificateRepository);
  }

  @Test
  void certificateOkay() throws IOException {
    Mockito.when(certificateRepository.findById(HEALTH_OFFICE_VALID_CERT))
        .thenReturn(
            java.util.Optional.of(
                new CertificateDataEntity(
                    HEALTH_OFFICE_VALID_CERT,
                    Files.readAllBytes(
                        Path.of(CERT_DIR).resolve(HEALTH_OFFICE_VALID_CERT + ".der")),
                    LocalDateTime.now())));

    assertThat(underTest.hasCertificate(HEALTH_OFFICE_VALID_CERT)).isTrue();

    final X509Certificate result = underTest.getCertificate(HEALTH_OFFICE_VALID_CERT);
    Assertions.assertThat(result)
        .isNotNull()
        .returns("X.509", X509Certificate::getType)
        .returns("SHA256withRSA", X509Certificate::getSigAlgName)
        .returns(
            "ST=Berlin, SERIALNUMBER=CSM014238886, L=Berlin, CN=OEGD-1.01.0.53., OU=DEMIS, O=Robert Koch-Institut, C=DE",
            c -> c.getSubjectX500Principal().toString());
  }

  @Test
  void certificateNotAvailable() {
    final var healthOffice = "HO-Unknown";
    final var exception =
        catchThrowableOfType(
            () -> underTest.getCertificate(healthOffice), NpsServiceException.class);

    assertThat(underTest.hasCertificate(healthOffice)).isFalse();

    assertThat(exception)
        .isNotNull()
        .returns(HEALTH_OFFICE_CERTIFICATE.name(), NpsServiceException::getErrorCode)
        .returns(HEALTH_OFFICE_CERTIFICATE.getHttpStatus(), NpsServiceException::getResponseStatus)
        .returns(
            "no certificate found for health office " + healthOffice,
            NpsServiceException::getMessage);
  }

  @Test
  void certificateInvalid() throws IOException {
    Mockito.when(certificateRepository.findById(HEALTH_OFFICE_VALID_CERT))
        .thenReturn(
            java.util.Optional.of(
                new CertificateDataEntity(
                    HEALTH_OFFICE_VALID_CERT, new byte[1], LocalDateTime.now())));

    final var exception =
        catchThrowableOfType(
            () -> underTest.getCertificate(HEALTH_OFFICE_VALID_CERT), NpsServiceException.class);

    assertThat(exception)
        .isNotNull()
        .returns(HEALTH_OFFICE_CERTIFICATE.name(), NpsServiceException::getErrorCode)
        .returns(HEALTH_OFFICE_CERTIFICATE.getHttpStatus(), NpsServiceException::getResponseStatus)
        .returns(
            "invalid certificate for health office " + HEALTH_OFFICE_VALID_CERT,
            NpsServiceException::getMessage);
  }
}

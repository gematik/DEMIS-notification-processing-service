package de.gematik.demis.nps.service.encryption;

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

import static de.gematik.demis.nps.error.ErrorCode.HEALTH_OFFICE_CERTIFICATE;

import de.gematik.demis.nps.base.data.CertificateRepository;
import de.gematik.demis.nps.error.NpsServiceException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
class CertificateProvider {

  private final CertificateRepository certificateRepository;

  private final CertificateFactory certificateFactory;

  public CertificateProvider(final CertificateRepository certificateRepository) {
    this.certificateRepository = certificateRepository;
    this.certificateFactory = createCertificateFactory();
  }

  private static CertificateFactory createCertificateFactory() {
    try {
      return CertificateFactory.getInstance("X.509");
    } catch (final CertificateException e) {
      throw new IllegalStateException("Error creating certificate factory with type X.509", e);
    }
  }

  /**
   * Check if a certificate is available for the given health office.
   *
   * @param healthOfficeId the health office id to be checked
   * @return true if a certificate is available, false otherwise
   */
  public boolean hasCertificate(final String healthOfficeId) {
    final var foundCert = certificateRepository.findById(healthOfficeId);
    if (foundCert.isPresent()
        && foundCert.get().encodedCertificate() != null
        && foundCert.get().encodedCertificate().length > 0) {
      return true;
    }
    log.info("health office {} has no valid certificate", healthOfficeId);
    return false;
  }

  /**
   * Get the certificate for the given health office.
   *
   * @param healthOfficeId the health office id to get the certificate for
   * @return the certificate
   * @throws NpsServiceException if no certificate is available for the given health office
   */
  public X509Certificate getCertificate(final String healthOfficeId) {
    final var foundCertificate =
        certificateRepository
            .findById(healthOfficeId)
            .orElseThrow(
                () ->
                    new NpsServiceException(
                        HEALTH_OFFICE_CERTIFICATE,
                        "no certificate found for health office " + healthOfficeId));

    try (final var streamIn = new ByteArrayInputStream(foundCertificate.encodedCertificate())) {
      return (X509Certificate) certificateFactory.generateCertificate(streamIn);
    } catch (final IOException e) {
      // could never happen, close is No-Op
      throw new IllegalStateException("error closing stream", e);
    } catch (final CertificateException e) {
      throw new NpsServiceException(
          HEALTH_OFFICE_CERTIFICATE, "invalid certificate for health office " + healthOfficeId, e);
    }
  }
}

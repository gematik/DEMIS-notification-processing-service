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
 * #L%
 */

import static de.gematik.demis.nps.test.DecryptionUtil.USER_1_01_0_53;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.gematik.demis.nps.test.DecryptionUtil;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

// This is required to execute PostConstruct automatically
@SpringBootTest(classes = DataEncryption.class, webEnvironment = WebEnvironment.NONE)
class DataEncryptionTest {

  @Autowired DataEncryption underTest;

  @Test
  void encryptDataAsymmetric() throws Exception {
    final byte[] data =
        "This text should be encrypted. Just for testing.".getBytes(StandardCharsets.UTF_8);

    final byte[] encrypted = underTest.encryptData(data, getPublicCertificate());

    assertNotNull(encrypted);
    assertArrayEquals(data, DecryptionUtil.decryptData(USER_1_01_0_53, encrypted));
  }

  private X509Certificate getPublicCertificate() throws CertificateException, IOException {
    final var certificateFactory = CertificateFactory.getInstance("X.509");
    try (final var streamIn =
        new ByteArrayInputStream(DecryptionUtil.getPublicCertificate(USER_1_01_0_53))) {
      return (X509Certificate) certificateFactory.generateCertificate(streamIn);
    }
  }
}

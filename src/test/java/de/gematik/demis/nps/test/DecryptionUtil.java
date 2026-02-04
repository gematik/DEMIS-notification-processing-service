package de.gematik.demis.nps.test;

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

import static de.gematik.demis.nps.test.TestData.readResource;
import static de.gematik.demis.nps.test.TestData.readResourceBytes;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import org.bouncycastle.cms.CMSEnvelopedData;
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class DecryptionUtil {
  public static final String USER_1_01_0_53 = "1.01.0.53.";
  public static final String USER_TEST_INT = "test-int";
  public static final String USER_1 = "1.";
  private static final String PUBLIC_CERTIFICATE_RESOURCE = "/certificates/%s.der";
  private static final String PRIVATE_CERTIFICATE_RESOURCE = "/certificates/%s.p12";
  private static final char[] KS_PASSWORD = "QV5BMIMP3L".toCharArray();

  public static byte[] getPublicCertificate(final String user) {
    return readResourceBytes(String.format(PUBLIC_CERTIFICATE_RESOURCE, user));
  }

  private static InputStream getPrivateCertificate(final String user) {
    return readResource(String.format(PRIVATE_CERTIFICATE_RESOURCE, user));
  }

  public static byte[] decryptData(final String user, final byte[] encrypted) throws Exception {
    return new CMSEnvelopedData(encrypted)
        .getRecipientInfos()
        .getRecipients()
        .iterator()
        .next()
        .getContent(
            new JceKeyTransEnvelopedRecipient(getPrivateKey(user))
                .setProvider(BouncyCastleProvider.PROVIDER_NAME));
  }

  private static PrivateKey getPrivateKey(final String user)
      throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException {
    final KeyStore keyStore = getKeyStore(user);
    final String keyAlias = keyStore.aliases().nextElement();
    return (PrivateKey) keyStore.getKey(keyAlias, KS_PASSWORD);
  }

  private static KeyStore getKeyStore(final String user) {
    try {
      final KeyStore keyStore;
      keyStore = KeyStore.getInstance("PKCS12", BouncyCastleProvider.PROVIDER_NAME);
      keyStore.load(getPrivateCertificate(user), KS_PASSWORD);
      return keyStore;
    } catch (KeyStoreException
        | NoSuchProviderException
        | IOException
        | NoSuchAlgorithmException
        | CertificateException e) {
      throw new IllegalStateException("error loading private keystore", e);
    }
  }
}

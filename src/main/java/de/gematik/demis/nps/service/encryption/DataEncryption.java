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
 * For additional notes and disclaimer from gematik and in case of changes by gematik,
 * find details in the "Readme" file.
 * #L%
 */

import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.error.NpsServiceException;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.RSAESOAEPparams;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.CMSEnvelopedData;
import org.bouncycastle.cms.CMSEnvelopedDataGenerator;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OutputEncryptor;
import org.springframework.stereotype.Service;

@Service
@Slf4j
class DataEncryption {

  private static final ASN1ObjectIdentifier SYMMETRIC_ALG = CMSAlgorithm.AES256_CBC;
  private static final AlgorithmIdentifier RSAES_OAEP_ALGORITHM_IDENTIFIER =
      new AlgorithmIdentifier(
          PKCSObjectIdentifiers.id_RSAES_OAEP,
          new RSAESOAEPparams(
              new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256, DERNull.INSTANCE),
              new AlgorithmIdentifier(
                  PKCSObjectIdentifiers.id_mgf1,
                  new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256, DERNull.INSTANCE)),
              RSAESOAEPparams.DEFAULT_P_SOURCE_ALGORITHM));

  @PostConstruct
  void setup() {
    Security.addProvider(new BouncyCastleProvider());
  }

  private JceKeyTransRecipientInfoGenerator getKeyTransRecipientInfoGeneratorForRsaesOaep(
      final X509Certificate cert) throws CertificateEncodingException {
    return new JceKeyTransRecipientInfoGenerator(cert, RSAES_OAEP_ALGORITHM_IDENTIFIER)
        .setProvider(BouncyCastleProvider.PROVIDER_NAME);
  }

  private OutputEncryptor generateEncryptor() throws CMSException {
    return new JceCMSContentEncryptorBuilder(SYMMETRIC_ALG)
        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
        .build();
  }

  /**
   * Encrypts data via CMS and public key.
   *
   * @param data to be encrypted
   * @param certificate
   * @return encrypted Data
   */
  public byte[] encryptData(final byte[] data, final X509Certificate certificate) {
    try {
      final var cmsEnvelopedDataGenerator = new CMSEnvelopedDataGenerator();
      final var jce = getKeyTransRecipientInfoGeneratorForRsaesOaep(certificate);
      cmsEnvelopedDataGenerator.addRecipientInfoGenerator(jce);

      final CMSTypedData msg = new CMSProcessableByteArray(data);
      final CMSEnvelopedData cmsEnvelopedData =
          cmsEnvelopedDataGenerator.generate(msg, generateEncryptor());

      return cmsEnvelopedData.getEncoded();
    } catch (final IOException | CMSException | CertificateEncodingException e) {
      throw new NpsServiceException(ErrorCode.ENCRYPTION, null, e);
    }
  }
}

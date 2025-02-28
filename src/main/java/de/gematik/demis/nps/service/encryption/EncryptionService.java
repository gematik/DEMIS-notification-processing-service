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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.gematik.demis.nps.service.healthoffice.SubsidiaryService;
import de.gematik.demis.nps.service.notification.Notification;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EncryptionService {

  private final DataEncryption dataEncryption;
  private final CertificateProvider certificateProvider;
  private final FhirContext fhirContext;
  private final BinaryCreator binaryCreator;
  private final SubsidiaryService subsidiaryService;

  /** Encrypt a bundle for the health office */
  public Binary encryptFor(final Bundle bundle, final String healthOffice) {
    return encryptInternal(bundle, healthOffice);
  }

  public Binary encryptForResponsibleHealthOffice(final Notification notification) {
    final String healthOfficeId =
        notification
            .getResponsibleHealthOfficeId()
            .orElseThrow(() -> new IllegalStateException("responsible health office is not set"));
    return encryptInternal(notification.getBundle(), healthOfficeId);
  }

  public Optional<Binary> encryptForSubsidiary(final Notification notification) {
    return notification
        .getResponsibleHealthOfficeId()
        .flatMap(subsidiaryService::getSubsidiaryId)
        .filter(certificateProvider::hasCertificate)
        .map(
            targetHealthOfficeId ->
                encryptInternal(notification.getBundle(), targetHealthOfficeId));
  }

  private Binary encryptInternal(final Bundle content, final String targetHealthOffice) {
    return binaryCreator
        .builder()
        .addTags(content.getMeta().copy().getTag())
        // TODO remove when feature.flag.notifications.7_4 is removed
        .addRelatedNotificationIdentifierTag(content.getIdentifier())
        .setContent(encryptBundle(content, targetHealthOffice))
        .setResponsibleHealthOfficeTag(targetHealthOffice)
        .build();
  }

  private byte[] encryptBundle(final Bundle bundle, final String targetHealthOfficeId) {
    final X509Certificate certificate = certificateProvider.getCertificate(targetHealthOfficeId);

    final IParser parser = fhirContext.newXmlParser();
    final String xmlPlain = parser.encodeResourceToString(bundle);

    return dataEncryption.encryptData(xmlPlain.getBytes(StandardCharsets.UTF_8), certificate);
  }
}

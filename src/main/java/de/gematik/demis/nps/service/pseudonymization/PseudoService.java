package de.gematik.demis.nps.service.pseudonymization;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.demis.nps.base.profile.DemisExtensions;
import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.service.Statistics;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.service.notification.NotificationUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Base64BinaryType;
import org.hl7.fhir.r4.model.Extension;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PseudoService {

  private final PseudonymizationServiceClient pseudonymizationServiceClient;
  private final PseudoStorageServiceClient storageServiceClient;
  private final NotificationUpdateService notificationUpdateService;
  private final ObjectMapper objectMapper;
  private final Statistics statistics;

  /**
   * Pseudo Call BundleId (aufpassen welche), Patient (Name), DiseaseCode -> Achtung Profile
   * CodeMapping erforderlich
   *
   * <p>Store Call: PseudonymResponse BundleId Responsible HealthOfficeId
   *
   * <p>Notification Update PseudonymResponse Patient->addExtention PseudonymRecordType+Binary ->
   * extention wird von notByName gelesen
   *
   * @return true if the Pseudonym was processed and added to the Notification's bundle. Exceptions
   *     are handled internally and don't have to be logged again by the caller.
   */
  public boolean createAndStorePseudonymAndAddToNotification(final Notification notification) {
    try {
      final PseudonymizationResponse pseudonymizationResponse = createPseudonym(notification);
      storePseudonym(notification, pseudonymizationResponse);
      addPseudonymToFhirResource(notification, pseudonymizationResponse);
      return true;
    } catch (final RuntimeException e) {
      // We catch all exceptions here, so a failing pseudonymization does not abort the notification
      // processing. Pseudonymization can be considered optional.
      log.error("error in pseudonymization", e);
      statistics.incIgnoredErrorCounter(ErrorCode.NO_PSEUDONYM.getCode());
      return false;
    }
  }

  private PseudonymizationResponse createPseudonym(final Notification notification) {
    final PseudonymizationRequest pseudonymizationRequest =
        PseudonymizationRequest.from(notification);
    return pseudonymizationServiceClient.generatePseudonym(pseudonymizationRequest);
  }

  void storePseudonym(
      final Notification notification, final PseudonymizationResponse pseudonymizationResponse) {
    final String responsibleHealthOfficeId =
        notification.getResponsibleHealthOfficeId().orElseThrow();

    final var storageRequest =
        new PseudonymStorageRequest(
            pseudonymizationResponse.activePseudonym(),
            notification.getBundleIdentifier(),
            responsibleHealthOfficeId);

    storageServiceClient.store(storageRequest);
  }

  private void addPseudonymToFhirResource(
      final Notification notification, final PseudonymizationResponse pseudonymizationResponse) {
    final byte[] pseudonymBytes = jsonAsBytes(pseudonymizationResponse);

    final Extension ex = new Extension();
    ex.setUrl(DemisExtensions.EXTENSION_URL_PSEUDONYM);
    ex.setValue(new Base64BinaryType().setValue(pseudonymBytes));

    notificationUpdateService.addExtension(
        notification.getBundle(), notification.getNotifiedPerson(), ex);
  }

  private byte[] jsonAsBytes(final Object o) {
    // TODO check gleichheit zu NES
    try {
      return objectMapper.writeValueAsBytes(o);
    } catch (final JsonProcessingException e) {
      throw new IllegalStateException("error writing pseudonym as byte array", e);
    }
  }
}

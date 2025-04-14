package de.gematik.demis.nps.service.notification;

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

import static de.gematik.demis.nps.base.fhir.BundleQueries.findFirstResourceWithProfile;
import static de.gematik.demis.nps.base.profile.DemisProfiles.NOTIFIER_FACILITY_PROFILE;

import de.gematik.demis.nps.base.profile.DemisExtensions;
import de.gematik.demis.nps.base.profile.DemisProfiles;
import de.gematik.demis.nps.base.profile.DemisSystems;
import de.gematik.demis.nps.base.util.TimeProvider;
import de.gematik.demis.nps.base.util.Uuid5Generator;
import de.gematik.demis.nps.base.util.UuidGenerator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

/** This class enriches an incoming bundle with additional information. */
@Slf4j
@Component
@RequiredArgsConstructor
class NotificationEnrichment {

  private final TimeProvider timeProvider;
  private final UuidGenerator uuidGenerator;
  private final NotificationUpdateService updateService;

  /**
   * Enriches the given {@code context Bundle} with the following information:
   *
   * <ul>
   *   <li>An ID for the bundle
   *   <li>refresh/set lastUpdated
   *   <li>A time stamp for the received date on the composition
   *   <li>sender information
   * </ul>
   *
   * @param notification the bundle to be enriched
   * @param requestId
   */
  public void enrichNotification(final Notification notification, final String requestId) {
    final Bundle bundle = notification.getBundle();
    updateBundleId(bundle, requestId);
    setReceptionTimeStamp(notification.getComposition());
    writeSenderId(bundle, notification.getSender());
    updateService.updated(bundle);
  }

  private void updateBundleId(final Bundle bundle, final String requestId) {
    final String uuidFromRequestId;
    if (requestId != null && !requestId.isBlank()) {
      uuidFromRequestId = Uuid5Generator.generateType5UUID(requestId).toString();
      log.debug(
          "Setting new UUID 5 bunde id from request id: {} -> {}", requestId, uuidFromRequestId);
    } else {
      // TODO soll requestId immer gesetzt sein?
      log.warn("Cannot generate UUID 5 bundle id because request id is empty");
      uuidFromRequestId = uuidGenerator.generateUuid();
      log.debug("Setting new random UUID bunde id: {}", uuidFromRequestId);
    }

    bundle.setIdentifier(
        new Identifier()
            .setSystem(DemisSystems.NOTIFICATION_BUNDLE_IDENTIFIER_SYSTEM)
            .setValue(uuidFromRequestId));
  }

  private void setReceptionTimeStamp(Composition composition) {
    final Extension ex = new Extension();
    ex.setUrl(DemisExtensions.EXTENSION_URL_RECEPTION_TIME_STAMP_TYPE);
    ex.setValue(new DateTimeType(timeProvider.now()));
    composition.addExtension(ex);
  }

  /**
   * Stores the given {@code userId} as an identifier on either the notifier facility, or if not
   * present on the notifier.
   *
   * @param bundle the Bundle in which the notifier facility / notifier is looked up
   * @param userId the user ID to be saved as an identifier to the notifier facility / notifier
   */
  private void writeSenderId(final Bundle bundle, final String userId) {
    // TODO sender optional
    Optional<Organization> notifierOrganization =
        findFirstResourceWithProfile(bundle, Organization.class, NOTIFIER_FACILITY_PROFILE);
    notifierOrganization.ifPresentOrElse(
        o -> o.addIdentifier().setSystem(DemisSystems.SENDER_ID_SYSTEM).setValue(userId),
        () ->
            // else we try to write to notifier practitioner
            findFirstResourceWithProfile(bundle, Practitioner.class, DemisProfiles.NOTIFIER_PROFILE)
                .ifPresent(
                    p ->
                        p.addIdentifier()
                            .setSystem(DemisSystems.SENDER_ID_SYSTEM)
                            .setValue(userId)));
  }
}

package de.gematik.demis.nps.service.notbyname;

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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.gematik.demis.nps.base.profile.DemisSystems;
import de.gematik.demis.nps.base.util.UuidGenerator;
import de.gematik.demis.nps.service.notification.Notification;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Service;

/**
 * Creates the "not by name" bundle (nicht namentliches Bundle) for the RKI. Source bundle is
 * copied. All added extensions like pseudonym are also taken over. Notified person (Patient) is
 * anonymized.
 *
 * @see <a href="https://wiki.gematik.de/pages/viewpage.action?pageId=422118513">Inhalte
 *     "Nichtnamentlicher Meldungsauszüge" für Erregernachweise</a>
 */
@Service
@RequiredArgsConstructor
public class NotByNameService {

  private static final String NOT_BY_NAME_NOTIFICATION_TITLE_POSTFIX = " (nichtnamentlich)";

  private final PatientResourceTransformer patientResourceTransformer;
  private final FhirContext fhirContext;
  private final UuidGenerator uuidGenerator;

  public Bundle createNotificationNotByName(final Notification notification) {
    final Bundle bundle = notification.getBundle();

    // DEMIS-2803 here we could use the NBL
    final Bundle copiedBundle = copyBundle(bundle);

    // Create an own NotificationBundleId (MeldevorgangsId) for the not by name bundle
    copiedBundle.setIdentifier(
        new Identifier()
            .setSystem(DemisSystems.NOTIFICATION_BUNDLE_IDENTIFIER_SYSTEM)
            .setValue(uuidGenerator.generateUuid()));

    // Create a tag that references the NotificationBundleId (MeldevorgangsId) of the 'name'
    // notification
    final String originalBundleIdentifier = notification.getBundleIdentifier();
    // TODO remove when feature.flag.notifications.7_4 is removed
    copiedBundle
        .getMeta()
        .addTag()
        .setSystem(DemisSystems.RELATED_NOTIFICATION_CODING_SYSTEM)
        .setCode(originalBundleIdentifier)
        .setDisplay("Relates to message with identifier: " + originalBundleIdentifier);

    // Add a tag, if the notification is sent by a test user
    if (notification.isTestUser()) {
      copiedBundle
          .getMeta()
          .addTag()
          .setSystem(DemisSystems.TEST_USER_CODING_SYSTEM)
          .setCode(DemisSystems.TEST_USER_CODE);
    }

    // DEMIS-2803 here we could use the NBL
    obfuscateNotifiedPerson(notification, copiedBundle);
    changeTitleOfComposition((Composition) copiedBundle.getEntryFirstRep().getResource());

    return copiedBundle;
  }

  private void changeTitleOfComposition(final Composition composition) {
    final String notByNameTitle = composition.getTitle() + NOT_BY_NAME_NOTIFICATION_TITLE_POSTFIX;
    composition.setTitle(notByNameTitle);
  }

  private void obfuscateNotifiedPerson(
      final Notification sourceNotification, final Bundle targetBundle) {

    // remove everything from notified person and add not by name title to composition
    final Patient notByNameNotifiedPerson =
        patientResourceTransformer.transformNotifiedPerson(sourceNotification.getNotifiedPerson());

    // replace resource
    targetBundle.getEntry().stream()
        .filter(entry -> entry.getResource() instanceof Patient)
        .findFirst()
        .ifPresent(entry -> entry.setResource(notByNameNotifiedPerson));
  }

  private Bundle copyBundle(final Bundle bundle) {
    final IParser parser = fhirContext.newJsonParser();
    final String serialized = parser.encodeResourceToString(bundle);
    return parser.parseResource(Bundle.class, serialized);
  }
}

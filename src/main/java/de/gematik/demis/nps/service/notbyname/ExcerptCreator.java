package de.gematik.demis.nps.service.notbyname;

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

import de.gematik.demis.notification.builder.demis.fhir.notification.builder.infectious.disease.DiseaseExcerptBuilder;
import de.gematik.demis.notification.builder.demis.fhir.notification.builder.infectious.laboratory.LaboratoryExcerptBuilder;
import de.gematik.demis.nps.base.profile.DemisSystems;
import de.gematik.demis.nps.service.notification.Notification;
import org.hl7.fhir.r4.model.Bundle;

public class ExcerptCreator {

  private ExcerptCreator() {}

  public static Bundle createAnonymousBundle(final Notification notification) {
    Bundle excerptBundle =
        switch (notification.getType()) {
          case LABORATORY ->
              LaboratoryExcerptBuilder.createExcerptNotifiedPersonAnonymousFromNonNominalBundle(
                  notification.getBundle());

          case DISEASE ->
              DiseaseExcerptBuilder.createExcerptNotifiedPersonAnonymousFromNonNominalBundle(
                  notification.getBundle());
        };
    // Add a tag, if the notification is sent by a test user
    modifyBundleForTestUser(excerptBundle, notification.isTestUser());
    return excerptBundle;
  }

  public static Bundle createNotByNameBundle(final Notification notification) {
    Bundle excerptBundle =
        switch (notification.getType()) {
          case LABORATORY ->
              LaboratoryExcerptBuilder.createExcerptNotifiedPersonNotByNameFromNominalBundle(
                  notification.getBundle());

          case DISEASE ->
              DiseaseExcerptBuilder.createExcerptNotifiedPersonNotByNameFromNominalBundle(
                  notification.getBundle());
        };
    // Add a tag, if the notification is sent by a test user
    modifyBundleForTestUser(excerptBundle, notification.isTestUser());

    return excerptBundle;
  }

  private static void modifyBundleForTestUser(
      final Bundle excerptBundle, final boolean isTestUser) {
    if (isTestUser) {
      excerptBundle
          .getMeta()
          .addTag()
          .setSystem(DemisSystems.TEST_USER_CODING_SYSTEM)
          .setCode(DemisSystems.TEST_USER_CODE);
    }
  }
}

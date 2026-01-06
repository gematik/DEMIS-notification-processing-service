package de.gematik.demis.nps.integrationtest;

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

import static de.gematik.demis.nps.test.TestUtil.fhirResourceToJson;
import static de.gematik.demis.nps.test.TestUtil.getJsonParser;

import de.gematik.demis.nps.base.fhir.BundleQueries;
import java.util.List;
import java.util.function.Consumer;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;

class BundleModifier {
  static String modifyResource(final String jsonBundle, final Consumer<Bundle> bundleModifier) {
    final Bundle bundle = getJsonParser().parseResource(Bundle.class, jsonBundle);
    bundleModifier.accept(bundle);
    return fhirResourceToJson(bundle);
  }

  static String removePseudonymAndResponsibleTags(
      final String processedNotificationForHealthOffice) {
    // NRS service becomes an earlier version of the processed notification, so remove pseudonym and
    // responsible tags
    final Bundle bundle =
        getJsonParser().parseResource(Bundle.class, processedNotificationForHealthOffice);
    bundle.getMeta().setTag(List.of());
    removePseudonym(bundle);
    return fhirResourceToJson(bundle);
  }

  static void removePseudonym(final Bundle bundle) {
    final Patient patient = BundleQueries.findFirstResource(bundle, Patient.class).orElseThrow();
    patient.getMeta().setLastUpdated(null);
    patient.setExtension(List.of());
  }

  static void updateResponsibleHealthOffice(
      final Bundle bundle, final String oldHealthOffice, final String newHealthOffice) {
    bundle
        .getMeta()
        .getTag("https://demis.rki.de/fhir/CodeSystem/ResponsibleDepartment", oldHealthOffice)
        .setCode(newHealthOffice);
  }

  static void addTestUserTag(final Bundle bundle) {
    bundle
        .getMeta()
        .addTag()
        .setSystem("https://demis.rki.de/fhir/CodeSystem/TestUser")
        .setCode("testuser");
  }
}

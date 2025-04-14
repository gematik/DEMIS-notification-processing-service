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

import static de.gematik.demis.nps.test.TestData.diseaseBundle;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.demis.nps.base.util.TimeProvider;
import de.gematik.demis.nps.test.TestData;
import lombok.SneakyThrows;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.junit.jupiter.api.Test;

class NotificationUpdateServiceTest {

  private final NotificationUpdateService underTest =
      new NotificationUpdateService(new TimeProvider());

  @Test
  @SneakyThrows
  void addEntryCorrectly() {
    Bundle bundle = diseaseBundle();
    BundleEntryComponent provenance = TestData.getProvenance();
    int countOfEntries = bundle.getEntry().size();
    underTest.addEntry(bundle, provenance);
    assertThat(bundle.getEntry())
        .as("The bundle does not get the enriched entry added")
        .hasSize(countOfEntries + 1);
    assertThat(bundle.getEntry())
        .extracting("resource")
        .extracting("resourceType")
        .map(Object::toString)
        .contains("Provenance");
  }
}

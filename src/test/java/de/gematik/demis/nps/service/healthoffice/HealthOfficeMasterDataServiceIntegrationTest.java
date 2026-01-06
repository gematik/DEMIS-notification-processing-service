package de.gematik.demis.nps.service.healthoffice;

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

import static de.gematik.demis.nps.test.TestData.readResourceAsString;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.demis.nps.test.TestUtil;
import org.hl7.fhir.r4.model.Organization;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HealthOfficeMasterDataServiceIntegrationTest {

  private static final String EXPECTED =
"""
{
  "resourceType": "Organization",
  "id": "1.01.0.53.",
  "identifier": [ {
    "system": "https://demis.rki.de/fhir/CodeSystem/reportingSite",
    "value": "1.01.0.53."
  } ],
  "name": "Kreis Herzogtum Lauenburg | Gesundheitsamt",
  "telecom": [ {
    "system": "phone",
    "value": "+49 4541 888-380"
  }, {
    "system": "email",
    "value": "gesundheitsdienste@kreis-rz.de"
  } ],
  "address": [ {
    "line": [ "Barlachstr. 4" ],
    "city": "Ratzeburg",
    "postalCode": "23909"
  } ]
}
""";

  @Autowired HealthOfficeMasterDataService underTest;

  @Test
  void getHealthOfficeOrganization() {
    final Organization organization = underTest.getHealthOfficeOrganization("1.01.0.53.", false);
    assertThat(TestUtil.fhirResourceToJson(organization)).isEqualToIgnoringWhitespace(EXPECTED);
  }

  @Test
  void unknownHealthOfficeId() {
    final Organization organization = underTest.getHealthOfficeOrganization("test-int", false);
    assertThat(organization).isNull();
  }

  @Test
  void testHealthOffice() {
    final Organization organization = underTest.getHealthOfficeOrganization("test-int", true);
    assertThat(TestUtil.fhirResourceToJson(organization))
        .isEqualToIgnoringWhitespace(readResourceAsString("/healthoffice/test-organization.json"));
  }
}

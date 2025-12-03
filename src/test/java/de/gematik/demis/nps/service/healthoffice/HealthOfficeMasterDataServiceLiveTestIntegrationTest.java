package de.gematik.demis.nps.service.healthoffice;

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

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.demis.nps.test.TestUtil;
import org.hl7.fhir.r4.model.Organization;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "nps.health-offices-live-test=true")
class HealthOfficeMasterDataServiceLiveTestIntegrationTest {

  private static final String EXPECTED =
"""
{
  "resourceType": "Organization",
  "id": "1.test-oegd01",
  "identifier": [ {
    "system": "https://demis.rki.de/fhir/CodeSystem/reportingSite",
    "value": "1.test-oegd01"
  } ],
  "name": "Test-Gesundheitsamt 01 | Abteilung Gesundheitsschutz",
  "telecom": [ {
    "system": "phone",
    "value": "030 - 123 456-789"
  }, {
    "system": "fax",
    "value": "030 - 123 456-780"
  }, {
    "system": "email",
    "value": "oegd01@dummy.de"
  } ],
  "address": [ {
    "line": [ "Teststr. 1" ],
    "city": "Berlin",
    "postalCode": "10115"
  } ]
}
""";

  @Autowired HealthOfficeMasterDataService underTest;

  @Test
  void liveTestHealthOffice() {
    final Organization organization = underTest.getHealthOfficeOrganization("1.test-oegd01", false);
    assertThat(TestUtil.fhirResourceToJson(organization)).isEqualToIgnoringWhitespace(EXPECTED);
  }

  @Test
  void normalHealthOfficeDoesNotExist() {
    final Organization organization = underTest.getHealthOfficeOrganization("1.01.0.53.", false);
    assertThat(organization).isNull();
  }
}

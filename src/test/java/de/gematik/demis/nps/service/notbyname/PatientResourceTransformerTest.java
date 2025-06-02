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

import static de.gematik.demis.nps.test.TestUtil.fhirResourceToJson;
import static de.gematik.demis.nps.test.TestUtil.toDate;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.demis.notification.builder.demis.fhir.notification.builder.infectious.NotifiedPersonDataBuilder;
import de.gematik.demis.notification.builder.demis.fhir.notification.builder.technicals.HumanNameDataBuilder;
import de.gematik.demis.notification.builder.demis.fhir.notification.builder.technicals.TelecomDataBuilder;
import java.time.LocalDate;
import java.util.List;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Address.AddressUse;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

class PatientResourceTransformerTest {

  private static final String EXPECTED =
"""
{
  "resourceType" : "Patient",
  "id" : "246c2610-3a90-42b1-b28d-5821fd7ed15d",
  "meta" : {
    "profile" : [ "https://demis.rki.de/fhir/StructureDefinition/NotifiedPersonNotByName" ]
  },
  "gender" : "male",
  "birthDate" : "1979-05-01",
  "deceasedBoolean" : true,
  "address" : [ {
    "postalCode" : "123",
    "country" : "DEU"
  } ]
}
""";

  private final PatientResourceTransformer underTest = new PatientResourceTransformer();

  private static Address createAddress() {
    return new Address()
        .setCountry("DEU")
        .setPostalCode("12345")
        .setCity("Bochum")
        .addLine("Meinestr. 5")
        .setUse(AddressUse.HOME);
  }

  @Test
  void transformNotifiedPerson() {
    final Address address = createAddress();
    final Patient patient =
        new NotifiedPersonDataBuilder()
            .setGender(Enumerations.AdministrativeGender.MALE)
            .setBirthdate(toDate(LocalDate.of(1979, 5, 23)))
            .setHumanName(
                new HumanNameDataBuilder()
                    .setFamilyName("Mustermann")
                    .setGivenEntry(List.of("Max", "Christian"))
                    .setSalutation(HumanNameDataBuilder.Salutation.MR)
                    .build())
            .addAddress(address)
            .addTelecom(new TelecomDataBuilder().setValue("1234").build())
            .build();
    patient.setDeceased(new BooleanType(true));
    patient.setId("246c2610-3a90-42b1-b28d-5821fd7ed15d");

    final String patientSnapshot = fhirResourceToJson(patient);

    final Patient result = underTest.transformNotifiedPerson(patient);

    // assert that patient is not modified
    assertThat(fhirResourceToJson(patient)).isEqualTo(patientSnapshot);

    // assert transformed patient (new resource)
    assertThat(fhirResourceToJson(result)).isEqualToIgnoringWhitespace(EXPECTED);
    assertThat(result.hasName()).isFalse();
  }
}

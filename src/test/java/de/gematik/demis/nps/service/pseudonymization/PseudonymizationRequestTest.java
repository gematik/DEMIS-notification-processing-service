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
 * #L%
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import de.gematik.demis.notification.builder.demis.fhir.notification.builder.infectious.NotifiedPersonDataBuilder;
import de.gematik.demis.notification.builder.demis.fhir.notification.builder.infectious.laboratory.NotificationBundleLaboratoryDataBuilder;
import de.gematik.demis.notification.builder.demis.fhir.notification.builder.infectious.laboratory.NotificationLaboratoryDataBuilder;
import de.gematik.demis.notification.builder.demis.fhir.notification.builder.technicals.HumanNameDataBuilder;
import de.gematik.demis.nps.service.notification.Notification;
import java.util.List;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Specimen;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PseudonymizationRequestTest {

  private static final String DEFAULT_BUNDLE_ID = "BUNDLE_ID";
  private static final String DISEASE_CODE = "ANY";

  private static Stream<Arguments> generatePatientsForPotentialNPEs() {
    return Stream.of(
        Arguments.of(
            // Only birthday is set
            new NotifiedPersonDataBuilder().setBirthdate("01.01.1994").build()),
        // Human name but nothing relevant is set
        Arguments.of(
            new NotifiedPersonDataBuilder()
                .setHumanName(new HumanNameDataBuilder().setText("Some irrelevant text").build())
                .build()),
        // Only firstname is set
        Arguments.of(
            new NotifiedPersonDataBuilder()
                .setHumanName(new HumanNameDataBuilder().addGivenName("FirstName").build())
                .build()),
        // Only lastname is set
        Arguments.of(
            new NotifiedPersonDataBuilder()
                .setHumanName(new HumanNameDataBuilder().setFamilyName("FamilyName").build())
                .build()),
        // Nothing is set
        Arguments.of(new NotifiedPersonDataBuilder().build()),
        // Only lastname & birthday
        Arguments.of(
            new NotifiedPersonDataBuilder()
                .setBirthdate("01.01.1994")
                .setHumanName(new HumanNameDataBuilder().setFamilyName("FamilyName").build())
                .build()));
  }

  @ParameterizedTest
  @MethodSource("generatePatientsForPotentialNPEs")
  void thatMissingOptionalElementsDontCauseNPEs(final Patient patient) {
    final Bundle bundle =
        new NotificationBundleLaboratoryDataBuilder()
            .setNotificationLaboratory(
                new NotificationLaboratoryDataBuilder().setNotifiedPerson(patient).build())
            .setNotifiedPerson(patient)
            .setSpecimen(List.of(new Specimen()))
            .setLaboratoryReport(new DiagnosticReport())
            .setIdentifier(new Identifier().setValue(DEFAULT_BUNDLE_ID))
            .build();

    final Notification notification =
        Notification.builder().bundle(bundle).diseaseCode(DISEASE_CODE).build();

    final PseudonymizationRequest request = PseudonymizationRequest.from(notification);
    assertThat(request.dateOfBirth()).isNotNull();
    assertThat(request.firstName()).isNotNull();
    assertThat(request.familyName()).isNotNull();
    assertThat(request.diseaseCode()).isNotNull();
    assertThat(request.notificationBundleId()).isNotNull();
  }
}

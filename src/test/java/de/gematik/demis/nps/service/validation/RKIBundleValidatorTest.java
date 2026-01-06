package de.gematik.demis.nps.service.validation;

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

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.demis.notification.builder.demis.fhir.notification.builder.infectious.NotifiedPersonAnonymousDataBuilder;
import de.gematik.demis.notification.builder.demis.fhir.notification.builder.infectious.NotifiedPersonNominalDataBuilder;
import de.gematik.demis.notification.builder.demis.fhir.notification.builder.infectious.laboratory.NotificationBundleLaboratoryAnonymousDataBuilder;
import de.gematik.demis.notification.builder.demis.fhir.notification.builder.infectious.laboratory.NotificationBundleLaboratoryDataBuilder;
import de.gematik.demis.notification.builder.demis.fhir.notification.builder.infectious.laboratory.NotificationBundleLaboratoryNonNominalDataBuilder;
import de.gematik.demis.notification.builder.demis.fhir.notification.builder.infectious.laboratory.NotificationLaboratoryDataBuilder;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

class RKIBundleValidatorTest {

  public static final Patient NOTIFIED_PERSON =
      new NotifiedPersonNominalDataBuilder().setDefault().setId("np1").build();
  public static final Composition COMPOSITION_WITH_NOTIFIED_PERSON =
      new NotificationLaboratoryDataBuilder()
          .setDefault()
          .setNotifiedPerson(NOTIFIED_PERSON)
          .build();
  private static final String RKI = "1.";

  private final RKIBundleValidator validator = new RKIBundleValidator();

  private static Bundle notifiedPersonBundle() {
    return new NotificationBundleLaboratoryDataBuilder()
        .setDefaults()
        .setNotificationLaboratory(COMPOSITION_WITH_NOTIFIED_PERSON)
        // DEMIS-2803 you can remove this line below
        .setNotifiedPerson(NOTIFIED_PERSON)
        .build();
  }

  private static Bundle nonNominalBundleWithNotifiedPerson() {
    return new NotificationBundleLaboratoryNonNominalDataBuilder()
        .setDefaults()
        .setNotificationLaboratory(COMPOSITION_WITH_NOTIFIED_PERSON)
        // DEMIS-2803 you can remove this line below
        .setNotifiedPerson(NOTIFIED_PERSON)
        .build();
  }

  private static Bundle anonymousBundle() {
    final NotificationBundleLaboratoryAnonymousDataBuilder bundle =
        new NotificationBundleLaboratoryAnonymousDataBuilder();
    bundle.setDefaults();
    final Patient anonymousPerson = new NotifiedPersonAnonymousDataBuilder().setId("ap1").build();
    bundle
        .setNotificationLaboratory(
            new NotificationLaboratoryDataBuilder()
                .setDefault()
                .setNotifiedPerson(anonymousPerson)
                .build())
        // DEMIS-2803 you can remove this line below
        .setNotifiedPerson(anonymousPerson);
    return bundle.build();
  }

  @Test
  void thatRKICanReceiveBundlesWithoutNotifiedPersonSubject() {
    final BundleValidationResult validationResult = validator.isValidBundle(anonymousBundle(), RKI);
    assertThat(validationResult.isValid()).isTrue();
    assertThat(validationResult.reason()).isEqualTo("Valid");
  }

  @Test
  void thatRKICanNeverReceiveANotifiedPersonSubject() {
    BundleValidationResult validationResult = validator.isValidBundle(notifiedPersonBundle(), RKI);
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.reason()).isEqualTo("Receiver 1. can't receive NotifiedPerson");

    validationResult = validator.isValidBundle(nonNominalBundleWithNotifiedPerson(), RKI);
    assertThat(validationResult.isValid()).isFalse();
    assertThat(validationResult.reason()).isEqualTo("Receiver 1. can't receive NotifiedPerson");
  }

  @Test
  void thatAnyoneElseCanReceiveAllKindsOfBundles() {
    BundleValidationResult validationResult =
        validator.isValidBundle(notifiedPersonBundle(), "anyone");
    assertThat(validationResult.isValid()).isTrue();
    assertThat(validationResult.reason()).isEqualTo("Valid");

    validationResult = validator.isValidBundle(nonNominalBundleWithNotifiedPerson(), "anyone");
    assertThat(validationResult.isValid()).isTrue();
    assertThat(validationResult.reason()).isEqualTo("Valid");

    validationResult = validator.isValidBundle(anonymousBundle(), "anyone");
    assertThat(validationResult.isValid()).isTrue();
    assertThat(validationResult.reason()).isEqualTo("Valid");
  }
}

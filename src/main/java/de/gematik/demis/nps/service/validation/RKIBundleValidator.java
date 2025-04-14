package de.gematik.demis.nps.service.validation;

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

import com.google.common.collect.Sets;
import de.gematik.demis.notification.builder.demis.fhir.notification.utils.DemisConstants;
import de.gematik.demis.notification.builder.demis.fhir.notification.utils.Metas;
import de.gematik.demis.nps.base.fhir.BundleQueries;
import java.util.Optional;
import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Service;

/** Validate bundles for the RKI receiver. */
@Service
public class RKIBundleValidator {

  private static final Set<String> FORBIDDEN_PROFILES =
      Set.of(DemisConstants.PROFILE_NOTIFIED_PERSON);

  private static final String RKI_RECEIVER_ID = "1.";

  private static final BundleValidationResult VALID_RESULT =
      new BundleValidationResult(true, "Valid");

  /**
   * Check whether the given Bundle is valid for the receiver. If the receiver is NOT RKI it is
   * automatically assumed valid.
   */
  public BundleValidationResult isValidBundle(final Bundle bundle, final String receiverId) {
    if (!receiverId.equals(RKI_RECEIVER_ID)) {
      return VALID_RESULT;
    }

    // After DEMIS-2803
    //    final Optional<Patient> patient = Bundles.subjectFrom(bundle);
    final Optional<Patient> patient = BundleQueries.findFirstResource(bundle, Patient.class);
    if (patient.isEmpty()) {
      return VALID_RESULT;
    }

    final Set<String> actualProfiles = Metas.profilesFrom(patient.get());
    final Sets.SetView<String> forbiddenProfiles =
        Sets.intersection(FORBIDDEN_PROFILES, actualProfiles);
    if (forbiddenProfiles.isEmpty()) {
      return VALID_RESULT;
    }
    return new BundleValidationResult(false, "Receiver 1. can't receive NotifiedPerson");
  }
}

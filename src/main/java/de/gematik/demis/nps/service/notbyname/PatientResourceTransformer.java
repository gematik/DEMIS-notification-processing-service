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

import static org.apache.commons.lang3.StringUtils.truncate;

import de.gematik.demis.notification.builder.demis.fhir.notification.utils.DemisConstants;
import de.gematik.demis.nps.base.profile.DemisExtensions;
import de.gematik.demis.nps.base.profile.DemisProfiles;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatientResourceTransformer {

  /**
   * Transforms patient resource to comply to the federal regulations about its content.
   *
   * @param sourceNotifiedPerson notified Person containing personal information
   * @return a new anonymized {@link Patient} resource.
   */
  public Patient transformNotifiedPerson(final Patient sourceNotifiedPerson) {
    final var result = new Patient();

    // TODO nicht im NES. Finde ich aber sinnvoll
    result.setId(sourceNotifiedPerson.getId());

    // Set gender, birthday and deceased
    result.setGender(sourceNotifiedPerson.getGender());
    final Date birthDate = sourceNotifiedPerson.getBirthDate();
    if (birthDate != null) {
      result.setBirthDate(DateUtils.truncate(birthDate, Calendar.MONTH));
    }
    result.setDeceased(sourceNotifiedPerson.getDeceased());

    final List<Address> notByNameAddresses =
        sourceNotifiedPerson.getAddress().stream()
            .filter(Address::hasPostalCode)
            .map(this::createNotByNameAddress)
            .toList();

    result.setAddress(notByNameAddresses);

    final Extension pseudonym = getPseudonymExtension(sourceNotifiedPerson);
    if (pseudonym != null) {
      result.addExtension(pseudonym.copy());
    } else {
      log.warn("Pseudonym missing : {}", sourceNotifiedPerson.getId());
    }

    // Add specific profile for the notByName version of the NotifiedPerson
    DemisProfiles.NOTIFIED_PERSON_NOT_BY_NAME_PROFILE.applyTo(result);

    return result;
  }

  private Address createNotByNameAddress(Address oldAddress) {
    final var newAddress = new Address();

    newAddress.setPostalCode(truncate(oldAddress.getPostalCode(), 3));

    newAddress.setCountry(oldAddress.getCountry());

    var addressUse = oldAddress.getExtensionByUrl(DemisConstants.STRUCTURE_DEFINITION_ADDRESS_USE);
    if (addressUse != null) {
      newAddress.addExtension(addressUse.copy());
    }

    return newAddress;
  }

  private Extension getPseudonymExtension(final Patient notifiedPerson) {
    return notifiedPerson.getExtensionByUrl(DemisExtensions.EXTENSION_URL_PSEUDONYM);
  }
}

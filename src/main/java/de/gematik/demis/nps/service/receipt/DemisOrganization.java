package de.gematik.demis.nps.service.receipt;

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

import lombok.experimental.UtilityClass;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Organization.OrganizationContactComponent;

/**
 * This class defines a {@link Organization} resource with the data to represent the DEMIS
 * organization. It is used in the context of the notification receipt.
 */
@UtilityClass
class DemisOrganization {

  private static final String ID = "DEMIS";
  private static final String EMAIL = "demis-support@rki.de";

  /**
   * @return A {@link Organization} resource representing DEMIS.
   */
  public static Organization createInstance() {
    final var result =
        new Organization()
            .setName(ID)
            .addContact(
                new OrganizationContactComponent()
                    .addTelecom(
                        new ContactPoint().setSystem(ContactPointSystem.EMAIL).setValue(EMAIL)));
    result.setId(ID);
    return result;
  }
}

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
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import de.gematik.demis.nps.base.profile.DemisSystems;
import lombok.experimental.UtilityClass;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;

@UtilityClass
class HealthOfficeOrganizationCreator {
  private static final String HEALTH_DEPARTMENT_NAME_DELIMITER = " | ";

  static Organization createOrganization(final TransmittingSite data) {
    Organization result = new Organization();
    result
        .setName(data.name() + HEALTH_DEPARTMENT_NAME_DELIMITER + data.department())
        .addIdentifier(
            new Identifier()
                .setSystem(DemisSystems.REPORTING_SITE_CODING_SYSTEM)
                .setValue(data.code()))
        .setId(data.code());

    result.addAddress().addLine(data.street()).setPostalCode(data.zipCode()).setCity(data.city());

    addContactPoint(result, data.tel(), ContactPoint.ContactPointSystem.PHONE);
    addContactPoint(result, data.fax(), ContactPoint.ContactPointSystem.FAX);
    addContactPoint(result, data.email(), ContactPoint.ContactPointSystem.EMAIL);

    return result;
  }

  private static void addContactPoint(
      final Organization organization,
      final String value,
      final ContactPoint.ContactPointSystem system) {
    if (value != null && !value.isBlank()) {
      organization.addTelecom(new ContactPoint().setSystem(system).setValue(value));
    }
  }
}

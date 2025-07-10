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

import de.gematik.demis.notification.builder.demis.fhir.notification.builder.receipt.RkiOrganizationBuilder;
import java.util.Map;
import org.hl7.fhir.r4.model.Organization;

public class HealthOfficeMasterDataService {

  private static final Organization RKI_ORGANIZATION =
      new RkiOrganizationBuilder().createRkiOrganization();
  private final Map<String, Organization> healthOfficeIdToOrganization;

  public HealthOfficeMasterDataService(Map<String, Organization> healthOfficeIdToOrganization) {

    healthOfficeIdToOrganization.put("1.", RKI_ORGANIZATION);
    this.healthOfficeIdToOrganization = healthOfficeIdToOrganization;
  }

  private static Organization createTestHealthOffice(final String id) {
    final String tel = "";
    final String fax = "";
    final String email = "test@test.com";
    final String department = "Test Department";
    final String name = "Test Gesundheitsamt";
    final String zipCode = "00000";
    final String street = "Teststraße";
    final String city = "Teststadt";
    return HealthOfficeOrganizationCreator.createOrganization(
        new TransmittingSite(id, tel, fax, email, department, name, street, zipCode, city));
  }

  public Organization getHealthOfficeOrganization(
      final String id, final boolean isTestHealthOffice) {
    return isTestHealthOffice ? createTestHealthOffice(id) : healthOfficeIdToOrganization.get(id);
  }
}

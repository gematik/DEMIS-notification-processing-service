package de.gematik.demis.nps.service.notification;

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

import static de.gematik.demis.nps.base.profile.DemisSystems.HEALTH_DEPARTMENT_FOR_CURRENT_ADDRESS_CODING_SYSTEM;
import static de.gematik.demis.nps.base.profile.DemisSystems.HEALTH_DEPARTMENT_FOR_ORDINARY_ADDRESS_CODING_SYSTEM;
import static de.gematik.demis.nps.base.profile.DemisSystems.HEALTH_DEPARTMENT_FOR_PRIMARY_ADDRESS_CODING_SYSTEM;
import static de.gematik.demis.nps.base.profile.DemisSystems.NOTIFIER_HEALTH_DEPARTMENT_CODING_SYSTEM;
import static de.gematik.demis.nps.base.profile.DemisSystems.RESPONSIBLE_HEALTH_DEPARTMENT_CODING_SYSTEM;
import static de.gematik.demis.nps.base.profile.DemisSystems.SUBMITTER_HEALTH_DEPARTMENT_CODING_SYSTEM;

import de.gematik.demis.nps.base.util.TimeProvider;
import de.gematik.demis.nps.service.routing.AddressOriginEnum;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Meta;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationUpdateService {

  private final TimeProvider timeProvider;

  private static String toResponsibleCodingSystem(final AddressOriginEnum addressOrigin) {
    return switch (addressOrigin) {
      case NOTIFIED_PERSON_PRIMARY -> HEALTH_DEPARTMENT_FOR_PRIMARY_ADDRESS_CODING_SYSTEM;
      case NOTIFIED_PERSON_CURRENT -> HEALTH_DEPARTMENT_FOR_CURRENT_ADDRESS_CODING_SYSTEM;
      case NOTIFIED_PERSON_ORDINARY -> HEALTH_DEPARTMENT_FOR_ORDINARY_ADDRESS_CODING_SYSTEM;
      case NOTIFIED_PERSON_OTHER -> null;
      case NOTIFIER -> NOTIFIER_HEALTH_DEPARTMENT_CODING_SYSTEM;
      case SUBMITTER -> SUBMITTER_HEALTH_DEPARTMENT_CODING_SYSTEM;
    };
  }

  public void setResponsibleDepartment(
      @Nonnull final Bundle bundle, @Nonnull final String healthOffice) {
    addOrReplaceMetaTag(bundle, RESPONSIBLE_HEALTH_DEPARTMENT_CODING_SYSTEM, healthOffice);
  }

  public void addHealthOfficeTags(
      @Nonnull final Bundle notification,
      @Nonnull final Map<AddressOriginEnum, String> healthOffices) {
    healthOffices.forEach(
        (addressOrigin, healthOffice) ->
            addHealthOfficeTag(notification, addressOrigin, healthOffice));
  }

  private void addHealthOfficeTag(
      final Bundle notification, final AddressOriginEnum addressOrigin, final String healthOffice) {
    final String codingSystem = toResponsibleCodingSystem(addressOrigin);
    if (codingSystem != null) {
      addMetaTag(notification, codingSystem, healthOffice);
    }
  }

  public void addOrReplaceMetaTag(
      final Bundle bundle, final String codingSystem, final String code) {
    final Meta meta = bundle.getMeta();
    Coding coding =
        meta.getTag().stream()
            .filter(t -> codingSystem.equals(t.getSystem()))
            .findFirst()
            .orElseGet(() -> meta.addTag().setSystem(codingSystem));

    if (!Objects.equals(coding.getCode(), code)) {
      coding.setCode(code);
      updated(bundle);
    }
  }

  public void addMetaTag(final Bundle bundle, final String tagCodingSystem, final String code) {
    bundle.getMeta().addTag().setSystem(tagCodingSystem).setCode(code);
    updated(bundle);
  }

  public void addExtension(
      final Bundle bundle, final DomainResource resource, final Extension extension) {
    resource.addExtension(extension);
    updated(bundle, resource);
  }

  public void addEntry(Bundle bundle, BundleEntryComponent entry) {
    bundle.addEntry(entry);
    updated(bundle);
  }

  public void updated(final IBaseResource... resources) {
    final Date now = timeProvider.now();
    for (final var resource : resources) {
      resource.getMeta().setLastUpdated(now);
    }
  }
}

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
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import de.gematik.demis.nps.base.util.TimeProvider;
import java.util.Date;
import java.util.Objects;
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

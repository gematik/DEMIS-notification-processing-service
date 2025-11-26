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

import static de.gematik.demis.nps.base.profile.DemisExtensions.*;
import static de.gematik.demis.nps.base.profile.DemisExtensions.EXTENSION_URL_ADDRESS_USE;

import java.util.ArrayDeque;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseExtension;
import org.hl7.fhir.instance.model.api.IBaseHasExtensions;
import org.hl7.fhir.instance.model.api.IBaseMetaType;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Service;

/**
 * This class strips a given notification bundle of information not wanted or about to be set by the
 * notification api.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class NotificationCleaning {

  // TODO can this list easily be extracted from the schema files?
  public static final Set<String> EXTENSION_WHITE_LIST =
      Set.of(
          EXTENSION_URL_SALUTATION,
          EXTENSION_URL_ADDRESS_USE,
          EXTENSION_URL_COMMENT,
          EXTENSION_URL_TRANSACTION_ID,
          EXTENSION_URL_FACILTY_ADDRESS_NOTIFIED_PERSON,
          EXTENSION_URL_PRECINCT,
          EXTENSION_URL_STREET_NAME,
          EXTENSION_URL_HOUSE_NUMBER,
          EXTENSION_URL_ADDITIONAL_LOCATOR,
          EXTENSION_URL_POST_BOX,
          EXTENSION_URL_HUMANNAME_NAMENSZUSATZ,
          EXTENSION_URL_HUMANNAME_OWN_NAME,
          EXTENSION_URL_HUMANNAME_OWN_PREFIX,
          EXTENSION_URL_HUMANNAME_PREFIX,
          EXTENSION_URL_HOSPITALIZATION_NOTE,
          EXTENSION_URL_HOSPITALIZATION_REGION,
          EXTENSION_URL_GENDER);
  private final NotificationUpdateService updateService;

  /**
   * Removes all tags and security information from the given {@code content} and all sub {@link
   * Resource}s.<br>
   * Also strips all extensions that are not intended to be used as part of the incoming
   * notification.
   *
   * @param bundle the Bundle to be cleaned (inplace)
   */
  public void cleanNotification(final Bundle bundle) {
    // Stripping meta info will also possible tags for health departments already present
    stripRecursively(bundle);
  }

  private void stripRecursively(Resource resource) {
    var toProcess = new ArrayDeque<Base>();
    toProcess.add(resource);
    while (!toProcess.isEmpty()) {
      Base element = toProcess.removeLast();
      if (element instanceof IBaseHasExtensions baseHasExtensions) {
        stripExtensions(baseHasExtensions, resource);
      }
      if (element instanceof IBaseResource baseResource) {
        stripMeta(baseResource, resource);
      }
      // all sub-elements: enqueue for processing
      element.children().stream().flatMap(p -> p.getValues().stream()).forEach(toProcess::add);
    }
  }

  private void stripExtensions(IBaseHasExtensions element, Resource rootResource) {
    // strip unknown extensions
    boolean update = false;
    for (var iterator = element.getExtension().iterator(); iterator.hasNext(); ) {
      IBaseExtension<?, ?> extension = iterator.next();
      String extensionUrl = extension.getUrl();
      if (extensionUrl == null) {
        continue;
      }
      // in the future, we may do more advanced checks, testing if extensions are applied
      // where they are allowed.
      if (!EXTENSION_WHITE_LIST.contains(extensionUrl)) {
        update = true;
        log.debug("Removed unknown extension with URL {}", extensionUrl);
        iterator.remove();
      }
    }
    // if we updated resource, change lastUpdated on resource and parent resource
    if (update) {
      if (element instanceof IBaseResource baseResource) {
        updateService.updated(rootResource, baseResource);
      } else {
        updateService.updated(rootResource);
      }
    }
  }

  private void stripMeta(IBaseResource resource, Resource rootResource) {
    IBaseMetaType meta = resource.getMeta();
    boolean update = !meta.getTag().isEmpty() || !meta.getSecurity().isEmpty();
    if (update) {
      meta.getTag().clear();
      meta.getSecurity().clear();
      updateService.updated(rootResource, resource);
    }
  }
}

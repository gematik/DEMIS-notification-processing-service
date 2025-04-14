package de.gematik.demis.nps.service.codemapping;

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

import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
class ReloadableCache<T, U> {

  private final String name;
  private final Supplier<Map<T, U>> cacheValuesSupplier;

  // map is immutable. In case of reloading just the map reference is changed and not the map itself
  @SuppressWarnings({"java:S3077"})
  private volatile Map<T, U> map = null;

  U getValue(final T key) {
    if (map == null) {
      // note: Once the map has been loaded, this case no longer occurs. synchronizing is not
      // required.
      loadCache();
    }

    final var mapLocal = map;
    final var result = mapLocal != null ? mapLocal.get(key) : null;
    if (result == null) {
      log.info(
          "No entry found for key {} in map {}. Map size = {}",
          key,
          name,
          mapLocal == null ? "n/a" : mapLocal.size());
    }
    return result;
  }

  void loadCache() {
    try {
      final var newMap = cacheValuesSupplier.get();
      if (newMap != null && !newMap.isEmpty()) {
        map = newMap;
        log.info("{} conceptmap cache (re)loaded. # entries = {}", name, newMap.size());
      }
    } catch (final RuntimeException e) {
      log.error("error fetching code map for " + name, e);
    }
  }
}

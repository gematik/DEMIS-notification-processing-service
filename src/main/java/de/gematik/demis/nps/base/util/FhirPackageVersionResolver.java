package de.gematik.demis.nps.base.util;

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

import de.gematik.demis.nps.config.NpsHeaders;
import java.util.Arrays;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Resolves the FHIR package version to be used for a given FHIR package. This is useful when
 * incoming requests use the old generic endpoint that doesn't already contain information about the
 * version.
 */
@Component
public class FhirPackageVersionResolver {

  /**
   * Resolves the version for the given {@code packageName} from the {@code defaultVersionsHeader}
   * string.
   *
   * @param defaultVersionsHeader semicolon-separated list of {@code package:version} pairs
   * @param packageName the FHIR package name to look up
   * @return an {@link Optional} containing the version, or empty if no entry matches
   * @throws IllegalStateException if {@code defaultVersionsHeader} is blank
   */
  public Optional<String> resolveVersion(String defaultVersionsHeader, String packageName) {
    if (StringUtils.isBlank(defaultVersionsHeader)) {
      throw new IllegalStateException(
          "Cannot resolve FHIR package version: header '%s' is blank."
              .formatted(NpsHeaders.HEADER_DEFAULT_FHIR_PACKAGE_VERSIONS));
    }

    return Arrays.stream(defaultVersionsHeader.split(";"))
        .map(String::trim)
        .map(this::toPackageVersionPair)
        .flatMap(Optional::stream)
        .filter(pair -> pair.packageName().equals(packageName))
        .map(PackageVersionPair::version)
        .findFirst();
  }

  private Optional<PackageVersionPair> toPackageVersionPair(String entry) {
    String[] parts = entry.split(":", 2);
    if (parts.length != 2 || StringUtils.isBlank(parts[0]) || StringUtils.isBlank(parts[1])) {
      return Optional.empty();
    }
    return Optional.of(new PackageVersionPair(parts[0].trim(), parts[1].trim()));
  }

  private record PackageVersionPair(String packageName, String version) {}
}

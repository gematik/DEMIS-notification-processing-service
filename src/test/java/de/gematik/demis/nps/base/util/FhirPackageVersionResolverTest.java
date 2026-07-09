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

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FhirPackageVersionResolverTest {

  private final FhirPackageVersionResolver underTest = new FhirPackageVersionResolver();

  @Test
  void resolveVersion_returnsVersionForMatchingPackage() {
    assertEquals(
        Optional.of("v7"), underTest.resolveVersion("disease:v7;laboratory:v6", "disease"));
    assertEquals(
        Optional.of("v6"), underTest.resolveVersion("disease:v7;laboratory:v6", "laboratory"));
  }

  @Test
  void resolveVersion_worksWithSingleEntry() {
    assertEquals(Optional.of("v3"), underTest.resolveVersion("disease:v3", "disease"));
  }

  @Test
  void resolveVersion_trimsWhitespaceAroundEntries() {
    assertEquals(
        Optional.of("v7"), underTest.resolveVersion(" disease : v7 ; laboratory : v6 ", "disease"));
  }

  @Test
  void resolveVersion_returnsEmptyWhenNoMatchingPackageFound() {
    assertEquals(Optional.empty(), underTest.resolveVersion("laboratory:v6", "disease"));
  }

  @Test
  void resolveVersion_returnsEmptyWhenHeaderIsGarbage() {
    assertEquals(Optional.empty(), underTest.resolveVersion("quatch", "disease"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void resolveVersion_throwsWhenHeaderIsBlank(String blankHeader) {
    assertThrows(
        IllegalStateException.class, () -> underTest.resolveVersion(blankHeader, "disease"));
  }

  @Test
  void resolveVersion_skipsInvalidEntries() {
    assertEquals(Optional.of("v7"), underTest.resolveVersion("badentry;disease:v7", "disease"));
  }
}

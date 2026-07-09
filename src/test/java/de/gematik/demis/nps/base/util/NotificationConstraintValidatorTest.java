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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import de.gematik.demis.notification.builder.demis.fhir.notification.types.NotificationCategory;
import de.gematik.demis.nps.error.NpsServiceException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class NotificationConstraintValidatorTest {

  static Stream<String> codes73() {
    return Stream.of("neg", "trp", "hiv", "ech", "tox", "cht");
  }

  static Stream<String> non73Codes() {
    return Stream.of("myt", "hev", "cvd");
  }

  static Stream<NotificationCategory> non73categories() {
    return Stream.of(
        NotificationCategory.P_6_1, NotificationCategory.P_7_1, NotificationCategory.P_7_4);
  }

  @ParameterizedTest
  @MethodSource("codes73")
  void is73Code_with73Code(final String category) {
    assertThat(NotificationConstraintValidator.is73Code(category)).isTrue();
  }

  @ParameterizedTest
  @MethodSource("non73Codes")
  void is73Code_withNon73Code(final String category) {
    assertThat(NotificationConstraintValidator.is73Code(category)).isFalse();
  }

  @ParameterizedTest
  @MethodSource("non73categories")
  void
      validate73ProcessingConstraints_73disabled_codeAndCategoryDoNotMatch_throwsUnsupportedOperationException(
          final NotificationCategory category) {
    assertThatThrownBy(
            () ->
                NotificationConstraintValidator.validate73ProcessingConstraints(
                    category, "hiv", false))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("7.3 notifications can't be processed due to disabled feature flag");
  }

  @Test
  void
      validate73ProcessingConstraints_73disabled_codeAndCategoryDoMatch_throwsUnsupportedOperationException() {
    assertThatThrownBy(
            () ->
                NotificationConstraintValidator.validate73ProcessingConstraints(
                    NotificationCategory.P_7_3, "hiv", false))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("7.3 notifications can't be processed due to disabled feature flag");
  }

  @ParameterizedTest
  @MethodSource("non73categories")
  void
      validate73ProcessingConstraints_73enabled_codeAndCategoryDoNotMatch_throwsNpsServiceException(
          final NotificationCategory category) {
    assertThatThrownBy(
            () ->
                NotificationConstraintValidator.validate73ProcessingConstraints(
                    category, "hiv", true))
        .isInstanceOf(NpsServiceException.class)
        .hasMessageContaining("Invalid Structure of §7.3 notification");
  }

  @Test
  void validate73ProcessingConstraints_73enabled_codeAndCategoryDoMatch() {
    assertDoesNotThrow(
        () ->
            NotificationConstraintValidator.validate73ProcessingConstraints(
                NotificationCategory.P_7_3, "hiv", true));
  }

  @ParameterizedTest
  @MethodSource("non73categories")
  void validate73ProcessingConstraints_73enabled_non73Code(final NotificationCategory category) {
    // method validates that 7.3 Codes are not allowed for non-7.3 categories, so this should not
    // throw an exception
    assertDoesNotThrow(
        () ->
            NotificationConstraintValidator.validate73ProcessingConstraints(category, "cvd", true));
  }
}

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

import static de.gematik.demis.notification.builder.demis.fhir.notification.types.NotificationCategory.P_7_3;

import de.gematik.demis.notification.builder.demis.fhir.notification.types.NotificationCategory;
import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.error.NpsServiceException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Utility class that validates cross-field constraints for notification category handling.
 *
 * <p>The current implementation enforces rules for section 7.3 notifications by checking the
 * combination of notification category, disease code root and feature flag state.
 */
public final class NotificationConstraintValidator {

  private static final Set<String> CODES_7_3 = Set.of("neg", "trp", "hiv", "ech", "tox", "cht");

  private NotificationConstraintValidator() {}

  /**
   * Validates whether a notification is allowed to be processed under 7.3 constraints.
   *
   * <p>Validation rules:
   *
   * <ul>
   *   <li>If 7.3 processing is disabled, notifications with category {@code P_7_3} or a 7.3 disease
   *       code root are rejected.
   *   <li>If the disease code root belongs to 7.3 but the notification category is not {@code
   *       P_7_3}, the notification is rejected as structurally invalid.
   * </ul>
   *
   * @param category notification category returned by routing
   * @param diseaseCodeRoot 3-digit disease code root extracted from the notification
   * @param isProcessing73Enabled feature flag that enables processing of section 7.3 notifications
   * @throws UnsupportedOperationException if 7.3 processing is disabled and notification matches
   *     7.3
   * @throws NpsServiceException if the notification has inconsistent 7.3 structure
   */
  public static void validate73ProcessingConstraints(
      @Nonnull final NotificationCategory category,
      @Nonnull final String diseaseCodeRoot,
      final boolean isProcessing73Enabled) {
    final boolean is73Category = Objects.equals(category, P_7_3);
    final boolean isDiseaseCodeRoot73 = is73Code(diseaseCodeRoot);

    if (!isProcessing73Enabled && (is73Category || isDiseaseCodeRoot73)) {
      throw new UnsupportedOperationException(
          "7.3 notifications can't be processed due to disabled feature flag");
    }

    if (!is73Category && isDiseaseCodeRoot73) {
      throw new NpsServiceException(
          ErrorCode.UNPROCESSABLE_ENTITY, "Invalid Structure of §7.3 notification");
    }
  }

  static boolean is73Code(final String diseaseCode) {
    return CODES_7_3.contains(diseaseCode.toLowerCase(Locale.ROOT));
  }
}

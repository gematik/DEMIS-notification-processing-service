package de.gematik.demis.nps.test;

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

import de.gematik.demis.notification.builder.demis.fhir.notification.types.NotificationCategory;
import de.gematik.demis.nps.base.util.SequencedSets;
import de.gematik.demis.nps.service.notification.NotificationType;
import de.gematik.demis.nps.service.routing.RoutingData;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

public class RoutingDataUtil {
  private RoutingDataUtil() {}

  /** Return an empty RoutingData model with the responsible data */
  @Nonnull
  public static RoutingData emptyFor(@Nonnull final String responsible) {
    return emptyFor(responsible, null);
  }

  @Nonnull
  public static RoutingData empty61For(@Nonnull final String responsible) {
    return new RoutingData(
        NotificationType.DISEASE,
        NotificationCategory.P_6_1,
        SequencedSets.of(),
        List.of(),
        Map.of(),
        responsible,
        Set.of(),
        null);
  }

  @Nonnull
  public static RoutingData emptyFor(
      @Nonnull final String responsible, @CheckForNull final String custodian) {
    return new RoutingData(
        NotificationType.LABORATORY,
        NotificationCategory.P_7_1,
        SequencedSets.of(),
        List.of(),
        Map.of(),
        responsible,
        Set.of(),
        custodian);
  }

  @Nonnull
  public static RoutingData laboratoryExample() {
    return new RoutingData(
        NotificationType.LABORATORY,
        NotificationCategory.P_7_1,
        SequencedSets.of(),
        List.of(),
        Map.of(),
        "1.",
        Set.of(),
        "custodian");
  }

  @Nonnull
  public static RoutingData diseaseExample() {
    return new RoutingData(
        NotificationType.DISEASE,
        NotificationCategory.P_6_1,
        SequencedSets.of(),
        List.of(),
        Map.of(),
        "1.",
        Set.of(),
        "custodian");
  }
}

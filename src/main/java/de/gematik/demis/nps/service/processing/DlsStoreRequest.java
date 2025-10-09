package de.gematik.demis.nps.service.processing;

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

import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.error.NpsServiceException;
import de.gematik.demis.nps.service.notification.Notification;
import java.time.Clock;
import java.time.Instant;
import javax.annotation.Nonnull;

/** Request payload to store notification metadata */
public record DlsStoreRequest(
    @Nonnull String notificationId,
    @Nonnull String responsibleDepartment,
    @Nonnull String notificationCategory,
    @Nonnull Instant lastUpdated) {

  /**
   * @see DlsStoreRequest#from(Notification, Clock)
   */
  @Nonnull
  public static DlsStoreRequest from(@Nonnull final Notification notification) {
    return from(notification, Clock.systemDefaultZone());
  }

  /**
   * @return a new instance using values from {@link Notification}.
   * @throws NpsServiceException for missing required notification id
   */
  @Nonnull
  public static DlsStoreRequest from(
      @Nonnull final Notification notification, @Nonnull final Clock clock) {
    // The notification id is required, given that we have validated the resource we expect the
    // information to be present. However, if it's actually missing something else went wrong.
    final String notificationId =
        notification
            .getCompositionIdentifier()
            .orElseThrow(
                () ->
                    new NpsServiceException(
                        ErrorCode.NRS_PROCESSING_ERROR, "Missing required NotificationID"));
    return new DlsStoreRequest(
        notificationId,
        notification.getRoutingData().responsible(),
        notification.getDiseaseCode(),
        clock.instant());
  }
}

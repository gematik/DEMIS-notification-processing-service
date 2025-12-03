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
 * For additional notes and disclaimer from gematik and in case of changes by gematik,
 * find details in the "Readme" file.
 * #L%
 */

import de.gematik.demis.nps.base.util.UUIDValidator;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.service.base.error.ServiceCallException;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Provides access to the DLS microservice */
@Slf4j
@Service
public class DlsService {
  private final DlsServiceClient client;
  private final boolean isFollowUpEnabled;

  public DlsService(
      final DlsServiceClient client,
      @Value("${feature.flag.follow.up.notification}") final boolean isFollowUpEnabled) {
    this.client = client;
    this.isFollowUpEnabled = isFollowUpEnabled;
  }

  /** Write the responsible receiver for the given notification id to the DLS. Fail silently. */
  public void store(@Nonnull final Notification original) {
    final String notificationId = original.getComposition().getIdentifier().getValue();
    if (!isFollowUpEnabled || !UUIDValidator.isValidUUID(notificationId)) {
      return;
    }

    final DlsStoreRequest payload = DlsStoreRequest.from(original);
    try {
      client.store(payload);
    } catch (final ServiceCallException e) {
      log.warn(
          "DLS rejected Notification metadata for notification id '{}'",
          payload.notificationId(),
          e);
    }
  }
}

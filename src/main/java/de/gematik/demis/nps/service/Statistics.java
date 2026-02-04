package de.gematik.demis.nps.service;

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

import de.gematik.demis.nps.base.util.NotificationLogger;
import de.gematik.demis.nps.base.util.RequestNotificationProperties;
import de.gematik.demis.service.base.error.rest.ErrorCounter;
import de.gematik.demis.service.base.error.rest.api.ErrorDTO;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class Statistics implements ErrorCounter {
  private final RequestNotificationProperties requestNotificationProperties;
  private static final String COUNTER_NOTIFICATION_SUCCESS = "notification_success";
  private static final String COUNTER_NOTIFICATION_ERROR = "notification_error";
  private static final String COUNTER_NOTIFICATION_IGNORED_ERROR = "notification_ignored_error";

  private static final String TAG_ERROR_CODE = "error_code";

  private final MeterRegistry meterRegistry;

  public void incSuccessCounter() {
    catchException(() -> meterRegistry.counter(COUNTER_NOTIFICATION_SUCCESS).increment());
  }

  @Override
  public void errorOccurred(final ErrorDTO error, @Nullable final String sender) {
    incErrorCounter();
    NotificationLogger.logUnsuccessfulNotification(
        requestNotificationProperties, error.errorCode());
  }

  public void incErrorCounter() {
    catchException(() -> meterRegistry.counter(COUNTER_NOTIFICATION_ERROR).increment());
  }

  public void incIgnoredErrorCounter(final String errorType) {
    catchException(
        () ->
            meterRegistry
                .counter(COUNTER_NOTIFICATION_IGNORED_ERROR, TAG_ERROR_CODE, nullsafe(errorType))
                .increment());
  }

  public void incNotificationEndpointCounter(
      final String apiVersion, final String submissionType, final String requestOrigin) {
    String counterName;
    final String version = Optional.ofNullable(apiVersion).orElse("legacy");
    if (version.equals("legacy")) {
      counterName = "nps_notifications_legacy_api";
    } else {
      counterName = "nps_notifications_" + requestOrigin + "_" + submissionType + "_api_" + version;
    }
    catchException(() -> meterRegistry.counter(counterName).increment());
  }

  private void catchException(final Runnable runnable) {
    try {
      runnable.run();
    } catch (final RuntimeException e) {
      log.error("error incrementing counter", e);
    }
  }

  private String nullsafe(final String s) {
    return s == null ? "n/a" : s;
  }
}

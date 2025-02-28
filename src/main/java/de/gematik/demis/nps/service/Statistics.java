package de.gematik.demis.nps.service;

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
 * #L%
 */

import de.gematik.demis.nps.service.notification.Notification;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class Statistics {
  private static final String COUNTER_NOTIFICATION_SUCCESS = "notification_success";
  private static final String COUNTER_NOTIFICATION_ERROR = "notification_error";
  private static final String COUNTER_NOTIFICATION_IGNORED_ERROR = "notification_ignored_error";

  private static final String TAG_USER = "user";
  private static final String TAG_TYPE = "type";
  private static final String TAG_ERROR_CODE = "error_code";
  private static final String TAG_DISEASE_CODE = "disease_code";

  private final MeterRegistry meterRegistry;

  public void incSuccessCounter(final Notification notification) {
    catchException(
        () ->
            meterRegistry
                .counter(
                    COUNTER_NOTIFICATION_SUCCESS,
                    TAG_TYPE,
                    notification.getType().name(),
                    TAG_USER,
                    nullsafe(notification.getSender()),
                    TAG_DISEASE_CODE,
                    nullsafe(notification.getDiseaseCode()))
                .increment());
  }

  public void incErrorCounter(final String user, final String errorCode) {
    catchException(
        () ->
            meterRegistry
                .counter(
                    COUNTER_NOTIFICATION_ERROR,
                    TAG_USER,
                    nullsafe(user),
                    TAG_ERROR_CODE,
                    nullsafe(errorCode))
                .increment());
  }

  public void incIgnoredErrorCounter(final String errorType) {
    catchException(
        () ->
            meterRegistry
                .counter(COUNTER_NOTIFICATION_IGNORED_ERROR, TAG_ERROR_CODE, nullsafe(errorType))
                .increment());
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

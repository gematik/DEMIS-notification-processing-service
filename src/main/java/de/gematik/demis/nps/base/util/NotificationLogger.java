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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j
public final class NotificationLogger {
  private NotificationLogger() {}

  private static final Logger LOG = LoggerFactory.getLogger("notification-logger");
  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .findAndRegisterModules()
          .setSerializationInclusion(JsonInclude.Include.NON_NULL);

  public static void logSuccessfulNotification(
      final RequestNotificationProperties requestNotificationProperties) {
    log(requestNotificationProperties, null);
  }

  public static void logUnsuccessfulNotification(
      final RequestNotificationProperties requestNotificationProperties, final String error) {
    log(requestNotificationProperties, error);
  }

  private static void log(
      final RequestNotificationProperties requestNotificationProperties, final String error) {
    final String status = error == null ? "SUCCESS" : "FAILURE";
    NotificationLogEntry e =
        new NotificationLogEntry(
            requestNotificationProperties.getNotificationId(),
            requestNotificationProperties.getSubmissionType(),
            requestNotificationProperties.getSubmissionCategory(),
            Optional.ofNullable(requestNotificationProperties.getApiVersion()).orElse(""),
            requestNotificationProperties.getSender(),
            status,
            error);

    try {
      String json = MAPPER.writeValueAsString(e);
      LOG.info(json);
    } catch (Exception ex) {
      LOG.error(
          "Failed to serialize success log for notificationId={}",
          requestNotificationProperties.getNotificationId(),
          ex);
    }
  }

  public record NotificationLogEntry(
      String notificationId,
      String submissionType,
      String submissionCategory,
      String apiVersion,
      String sender,
      String validationStatus,
      String error) {}
}

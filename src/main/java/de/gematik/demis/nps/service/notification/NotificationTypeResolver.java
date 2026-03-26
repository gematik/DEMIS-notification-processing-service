package de.gematik.demis.nps.service.notification;

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

import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.error.NpsServiceException;
import org.springframework.stereotype.Component;

/**
 * This component is responsible for determining the type of a notification based on the profile URL
 * specified in the Bundle's meta.profile field. It supports both JSON and XML representations of
 * FHIR Bundles. The resolution is done by prefix matching the profile URL against known
 * notification types.
 */
@Component
public class NotificationTypeResolver {

  static final String RESOURCE_TYPE_FIELD = "resourceType";
  static final String RESOURCE_FIELD = "resource";
  static final String META_FIELD = "meta";
  static final String PARAMETER_FIELD = "parameter";
  static final String PROFILE_FIELD = "profile";
  static final String PARAMETERS_VALUE = "Parameters";
  static final String BUNDLE_VALUE = "Bundle";

  private final ProfileReaderJson jsonReader;
  private final ProfileReaderXml xmlReader;

  public NotificationTypeResolver(ProfileReaderJson jsonReader, ProfileReaderXml xmlReader) {
    this.jsonReader = jsonReader;
    this.xmlReader = xmlReader;
  }

  public NotificationType resolveFromNotification(String notification, MessageType messageType) {
    if (messageType == null) {
      throw new NpsServiceException(
          ErrorCode.INVALID_REQUEST_PAYLOAD, "MessageType must not be null");
    }

    String profileUrl =
        switch (messageType) {
          case JSON -> jsonReader.readProfile(notification);
          case XML -> xmlReader.readProfile(notification);
        };

    return resolveFromProfileUrl(profileUrl);
  }

  /** Resolves the type from a profile URL using prefix matching. */
  private NotificationType resolveFromProfileUrl(String profileUrl) {
    if (profileUrl == null || profileUrl.isBlank()) {
      throw new NpsServiceException(
          ErrorCode.INVALID_REQUEST_PAYLOAD, "Bundle.meta.profile missing or empty.");
    }

    if (profileUrl.startsWith(NotificationType.LABORATORY.getBaseProfile().getUrl())) {
      return NotificationType.LABORATORY;
    }
    if (profileUrl.startsWith(NotificationType.DISEASE.getBaseProfile().getUrl())) {
      return NotificationType.DISEASE;
    }

    throw new NpsServiceException(
        ErrorCode.UNSUPPORTED_PROFILE, "Unsupported notification bundle profile: " + profileUrl);
  }
}

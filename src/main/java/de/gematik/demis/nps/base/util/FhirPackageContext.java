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

import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.nps.config.FeatureFlagsConfigProperties;
import de.gematik.demis.nps.config.NpsHeaders;
import de.gematik.demis.nps.service.notification.NotificationType;
import de.gematik.demis.nps.service.notification.NotificationTypeResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * This class holds the value of the {@link NpsHeaders#HEADER_FHIR_PACKAGE } to be used for outgoing
 * requests during the processing of a notification. It is initialized once per request and can be
 * accessed throughout the processing of the notification. The value is determined based on the
 * header value in the incoming request or derived from the notification type if the header is not
 * present in the incoming request.
 */
@Component
@RequestScope
@RequiredArgsConstructor
public class FhirPackageContext {
  private final HttpServletRequest request;
  private final NotificationTypeResolver notificationTypeResolver;
  private final FeatureFlagsConfigProperties featureFlagsConfigProperties;

  // The naming derived from the full package name
  // i.e. rki.demis.disease -> "disease", rki.demis.laboratory -> "laboratory"
  static final String DISEASE_PACKAGE = "disease";
  static final String LABORATORY_PACKAGE = "laboratory";
  static final String LEGACY_CORE_PACKAGE = "fhir-profile-snapshots";

  static final String FEATURE_FLAG_FHIR_CORE_SPLIT = "fhir_core_split";

  // Holds the value of the x-fhir-package header to be used for outgoing requests.
  private String outgoingFhirPackageHeaderValue;

  public void initialize(String fhirNotification, MessageType messageType) {

    if (StringUtils.isNotBlank(this.outgoingFhirPackageHeaderValue)) {
      // already initialized, do nothing
      return;
    }

    var incomingFhirPackageHeader = request.getHeader(NpsHeaders.HEADER_FHIR_PACKAGE);

    if (StringUtils.isNotBlank(incomingFhirPackageHeader)) {
      this.outgoingFhirPackageHeaderValue = incomingFhirPackageHeader;
      return;
    }

    if (!featureFlagsConfigProperties.isEnabled(FEATURE_FLAG_FHIR_CORE_SPLIT)) {
      this.outgoingFhirPackageHeaderValue = LEGACY_CORE_PACKAGE;
      return;
    }

    var notificationType =
        notificationTypeResolver.resolveFromNotification(fhirNotification, messageType);
    this.outgoingFhirPackageHeaderValue = deriveFhirPackageFromSubmissionType(notificationType);
  }

  public String getOutgoingFhirPackageHeaderValue() {
    if (StringUtils.isBlank(this.outgoingFhirPackageHeaderValue)) {
      throw new IllegalStateException(
          "FhirProfileContext has not been initialized for the current request.");
    }
    return this.outgoingFhirPackageHeaderValue;
  }

  private String deriveFhirPackageFromSubmissionType(NotificationType notificationType) {
    return switch (notificationType) {
      case DISEASE -> DISEASE_PACKAGE;
      case LABORATORY -> LABORATORY_PACKAGE;
    };
  }
}

package de.gematik.demis.nps.service.notification;

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

import de.gematik.demis.notification.builder.demis.fhir.notification.utils.DemisConstants;
import de.gematik.demis.nps.base.fhir.BundleQueries;
import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.error.NpsServiceException;
import de.gematik.demis.nps.service.codemapping.CodeMappingService;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationFhirService {

  private static final Set<String> ALLOWED_PRECHECK_PROFILES =
      Set.of(
          NotificationType.DISEASE.getProfile().getUrl(),
          NotificationType.LABORATORY.getProfile().getUrl(),
          DemisConstants.PROFILE_NOTIFICATION_BUNDLE_LABORATORY_NEGATIVE,
          DemisConstants.PROFILE_NOTIFICATION_BUNDLE_LABORATORY_ANONYMOUS,
          DemisConstants.PROFILE_NOTIFICATION_BUNDLE_LABORATORY_NON_NOMINAL);

  private static final Supplier<NpsServiceException> CANT_PARSE_DISEASE_CODE_ERROR =
      () ->
          new NpsServiceException(
              ErrorCode.NRS_PROCESSING_ERROR, "Can't parse disease code from notification");

  private final NotificationCleaning cleaner;
  private final NotificationEnrichment enricher;
  private final CodeMappingService codeMappingService;
  private static final Pattern pattern =
      Pattern.compile(
          "(?s)meta.*profile.*(" + String.join("|", ALLOWED_PRECHECK_PROFILES) + ")(?!\\w|/)");

  public void preCheckProfile(final String fhirNotification) {
    Matcher matcher = pattern.matcher(fhirNotification);
    if (!matcher.find()) {
      throw new NpsServiceException(
          ErrorCode.UNSUPPORTED_PROFILE, "bundle profile not supported (pre-check).");
    }
  }

  public void cleanAndEnrichNotification(final Notification notification, final String requestId) {
    // Remove unwanted information and data
    cleaner.cleanNotification(notification.getBundle());

    // Add timestamp, identifier and sender
    enricher.enrichNotification(notification, requestId);
  }

  @Nonnull
  public String getDiseaseCode(@Nonnull final Bundle notification, final NotificationType type) {
    return switch (type) {
      case LABORATORY -> getDiseaseCodeFromLaboratoryNotification(notification);
      case DISEASE -> getDiseaseCodeFromDiseaseNotification(notification);
    };
  }

  @Nonnull
  private String getDiseaseCodeFromLaboratoryNotification(@Nonnull final Bundle bundle) {
    return BundleQueries.findFirstResource(bundle, DiagnosticReport.class)
        .map(DiagnosticReport::getCode)
        .map(CodeableConcept::getCodingFirstRep)
        .map(Coding::getCode)
        .map(codeMappingService::getMappedPathogenCode)
        .orElseThrow(CANT_PARSE_DISEASE_CODE_ERROR);
  }

  @Nonnull
  private String getDiseaseCodeFromDiseaseNotification(@Nonnull final Bundle bundle) {
    return BundleQueries.findFirstResource(bundle, Condition.class)
        .map(Condition::getCode)
        .map(CodeableConcept::getCodingFirstRep)
        .map(Coding::getCode)
        .map(codeMappingService::getMappedDiseaseCode)
        .orElseThrow(CANT_PARSE_DISEASE_CODE_ERROR);
  }
}

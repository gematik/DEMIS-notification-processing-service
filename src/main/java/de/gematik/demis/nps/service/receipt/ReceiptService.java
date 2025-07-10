package de.gematik.demis.nps.service.receipt;

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
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence is distributed on an "AS IS" basis,
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

import de.gematik.demis.fhirparserlibrary.FhirParser;
import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.service.Statistics;
import de.gematik.demis.nps.service.healthoffice.HealthOfficeMasterDataService;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.service.receipt.ReceiptBundleCreator.ReceiptBundleBuilder;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Organization;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for generating receipts based on notifications. Handles the creation of receipt bundles
 * and PDFs, and manages errors gracefully.
 */
@Service
@Slf4j
public class ReceiptService {

  private final ReceiptBundleCreator receiptBundleCreator;
  private final PdfGenServiceClient pdfGenServiceClient;
  private final HealthOfficeMasterDataService healthOfficeMasterDataService;
  private final FhirParser fhirParser;
  private final Statistics statistics;
  private final boolean isNotification73enabled;

  /**
   * Constructs a ReceiptService instance.
   *
   * @param receiptBundleCreator the creator for receipt bundles
   * @param pdfGenServiceClient the client for generating PDFs
   * @param healthOfficeMasterDataService the service for retrieving health office data
   * @param fhirParser the parser for FHIR resources
   * @param statistics the statistics service for tracking errors
   * @param isNotification73enabled flag indicating if Notification 7.3 is enabled
   */
  public ReceiptService(
      ReceiptBundleCreator receiptBundleCreator,
      PdfGenServiceClient pdfGenServiceClient,
      HealthOfficeMasterDataService healthOfficeMasterDataService,
      FhirParser fhirParser,
      Statistics statistics,
      @Value("${feature.flag.notifications.7_3}") boolean isNotification73enabled) {
    this.receiptBundleCreator = receiptBundleCreator;
    this.pdfGenServiceClient = pdfGenServiceClient;
    this.healthOfficeMasterDataService = healthOfficeMasterDataService;
    this.fhirParser = fhirParser;
    this.statistics = statistics;
    this.isNotification73enabled = isNotification73enabled;
  }

  /**
   * Generates a receipt bundle for the given notification. Includes health office information and a
   * PDF if possible.
   *
   * @param notification the notification to process
   * @return the generated receipt bundle
   */
  public Bundle generateReceipt(final Notification notification) {
    final ReceiptBundleBuilder receiptBuilder =
        receiptBundleCreator.builder().addNotificationId(notification.getBundle().getIdentifier());

    final String responsibleHealthOfficeId =
        notification.getResponsibleHealthOfficeId().orElseThrow();
    final Organization healthOfficeOrganization =
        healthOfficeMasterDataService.getHealthOfficeOrganization(
            responsibleHealthOfficeId, notification.isTestUser());
    if (healthOfficeOrganization != null) {
      receiptBuilder.addResponsibleHealthOffice(healthOfficeOrganization);
    } else {
      log.warn("No Organization info for health office {}", responsibleHealthOfficeId);
    }
    notification.getCompositionIdentifier().ifPresent(receiptBuilder::addRelatesNotificationId);

    try {
      final byte[] pdfBytes = generatePdf(notification);
      receiptBuilder.addPdf(pdfBytes);
    } catch (final RuntimeException e) {
      log.error("error creating pdf", e);
      // do not abort processing
      statistics.incIgnoredErrorCounter(ErrorCode.NO_PDF.getCode());
    }

    return receiptBuilder.build();
  }

  /**
   * Generates a PDF for the given notification. Handles different notification types and routing
   * logic.
   *
   * @param notification the notification to process
   * @return the generated PDF as a byte array
   * @throws IllegalStateException if no responsible health office is found
   */
  private byte[] generatePdf(final Notification notification) {
    String bundleAsJson;
    if (isNotification73enabled) {
      String id;
      if (notification.isTestUser()) {
        id = notification.getTestUserRecipient();
      } else {
        id =
            notification
                .getResponsibleHealthOfficeId()
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "no responsilbe health office found through routing"));
      }
      bundleAsJson = fhirParser.encodeToJson((notification.getPreEncryptedBundles().get(id)));

    } else {
      bundleAsJson = fhirParser.encodeToJson(notification.getBundle());
    }
    return switch (notification.getType()) {
      case DISEASE -> pdfGenServiceClient.createDiseasePdfFromJson(bundleAsJson);
      case LABORATORY -> pdfGenServiceClient.createLaboratoryPdfFromJson(bundleAsJson);
    };
  }
}

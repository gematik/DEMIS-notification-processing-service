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
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
 * In case of changes by gematik find details in the "Readme" file.
 *
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 * #L%
 */

import de.gematik.demis.fhirparserlibrary.FhirParser;
import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.service.Statistics;
import de.gematik.demis.nps.service.healthoffice.HealthOfficeMasterDataService;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.service.receipt.ReceiptBundleCreator.ReceiptBundleBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Organization;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptService {

  private final ReceiptBundleCreator receiptBundleCreator;
  private final PdfGenServiceClient pdfGenServiceClient;
  private final HealthOfficeMasterDataService healthOfficeMasterDataService;
  private final FhirParser fhirParser;
  private final Statistics statistics;

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

  private byte[] generatePdf(final Notification notification) {
    final String bundleAsJson = fhirParser.encodeToJson(notification.getBundle());

    return switch (notification.getType()) {
      case DISEASE -> pdfGenServiceClient.createDiseasePdfFromJson(bundleAsJson);
      case LABORATORY -> pdfGenServiceClient.createLaboratoryPdfFromJson(bundleAsJson);
    };
  }
}

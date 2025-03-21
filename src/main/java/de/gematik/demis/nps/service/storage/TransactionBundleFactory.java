package de.gematik.demis.nps.service.storage;

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

import java.util.Collection;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Service;

/** Creates transaction bundle for single resources. */
@Service
class TransactionBundleFactory {

  public static final String BINARY = "Binary";
  public static final String BUNDLE = "Bundle";

  /**
   * Creates a transaction bundle, adding the resources as parameters
   *
   * @param encryptedBinaryNotification the full encrypted notification for the health office
   *     (Gesundheitsamt)
   * @param encryptedSubsidiaryNotification the full encrypted notification for the health office
   *     subsidiary (Gesundheitsamt Nebenstelle)
   * @param anonymizedNotification anonymized notification
   * @return transaction bundle, wrapping {@code encryptedBinaryNotification}, {@code
   *     encryptedSubsidiaryNotification} and {@code anonymizedNotification}.
   */
  @Deprecated
  public Bundle createTransactionBundle(
      final Binary encryptedBinaryNotification,
      final Binary encryptedSubsidiaryNotification,
      final Bundle anonymizedNotification) {
    final Bundle transactionBundle = new Bundle();
    transactionBundle.setType(Bundle.BundleType.TRANSACTION);
    addEntry(transactionBundle, anonymizedNotification, BUNDLE);
    addEntry(transactionBundle, encryptedBinaryNotification, BINARY);
    addEntry(transactionBundle, encryptedSubsidiaryNotification, BINARY);
    return transactionBundle;
  }

  /**
   * creates a transaction bundle, adding the resources as parameters
   *
   * @param notificationsToForward
   * @return transaction bundle containing all notifications that are to be forwarded
   */
  public Bundle createTransactionBundle(
      final Collection<? extends IBaseResource> notificationsToForward) {
    final Bundle transactionBundle = new Bundle();
    transactionBundle.setType(Bundle.BundleType.TRANSACTION);

    for (final IBaseResource notification : notificationsToForward) {
      if (notification instanceof Bundle bundle) {
        addEntry(transactionBundle, bundle, BUNDLE);
      } else if (notification instanceof Binary binary) {
        addEntry(transactionBundle, binary, BINARY);
      }
    }

    return transactionBundle;
  }

  private void addEntry(final Bundle transactionBundle, final Resource resource, final String url) {
    if (resource != null) {
      transactionBundle
          .addEntry()
          .setFullUrl(IdType.newRandomUuid().getValue())
          .setResource(resource)
          .getRequest()
          .setUrl(url)
          .setMethod(Bundle.HTTPVerb.POST);
    }
  }
}

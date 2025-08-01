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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import de.gematik.demis.nps.error.ServiceCallErrorCode;
import de.gematik.demis.service.base.feign.annotations.ErrorCode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "fhir-storage-writer", url = "${nps.client.fhir-storage-writer.address}")
interface FhirStorageWriterClient {
  @PostMapping(
      value = "${nps.client.fhir-storage-writer.context-path}",
      consumes = "application/fhir+json",
      produces = "application/fhir+json")
  @ErrorCode(ServiceCallErrorCode.FSW)
  void sendNotificationToFhirStorageWriter(String bundleAsJson);
}

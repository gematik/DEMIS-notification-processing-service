package de.gematik.demis.nps.service.pseudonymization;

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

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import de.gematik.demis.nps.error.ServiceCallErrorCode;
import de.gematik.demis.service.base.feign.annotations.ErrorCode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "pseudo", url = "${nps.client.pseudonymization}")
interface PseudonymizationServiceClient {
  @PostMapping(
      path = "/pseudonymization",
      consumes = APPLICATION_JSON_VALUE,
      produces = APPLICATION_JSON_VALUE)
  @ErrorCode(ServiceCallErrorCode.PS)
  PseudonymizationResponse generatePseudonym(PseudonymizationRequest content);
}

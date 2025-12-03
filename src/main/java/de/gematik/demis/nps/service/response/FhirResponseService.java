package de.gematik.demis.nps.service.response;

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
 * For additional notes and disclaimer from gematik and in case of changes by gematik,
 * find details in the "Readme" file.
 * #L%
 */

import de.gematik.demis.nps.base.profile.DemisProfiles;
import de.gematik.demis.service.base.fhir.outcome.FhirOperationOutcomeService;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FhirResponseService {
  private static final String BUNDLE_PARAMETER_NAME = "bundle";
  private static final String OPERATION_OUTCOME_PARAMETER_NAME = "operationOutcome";

  private final FhirOperationOutcomeService operationOutcomeService;

  public Parameters success(final Bundle bundle, final OperationOutcome outcome) {
    operationOutcomeService.processOutcome(outcome);
    outcome.getIssue().addFirst(operationOutcomeService.allOk());

    final Parameters resultParams = new Parameters();
    DemisProfiles.PROCESS_NOTIFICATION_RESPONSE_PARAMETER.applyTo(resultParams);
    resultParams.addParameter().setName(BUNDLE_PARAMETER_NAME).setResource(bundle);
    resultParams.addParameter().setName(OPERATION_OUTCOME_PARAMETER_NAME).setResource(outcome);
    return resultParams;
  }
}

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
 * #L%
 */

import static java.util.Objects.requireNonNullElseGet;

import de.gematik.demis.nps.base.profile.DemisProfiles;
import de.gematik.demis.nps.config.NpsConfigProperties;
import de.gematik.demis.service.base.error.rest.api.ErrorDTO;
import jakarta.annotation.Nullable;
import java.util.Comparator;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.springframework.stereotype.Service;

@Service
public class FhirResponseService {
  private static final String BUNDLE_PARAMETER_NAME = "bundle";
  private static final String OPERATION_OUTCOME_PARAMETER_NAME = "operationOutcome";

  private final SeverityComparator severityComparator = new SeverityComparator();
  private final IssueSeverity outcomeIssueThreshold;

  public FhirResponseService(final NpsConfigProperties configProperties) {
    outcomeIssueThreshold = configProperties.outcomeIssueThreshold();
  }

  public OperationOutcome error(
      final ErrorDTO errorDTO, @Nullable final OperationOutcome operationOutcome) {
    final OperationOutcome result = requireNonNullElseGet(operationOutcome, OperationOutcome::new);

    result
        .addIssue()
        .setSeverity(IssueSeverity.ERROR)
        .setCode(errorDTO.status() >= 500 ? IssueType.EXCEPTION : IssueType.PROCESSING)
        .setDiagnostics(errorDTO.detail())
        .setDetails(new CodeableConcept().addCoding(new Coding().setCode(errorDTO.errorCode())))
        .addLocation(errorDTO.id());

    processOutcome(result);
    return result;
  }

  public Parameters success(final Bundle bundle, final OperationOutcome outcome) {
    processOutcome(outcome);
    outcome.getIssue().add(0, allOk());

    final Parameters resultParams = new Parameters();
    DemisProfiles.PROCESS_NOTIFICATION_RESPONSE_PARAMETER.applyTo(resultParams);
    resultParams.addParameter().setName(BUNDLE_PARAMETER_NAME).setResource(bundle);
    resultParams.addParameter().setName(OPERATION_OUTCOME_PARAMETER_NAME).setResource(outcome);
    return resultParams;
  }

  private void processOutcome(final OperationOutcome outcome) {
    DemisProfiles.PROCESS_NOTIFICATION_RESPONSE_PROFILE.applyTo(outcome);
    outcome.setText(createOutcomeText());
    filterOutcomeIssues(outcome);
    orderOutcomeIssues(outcome);
  }

  private void filterOutcomeIssues(final OperationOutcome outcome) {
    outcome
        .getIssue()
        .removeIf(
            issue -> severityComparator.compare(issue.getSeverity(), outcomeIssueThreshold) < 0);
  }

  private void orderOutcomeIssues(final OperationOutcome outcome) {
    outcome
        .getIssue()
        .sort(
            Comparator.comparing(
                OperationOutcomeIssueComponent::getSeverity, severityComparator.reversed()));
  }

  private OperationOutcomeIssueComponent allOk() {
    return new OperationOutcomeIssueComponent()
        .setSeverity(IssueSeverity.INFORMATION)
        .setCode(IssueType.INFORMATIONAL)
        .setDetails(new CodeableConcept().setText("All OK"));
  }

  // TODO welchen fachlichen Sinn hat das div
  private Narrative createOutcomeText() {
    var div = new XhtmlNode(NodeType.Element, "div");
    div.addText("");
    return new Narrative().setStatus(Narrative.NarrativeStatus.GENERATED).setDiv(div);
  }
}

package de.gematik.demis.nps.service.response;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.gematik.demis.nps.test.TestUtil;
import de.gematik.demis.service.base.fhir.FhirSupportAutoConfiguration;
import de.gematik.demis.service.base.fhir.outcome.FhirOperationOutcomeProperties;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(classes = FhirResponseService.class)
@ImportAutoConfiguration(FhirSupportAutoConfiguration.class)
class FhirResponseServiceTest {

  private static final String EXPECTED_SUCCESS_RESPONSE =
"""
{
  "resourceType" : "Parameters",
  "meta" : {
    "profile" : [ "https://demis.rki.de/fhir/StructureDefinition/ProcessNotificationResponseParameters" ]
  },
  "parameter" : [ {
    "name" : "bundle",
    "resource" : {
      "resourceType" : "Bundle",
      "id" : "Bundle for testing"
    }
  }, {
    "name" : "operationOutcome",
    "resource" : {
      "resourceType" : "OperationOutcome",
      "meta" : {
        "profile" : [ "https://demis.rki.de/fhir/StructureDefinition/ProcessNotificationResponse" ]
      },
      "text" : {
        "status" : "generated",
        "div" : "<div xmlns=\\"http://www.w3.org/1999/xhtml\\"></div>"
      },
      "issue" : [ {
        "severity" : "information",
        "code" : "informational",
        "details" : {
          "text" : "All OK"
        }
      }, {
        "severity" : "warning",
        "code" : "processing",
        "diagnostics" : "Just for testing"
      } ]
    }
  } ]
}
""";

  @Autowired private FhirResponseService underTest;
  @MockitoSpyBean FhirOperationOutcomeProperties operationOutcomeProperties;

  @Test
  void responseForSuccess() {
    final Parameters result = executeSuccessResponse();

    assertThat(result).isNotNull();
    assertThat(TestUtil.fhirResourceToJson(result))
        .isEqualToIgnoringWhitespace(EXPECTED_SUCCESS_RESPONSE);
  }

  @Test
  void responseForSuccess_IssueThreshold_Error() {
    when(operationOutcomeProperties.issueThreshold()).thenReturn(IssueSeverity.ERROR);
    final Parameters result = executeSuccessResponse();

    assertThat(result).isNotNull();
    final OperationOutcome operationOutcome =
        (OperationOutcome) result.getParameter("operationOutcome").getResource();
    assertThat(operationOutcome.getIssue())
        .isNotNull()
        .hasSize(1)
        .allMatch(issue -> issue.getSeverity() != IssueSeverity.WARNING);
  }

  private Parameters executeSuccessResponse() {
    final var bundle = new Bundle();
    bundle.setId("Bundle for testing");

    final var outcome = new OperationOutcome();
    outcome
        .addIssue()
        .setSeverity(IssueSeverity.WARNING)
        .setCode(IssueType.PROCESSING)
        .setDiagnostics("Just for testing");

    return underTest.success(bundle, outcome);
  }
}

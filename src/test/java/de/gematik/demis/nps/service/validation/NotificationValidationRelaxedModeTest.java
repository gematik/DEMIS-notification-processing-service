package de.gematik.demis.nps.service.validation;

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

import static de.gematik.demis.nps.test.TestUtil.fhirResourceToJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ca.uhn.fhir.context.FhirContext;
import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.nps.config.NpsConfigProperties;
import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.error.NpsServiceException;
import feign.Response;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationValidationRelaxedModeTest {

  private static final String ORIGINAL_NOTIFICATION =
      """
<Bundle xmlns="http://hl7.org/fhir">
    <id value="098f6bcd-4621-3373-8ade-4e832627b4f6" />
    <id value="9aaaaaaa-4621-3373-8ade-bbbbbbbbbbbb" />
</Bundle>
""";

  private static final String CORRECTED_NOTIFICATION =
      """
{"resourceType":"Bundle","id":"098f6bcd-4621-3373-8ade-4e832627b4f6"}""";

  private static final FhirContext fhirContext = FhirContext.forR4Cached();

  @Mock NpsConfigProperties config;
  @Mock ValidationServiceClient validationServiceClient;
  @Mock LifecycleValidationServiceClient lifecycleValidationServiceClient;

  private NotificationValidator underTest;

  private static Response mockResponse(final int status, final String content) throws IOException {
    final Response response = Mockito.mock(Response.class);
    Mockito.when(response.status()).thenReturn(status);
    if (content != null) {
      final Response.Body body = Mockito.mock(Response.Body.class);
      Mockito.when(body.asReader(StandardCharsets.UTF_8)).thenReturn(new StringReader(content));
      Mockito.when(response.body()).thenReturn(body);
    }
    return response;
  }

  private static OperationOutcome createOperationOutcomeOfValidationService() {
    final OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue().setSeverity(IssueSeverity.INFORMATION);
    outcome.addIssue().setSeverity(IssueSeverity.ERROR);
    outcome.addIssue().setSeverity(IssueSeverity.FATAL);
    return outcome;
  }

  @BeforeEach
  void setup() {
    underTest =
        new NotificationValidator(
            validationServiceClient, lifecycleValidationServiceClient, fhirContext, config);
    Mockito.when(config.relaxedValidation()).thenReturn(true);
  }

  @Test
  void parsedFhirNotificationIsValid() throws Exception {
    final var outcome = createOperationOutcomeOfValidationService();
    final var firstResponse = mockResponse(422, fhirResourceToJson(outcome));
    Mockito.when(validationServiceClient.validateXmlBundle(ORIGINAL_NOTIFICATION))
        .thenReturn(firstResponse);

    final var secondTryResponse = mockResponse(200, null);
    Mockito.when(validationServiceClient.validateJsonBundle(CORRECTED_NOTIFICATION))
        .thenReturn(secondTryResponse);

    final OperationOutcome result = underTest.validateFhir(ORIGINAL_NOTIFICATION, MessageType.XML);
    assertThat(result).isNotNull();
    assertThat(result.getIssue())
        .extracting(OperationOutcomeIssueComponent::getSeverity)
        .containsExactly(IssueSeverity.INFORMATION, IssueSeverity.WARNING, IssueSeverity.WARNING);
  }

  @Test
  void parsedFhirNotificationIsStillInvalid() throws Exception {
    final var outcome = createOperationOutcomeOfValidationService();
    final var firstResponse = mockResponse(422, fhirResourceToJson(outcome));
    Mockito.when(validationServiceClient.validateXmlBundle(ORIGINAL_NOTIFICATION))
        .thenReturn(firstResponse);

    final var secondTryResponse = mockResponse(422, null);
    Mockito.when(validationServiceClient.validateJsonBundle(CORRECTED_NOTIFICATION))
        .thenReturn(secondTryResponse);

    final var exception =
        catchThrowableOfType(
            () -> underTest.validateFhir(ORIGINAL_NOTIFICATION, MessageType.XML),
            NpsServiceException.class);

    assertThat(exception)
        .isNotNull()
        .returns(ErrorCode.FHIR_VALIDATION_FATAL.name(), NpsServiceException::getErrorCode);
    assertThat(exception.getOperationOutcome()).isNotNull();
    assertThat(exception.getOperationOutcome().getIssue())
        .extracting(OperationOutcomeIssueComponent::getSeverity)
        .containsExactly(IssueSeverity.INFORMATION, IssueSeverity.ERROR, IssueSeverity.FATAL);
  }

  @Test
  void fhirNotificationNotParseable() throws Exception {
    final String notParseableNotification =
        """
<Bundle xmlns="http://hl7.org/fhir">
    <id value="098f6bcd-4621-3373-8ade-4e832627b4f6" />
    <syntax error
</Bundle>
""";
    final var outcome = createOperationOutcomeOfValidationService();
    final var firstResponse = mockResponse(422, fhirResourceToJson(outcome));
    Mockito.when(validationServiceClient.validateXmlBundle(notParseableNotification))
        .thenReturn(firstResponse);
    // Note: No second try (that's the difference to parsedFhirNotificationIsStillInvalid)

    final var exception =
        catchThrowableOfType(
            () -> underTest.validateFhir(notParseableNotification, MessageType.XML),
            NpsServiceException.class);

    assertThat(exception)
        .isNotNull()
        .returns(ErrorCode.FHIR_VALIDATION_FATAL.name(), NpsServiceException::getErrorCode);
    assertThat(exception.getOperationOutcome()).isNotNull();
    assertThat(exception.getOperationOutcome().getIssue())
        .extracting(OperationOutcomeIssueComponent::getSeverity)
        .containsExactly(IssueSeverity.INFORMATION, IssueSeverity.ERROR, IssueSeverity.FATAL);
  }
}

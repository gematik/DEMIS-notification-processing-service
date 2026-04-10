package de.gematik.demis.nps.service.validation;

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

import static de.gematik.demis.nps.config.NpsHeaders.HEADER_FHIR_API_VERSION;
import static de.gematik.demis.nps.config.NpsHeaders.HEADER_FHIR_PROFILE;
import static de.gematik.demis.nps.config.NpsHeaders.HEADER_SENDER;
import static de.gematik.demis.nps.test.TestUtil.fhirResourceToJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.nps.base.util.RequestProcessorState;
import de.gematik.demis.nps.config.FeatureFlagsConfigProperties;
import de.gematik.demis.nps.config.NpsConfigProperties;
import feign.Response;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class NotificationValidatorRelaxedLoggingTest {

  private static final String SENDER_ID = "test-sender-user-123";
  private static final String RELAXED_VALIDATION_LOG_MESSAGE =
      "Relaxed validation was used successfully for sender:";

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

  @Mock ValidationServiceClient validationServiceClient;
  @Mock HttpServletRequest httpServletRequest;

  private NotificationValidator underTest;
  private ListAppender<ILoggingEvent> logAppender;

  private static Response mockResponse(final int status, final String content) throws IOException {
    final Response response = mock(Response.class);
    when(response.status()).thenReturn(status);
    if (content != null) {
      final Response.Body body = mock(Response.Body.class);
      when(body.asReader(StandardCharsets.UTF_8)).thenReturn(new StringReader(content));
      when(response.body()).thenReturn(body);
    }
    return response;
  }

  private static OperationOutcome createOperationOutcomeWithErrors() {
    final OperationOutcome outcome = new OperationOutcome();
    outcome.addIssue().setSeverity(IssueSeverity.INFORMATION);
    outcome.addIssue().setSeverity(IssueSeverity.ERROR);
    outcome.addIssue().setSeverity(IssueSeverity.FATAL);
    return outcome;
  }

  @BeforeEach
  void setupLogCapture() {
    final Logger logger = (Logger) LoggerFactory.getLogger(NotificationValidator.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    logger.addAppender(logAppender);
  }

  @AfterEach
  void tearDownLogCapture() {
    final Logger logger = (Logger) LoggerFactory.getLogger(NotificationValidator.class);
    logger.detachAppender(logAppender);
  }

  @Nested
  @DisplayName("logRelaxedValidationUsage - unit tests for isolated method")
  class LogRelaxedValidationUsageTest {

    @Test
    @DisplayName("should log sender at INFO level when config flag is enabled")
    void shouldLogSenderWhenFlagEnabled() {
      underTest = createValidator(true, true);
      when(httpServletRequest.getHeader(HEADER_SENDER)).thenReturn(SENDER_ID);

      underTest.logRelaxedValidationUsage();

      assertThat(logAppender.list)
          .hasSize(1)
          .first()
          .satisfies(
              event -> {
                assertThat(event.getLevel()).hasToString("INFO");
                assertThat(event.getFormattedMessage())
                    .contains(RELAXED_VALIDATION_LOG_MESSAGE)
                    .contains(SENDER_ID);
              });
    }

    @Test
    @DisplayName("should not log sender when config flag is disabled")
    void shouldNotLogSenderWhenFlagDisabled() {
      underTest = createValidator(true, false);

      underTest.logRelaxedValidationUsage();

      assertThat(logAppender.list).isEmpty();
    }

    @Test
    @DisplayName("should handle null sender header gracefully")
    void shouldHandleNullSenderGracefully() {
      underTest = createValidator(true, true);
      when(httpServletRequest.getHeader(HEADER_SENDER)).thenReturn(null);

      underTest.logRelaxedValidationUsage();

      assertThat(logAppender.list)
          .hasSize(1)
          .first()
          .satisfies(
              event -> {
                assertThat(event.getLevel()).hasToString("INFO");
                assertThat(event.getFormattedMessage()).contains(RELAXED_VALIDATION_LOG_MESSAGE);
              });
    }
  }

  @Nested
  @DisplayName("validateFhir - integration with relaxed validation logging")
  class ValidateFhirRelaxedLoggingTest {

    @Test
    @DisplayName("should log sender when relaxed validation succeeds and config flag is enabled")
    void shouldLogSenderOnSuccessfulRelaxedValidation() throws IOException {
      underTest = createValidator(true, true);
      when(httpServletRequest.getHeader(HEADER_SENDER)).thenReturn(SENDER_ID);

      final var outcome = createOperationOutcomeWithErrors();
      final var firstResponse = mockResponse(422, fhirResourceToJson(outcome));
      when(validationServiceClient.validateXmlBundle(ORIGINAL_NOTIFICATION))
          .thenReturn(firstResponse);

      final var secondTryResponse = mockResponse(200, null);
      when(validationServiceClient.validateJsonBundle(CORRECTED_NOTIFICATION))
          .thenReturn(secondTryResponse);

      underTest.validateFhir(ORIGINAL_NOTIFICATION, MessageType.XML);

      assertThat(logAppender.list)
          .anyMatch(
              event ->
                  event.getLevel().toString().equals("INFO")
                      && event.getFormattedMessage().contains(SENDER_ID)
                      && event.getFormattedMessage().contains(RELAXED_VALIDATION_LOG_MESSAGE));
    }

    @Test
    @DisplayName(
        "should not log sender when relaxed validation succeeds but config flag is disabled")
    void shouldNotLogSenderWhenLoggingFlagDisabled() throws IOException {
      underTest = createValidator(true, false);

      final var outcome = createOperationOutcomeWithErrors();
      final var firstResponse = mockResponse(422, fhirResourceToJson(outcome));
      when(validationServiceClient.validateXmlBundle(ORIGINAL_NOTIFICATION))
          .thenReturn(firstResponse);

      final var secondTryResponse = mockResponse(200, null);
      when(validationServiceClient.validateJsonBundle(CORRECTED_NOTIFICATION))
          .thenReturn(secondTryResponse);

      underTest.validateFhir(ORIGINAL_NOTIFICATION, MessageType.XML);

      assertThat(logAppender.list)
          .noneMatch(event -> event.getFormattedMessage().contains(RELAXED_VALIDATION_LOG_MESSAGE));
    }
  }

  private NotificationValidator createValidator(
      boolean relaxedValidation, boolean loggingRelaxedValidation) {
    final FeatureFlagsConfigProperties featureFlags =
        new FeatureFlagsConfigProperties(
            Map.of("relaxed_validation", relaxedValidation, "feign_interceptor_enabled", true));
    final NpsConfigProperties npsConfigProperties = mock(NpsConfigProperties.class);
    when(npsConfigProperties.loggingRelaxedValidation()).thenReturn(loggingRelaxedValidation);
    final NotificationValidator validator =
        new NotificationValidator(
            validationServiceClient,
            new RequestProcessorState(),
            fhirContext,
            featureFlags,
            npsConfigProperties,
            httpServletRequest);
    validator.init();
    lenient().when(httpServletRequest.getHeader(HEADER_FHIR_API_VERSION)).thenReturn("v1");
    lenient()
        .when(httpServletRequest.getHeader(HEADER_FHIR_PROFILE))
        .thenReturn("ars-profile-snapshots");
    return validator;
  }
}

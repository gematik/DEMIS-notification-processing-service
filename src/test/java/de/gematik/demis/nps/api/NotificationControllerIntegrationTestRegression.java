package de.gematik.demis.nps.api;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.nps.base.util.TimeProvider;
import de.gematik.demis.nps.base.util.UuidGenerator;
import de.gematik.demis.nps.config.TestUserConfiguration;
import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.error.NpsServiceException;
import de.gematik.demis.nps.service.Processor;
import de.gematik.demis.nps.service.Statistics;
import de.gematik.demis.nps.service.response.FhirConverter;
import de.gematik.demis.nps.service.response.FhirResponseService;
import de.gematik.demis.service.base.error.rest.api.ErrorDTO;
import java.nio.charset.StandardCharsets;
import org.hamcrest.Matchers;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
@Import({FhirConverter.class, UuidGenerator.class, TimeProvider.class})
class NotificationControllerIntegrationTestRegression {

  private static final String ENDPOINT = "/fhir/$process-notification";
  private static final String FHIR_NOTIFICATION = "does not matter";
  private static final String RESULT_BODY = "receipt fhir message";
  private static final String TOKEN = "SomeToken";

  @MockBean Processor processor;
  @MockBean FhirContext fhirContext;
  @MockBean FhirResponseService responseService;
  @MockBean TestUserConfiguration testUserConfiguration;
  @MockBean Statistics statistics;

  @Autowired private MockMvc mockMvc;

  private static MessageType getMessageType(final String contentType) {
    return contentType.contains("json") ? MessageType.JSON : MessageType.XML;
  }

  private void setupFhirSerializer(final IBaseResource resource, final MessageType outputFormat) {
    final IParser parser = mock(IParser.class);
    when(parser.encodeResourceToString(resource)).thenReturn(RESULT_BODY);
    when(outputFormat == MessageType.JSON
            ? fhirContext.newJsonParser()
            : fhirContext.newXmlParser())
        .thenReturn(parser);
  }

  @ParameterizedTest
  @CsvSource({
    // permute contentType
    "application/json,application/json",
    "application/json;charset=UTF-8,application/json",
    "application/json+fhir,application/json",
    "application/fhir+json,application/json",
    "application/xml,application/json",
    "application/xml;charset=UTF-8,application/json",
    "application/xml+fhir,application/json",
    "application/fhir+xml,application/json",
    "text/xml,*/*",
    "text/xml;charset=UTF-8,*/*",
    // permute accept
    "application/json,application/json",
    "application/json,application/json+fhir",
    "application/json,application/fhir+json",
    "application/json,application/xml",
    "application/json,application/xml+fhir",
    "application/json,application/fhir+xml",
    "application/json,*/*",
    "application/xml,*/*"
  })
  void success(final String contentType, final String accept) throws Exception {
    final MessageType messageType = getMessageType(contentType);

    final Parameters parameters = new Parameters();
    when(processor.execute(FHIR_NOTIFICATION, messageType, null, null, false, "", TOKEN))
        .thenReturn(parameters);

    final String outputType = accept.equals("*/*") ? contentType : accept;
    setupFhirSerializer(parameters, getMessageType(outputType));

    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(contentType)
                .content(FHIR_NOTIFICATION)
                .header(HttpHeaders.ACCEPT, accept)
                .header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpectAll(
            status().isOk(),
            content().contentTypeCompatibleWith(outputType),
            content().encoding(StandardCharsets.UTF_8),
            content().string(Matchers.equalTo(RESULT_BODY)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"text/json", "text/json+fhir", "text/xml+fhir", "unknown/xml"})
  void unsupportedMediaType(final String contentType) throws Exception {
    Mockito.verifyNoInteractions(processor);

    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(contentType)
                .content(FHIR_NOTIFICATION)
                .header(HttpHeaders.ACCEPT, "*/*"))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void additionalHeaders() throws Exception {
    // json or xml, this does not matter for this test scenario
    final String contentType = APPLICATION_JSON_VALUE;
    final String accept = APPLICATION_JSON_VALUE;

    final String requestId = "1234-5678";
    final String sender = "me";
    final boolean isTestUser = true;

    when(testUserConfiguration.isTestUser(sender)).thenReturn(true);
    when(testUserConfiguration.getReceiver(sender)).thenReturn(sender);

    final Parameters parameters = new Parameters();
    // Assume the test notification is supposed to be forwarded to the sender
    when(processor.execute(
            FHIR_NOTIFICATION,
            getMessageType(contentType),
            requestId,
            sender,
            isTestUser,
            "me",
            TOKEN))
        .thenReturn(parameters);
    setupFhirSerializer(parameters, getMessageType(accept));

    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(contentType)
                .content(FHIR_NOTIFICATION)
                .header(HttpHeaders.ACCEPT, accept)
                .header(HttpHeaders.AUTHORIZATION, TOKEN)
                .header("x-request-id", requestId)
                .header("x-sender", sender)
                .header("x-testuser", isTestUser))
        .andExpectAll(
            status().isOk(),
            content().contentTypeCompatibleWith(accept),
            content().encoding(StandardCharsets.UTF_8),
            content().string(Matchers.equalTo(RESULT_BODY)));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        APPLICATION_JSON_VALUE,
        "application/json+fhir",
        "application/fhir+json",
        APPLICATION_XML_VALUE,
        "application/xml+fhir",
        "application/fhir+xml"
      })
  void processingBusinessException(final String accept) throws Exception {
    final String contentType = APPLICATION_JSON_VALUE; // does not matter for this test scenario

    final OperationOutcome exceptionOutcome = new OperationOutcome();
    when(processor.execute(
            FHIR_NOTIFICATION, getMessageType(contentType), null, null, false, "", TOKEN))
        .thenThrow(new NpsServiceException(ErrorCode.FHIR_VALIDATION_ERROR, exceptionOutcome));

    final OperationOutcome responseOutcome = new OperationOutcome();
    when(responseService.error(any(ErrorDTO.class), eq(exceptionOutcome)))
        .thenReturn(responseOutcome);

    // Assume this test doesn't want to send a test notification
    when(testUserConfiguration.isTestUser(any())).thenReturn(false);
    when(testUserConfiguration.getReceiver(any())).thenReturn("");

    setupFhirSerializer(responseOutcome, getMessageType(accept));

    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(contentType)
                .content(FHIR_NOTIFICATION)
                .header("accept", accept)
                .header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andExpectAll(
            status().is(422),
            content().contentTypeCompatibleWith(accept),
            content().encoding(StandardCharsets.UTF_8),
            content().string(Matchers.equalTo(RESULT_BODY)));
  }

  @Test
  void emptyBody() throws Exception {
    final OperationOutcome outcome = new OperationOutcome();
    when(responseService.error(any(ErrorDTO.class), eq(null))).thenReturn(outcome);
    setupFhirSerializer(outcome, MessageType.JSON);

    mockMvc
        .perform(post(ENDPOINT).contentType(APPLICATION_JSON_VALUE))
        .andExpect(status().is(400))
        .andExpect(content().string(Matchers.equalTo(RESULT_BODY)));

    Mockito.verifyNoInteractions(processor);
  }
}

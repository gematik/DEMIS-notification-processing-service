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
 * For additional notes and disclaimer from gematik and in case of changes by gematik,
 * find details in the "Readme" file.
 * #L%
 */

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.error.NpsServiceException;
import de.gematik.demis.nps.service.Processor;
import de.gematik.demis.nps.service.Statistics;
import de.gematik.demis.nps.test.TestUtil;
import de.gematik.demis.service.base.error.rest.ErrorFieldProvider;
import de.gematik.demis.service.base.error.rest.ErrorHandlerConfiguration;
import de.gematik.demis.service.base.fhir.FhirSupportAutoConfiguration;
import de.gematik.demis.service.base.fhir.error.FhirErrorResponseAutoConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.hamcrest.Matchers;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    value = NotificationController.class,
    properties = "feature.flag.move-error-id-to-diagnostics=false")
@ImportAutoConfiguration({
  ErrorHandlerConfiguration.class,
  FhirSupportAutoConfiguration.class,
  FhirErrorResponseAutoConfiguration.class
})
class NotificationControllerIntegrationRegressionTest {

  private static final String ENDPOINT = "/fhir/$process-notification";
  private static final String FHIR_NOTIFICATION = "does not matter";
  private static final String RESULT_BODY = "receipt fhir message";
  private static final String TOKEN = "SomeToken";

  private static final String EXPECTED_ERROR_OPERATION_OUTCOME =
"""
{
  "resourceType": "OperationOutcome",
  "meta": {
    "profile": [ "https://demis.rki.de/fhir/StructureDefinition/ProcessNotificationResponse" ]
  },
  "text": {
    "status": "generated",
    "div": "<div xmlns=\\"http://www.w3.org/1999/xhtml\\"></div>"
  },
  "issue": [ {
    "severity": "error",
    "code": "processing",
    "details": {
      "coding": [ {
        "code": "FHIR_VALIDATION_ERROR"
      } ]
    },
    "location": [ "75cc12e8-5f3e-4470-a270-dd16052bd0ae" ]
  } ]
}
""";

  private final IParser parser = mock(IParser.class);
  @MockitoBean Processor processor;
  @MockitoBean FhirContext fhirContext;
  @MockitoBean Statistics statistics;

  @MockitoBean(answers = Answers.CALLS_REAL_METHODS)
  ErrorFieldProvider errorFieldProvider;

  @Autowired private MockMvc mockMvc;

  private static MessageType getMessageType(final String contentType) {
    return contentType.contains("json") ? MessageType.JSON : MessageType.XML;
  }

  private void setupFhirSerializer(final IBaseResource resource, final MessageType outputFormat) {
    when(parser.encodeResourceToString(any(resource.getClass()))).thenReturn(RESULT_BODY);
    when(outputFormat == MessageType.JSON
            ? fhirContext.newJsonParser()
            : fhirContext.newXmlParser())
        .thenReturn(parser);
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
            FHIR_NOTIFICATION, getMessageType(contentType), null, null, false, "", TOKEN, Set.of()))
        .thenThrow(new NpsServiceException(ErrorCode.FHIR_VALIDATION_ERROR, exceptionOutcome));

    final OperationOutcome responseOutcome = new OperationOutcome();

    setupFhirSerializer(responseOutcome, getMessageType(accept));
    when(errorFieldProvider.generateId()).thenReturn("75cc12e8-5f3e-4470-a270-dd16052bd0ae");

    mockMvc
        .perform(
            post(ENDPOINT)
                .contentType(contentType)
                .content(FHIR_NOTIFICATION)
                .header("accept", accept)
                .header(HttpHeaders.AUTHORIZATION, TOKEN))
        .andDo(print())
        .andExpectAll(
            status().is(422),
            content().contentTypeCompatibleWith(accept),
            content().encoding(StandardCharsets.UTF_8),
            content().string(Matchers.equalTo(RESULT_BODY)));

    final ArgumentCaptor<OperationOutcome> operationOutcomeCaptor =
        ArgumentCaptor.forClass(OperationOutcome.class);
    verify(parser).encodeResourceToString(operationOutcomeCaptor.capture());
    TestUtil.assertFhirResource(
        operationOutcomeCaptor.getValue(), EXPECTED_ERROR_OPERATION_OUTCOME);
  }
}

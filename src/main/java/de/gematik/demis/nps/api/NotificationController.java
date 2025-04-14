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

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;

import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.nps.service.Processor;
import de.gematik.demis.nps.service.response.FhirConverter;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

@RestController
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

  public static final String HEADER_SENDER = "x-sender";

  private final Processor processor;
  private final FhirConverter fhirConverter;

  @PostMapping(
      path = "fhir/$process-notification",
      consumes = {
        APPLICATION_JSON_VALUE,
        "application/json+fhir",
        "application/fhir+json",
        APPLICATION_XML_VALUE,
        "application/xml+fhir",
        "application/fhir+xml",
        "text/xml"
      },
      produces = {
        APPLICATION_JSON_VALUE,
        "application/json+fhir",
        "application/fhir+json",
        APPLICATION_XML_VALUE,
        "application/xml+fhir",
        "application/fhir+xml",
        "text/xml"
      })
  public ResponseEntity<Object> processNotification(
      @RequestBody @NotBlank final String fhirNotification,
      @RequestHeader(CONTENT_TYPE) final MediaType contentType,
      @RequestHeader(value = "X-Request-ID", required = false) final String requestId,
      @RequestHeader(value = HEADER_SENDER, required = false) final String sender,
      @RequestHeader(value = "x-testuser", required = false) final boolean testUserFlag,
      @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
      final WebRequest request) {
    if ("text".equalsIgnoreCase(contentType.getType())) {
      log.info("sender= {}, deprecated contentType= {}", sender, contentType);
    }
    final Parameters response =
        processor.execute(
            fhirNotification,
            MessageType.getMessageType(contentType.getSubtype()),
            requestId,
            sender,
            testUserFlag,
            authorization);
    return fhirConverter.setResponseContent(ResponseEntity.ok(), response, request);
  }
}

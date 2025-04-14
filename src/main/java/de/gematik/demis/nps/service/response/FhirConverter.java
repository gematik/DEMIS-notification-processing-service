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
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import ca.uhn.fhir.context.FhirContext;
import de.gematik.demis.fhirparserlibrary.FhirParser;
import de.gematik.demis.fhirparserlibrary.MessageType;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.WebRequest;

@Service
@RequiredArgsConstructor
public class FhirConverter {

  private final FhirContext fhirContext;

  private static MediaType determineOutputFormat(final WebRequest webRequest) {
    final String outputFormat;
    final String accept = webRequest.getHeader(HttpHeaders.ACCEPT);
    if (accept != null && !accept.contains("*")) {
      outputFormat = accept;
    } else {
      final String contentType = webRequest.getHeader(HttpHeaders.CONTENT_TYPE);
      outputFormat = contentType != null ? contentType : MediaType.APPLICATION_JSON_VALUE;
    }

    return MediaType.parseMediaType(outputFormat);
  }

  public ResponseEntity<Object> setResponseContent(
      final BodyBuilder bodyBuilder, final IBaseResource resource, final WebRequest webRequest) {
    final MediaType outputFormat =
        new MediaType(determineOutputFormat(webRequest), StandardCharsets.UTF_8);
    final MessageType messageType = MessageType.getMessageType(outputFormat.getSubtype());

    return bodyBuilder
        .contentType(outputFormat)
        .body(new FhirParser(fhirContext).encode(resource, messageType));
  }
}

package de.gematik.demis.nps.base.fhir;

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

import ca.uhn.fhir.context.FhirContext;
import de.gematik.demis.fhirparserlibrary.FhirParser;
import de.gematik.demis.fhirparserlibrary.MessageType;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class FhirParserInitializer {

  private static final String NOTIFICATION_TO_WARMUP_FHIR_PARSER =
      "/fhir/warmup-disease-notification.json";

  private final FhirContext fhirContext;

  private static String readResource(final String resourceName) {
    try (final InputStream is = FhirParserInitializer.class.getResourceAsStream(resourceName)) {
      if (is == null) {
        throw new IllegalStateException("missing resource file " + resourceName);
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (final IOException e) {
      throw new UncheckedIOException("error reading classpath resource " + resourceName, e);
    }
  }

  @PostConstruct
  void warmUp() {
    log.info("warmup fhir parser...");
    final FhirParser fhirParser = new FhirParser(fhirContext);
    try {
      final String json = readResource(NOTIFICATION_TO_WARMUP_FHIR_PARSER);
      fhirParser.parseBundleOrParameter(json, MessageType.JSON);
    } catch (final RuntimeException e) {
      log.warn(
          "error reading/parsing warmup notification. Please check resource "
              + NOTIFICATION_TO_WARMUP_FHIR_PARSER,
          e);
    }
  }
}

package de.gematik.demis.nps.service.codemapping;

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

import de.gematik.demis.service.base.clients.mapping.CodeMappingAutoConfiguration;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Slf4j
@Import(CodeMappingAutoConfiguration.class)
class CodeMappingServiceConfiguration {

  @Bean
  LegacyCodeMappingService legacyCodeMappingService(final FutsClient futsClient) {
    return new LegacyCodeMappingService(futsClient);
  }

  @Bean
  SwitchingCodeMappingService switchingCodeMappingService(
      final LegacyCodeMappingService legacyService,
      @Nullable
          final de.gematik.demis.service.base.clients.mapping.CodeMappingService serviceBaseService,
      @Value("${feature.flag.codemapping.service.base}") final boolean serviceBaseEnabled) {
    return new SwitchingCodeMappingService(legacyService, serviceBaseService, serviceBaseEnabled);
  }
}

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

import de.gematik.demis.service.base.clients.mapping.CodeMappingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SwitchingCodeMappingService {

  private final LegacyCodeMappingService legacyService;

  private final CodeMappingService codeMappingService;

  private final boolean codeMappingServiceBase;

  public SwitchingCodeMappingService(
      LegacyCodeMappingService legacyService,
      CodeMappingService codeMappingService,
      @Value("${feature.flag.codemapping.service.base:false}") boolean codeMappingServiceBase) {

    this.legacyService = legacyService;
    this.codeMappingService = codeMappingService;
    // Use the service-base implementation only when the feature flag is enabled
    // and the CodeMappingService bean is actually available (non-null).
    this.codeMappingServiceBase = codeMappingServiceBase && codeMappingService != null;
  }

  /**
   * delegates the disease code mapping to the new {@link CodeMappingService} or to the {@link
   * LegacyCodeMappingService} depending on the feature flag feature.flag.codemapping.service.base.
   * can be removed together with the feature flag.
   */
  public String getMappedDiseaseCode(String code) {
    if (codeMappingServiceBase) {
      return codeMappingService.mapCode(code);
    }
    return legacyService.getMappedDiseaseCode(code);
  }

  /**
   * delegates the pathogen/laboratory code mapping to the new {@link CodeMappingService} or to the
   * {@link LegacyCodeMappingService} depending on the feature flag
   * feature.flag.codemapping.service.base. can be removed together with the feature flag.
   */
  public String getMappedLaboratoryCode(String code) {
    if (codeMappingServiceBase) {
      return codeMappingService.mapCode(code);
    }
    return legacyService.getMappedPathogenCode(code);
  }
}

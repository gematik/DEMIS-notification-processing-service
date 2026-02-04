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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.demis.service.base.clients.mapping.CodeMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SwitchingCodeMappingServiceTest {

  private static final String DISEASE_CODE = "disease";
  private static final String PATHOGEN_CODE = "lab";

  @Mock private LegacyCodeMappingService legacyService;
  @Mock private CodeMappingService serviceBaseService;

  @Nested
  class ServiceBaseDisabled {

    private SwitchingCodeMappingService underTest;

    @BeforeEach
    void setUp() {
      underTest = new SwitchingCodeMappingService(legacyService, serviceBaseService, false);
    }

    @Test
    void usesLegacyForDisease() {
      when(legacyService.getMappedDiseaseCode(DISEASE_CODE)).thenReturn("legacy-disease");

      assertThat(underTest.getMappedDiseaseCode(DISEASE_CODE)).isEqualTo("legacy-disease");
      verify(serviceBaseService, never()).mapCode(DISEASE_CODE);
    }

    @Test
    void usesLegacyForPathogen() {
      when(legacyService.getMappedPathogenCode(PATHOGEN_CODE)).thenReturn("legacy-lab");

      assertThat(underTest.getMappedLaboratoryCode(PATHOGEN_CODE)).isEqualTo("legacy-lab");
      verify(serviceBaseService, never()).mapCode(PATHOGEN_CODE);
    }
  }

  @Nested
  class ServiceBaseEnabled {

    private SwitchingCodeMappingService underTest;

    @BeforeEach
    void setUp() {
      underTest = new SwitchingCodeMappingService(legacyService, serviceBaseService, true);
    }

    @Test
    void usesServiceBaseDisease() {
      when(serviceBaseService.mapCode(DISEASE_CODE)).thenReturn("sb-disease");

      assertThat(underTest.getMappedDiseaseCode(DISEASE_CODE)).isEqualTo("sb-disease");
      verify(legacyService, never()).getMappedDiseaseCode(DISEASE_CODE);
    }

    @Test
    void usesServiceBasePathogen() {
      when(serviceBaseService.mapCode(PATHOGEN_CODE)).thenReturn("sb-lab");

      assertThat(underTest.getMappedLaboratoryCode(PATHOGEN_CODE)).isEqualTo("sb-lab");
      verify(legacyService, never()).getMappedPathogenCode(PATHOGEN_CODE);
    }
  }
}

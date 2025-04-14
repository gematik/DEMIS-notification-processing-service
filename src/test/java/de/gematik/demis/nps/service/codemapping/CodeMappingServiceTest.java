package de.gematik.demis.nps.service.codemapping;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;

import feign.FeignException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CodeMappingServiceTest {

  private static final String LAB_CODE_KEY = "cvdp";
  private static final String LAB_CODE_VALUE = "cvd";
  private static final String LAB_CODE_VALUE_RELOADED = "cvd2";

  private static final String DIS_CODE_KEY = "cvdd";
  private static final String DIS_CODE_VALUE = "covid";

  private final FutsClient futsClientMock = Mockito.mock(FutsClient.class);
  private CodeMappingService underTest;

  @BeforeEach
  void setup() {
    Mockito.when(futsClientMock.getConceptMap("NotificationCategoryToTransmissionCategory"))
        .thenReturn(Map.of(LAB_CODE_KEY, LAB_CODE_VALUE, "xxx", "yyy"))
        .thenReturn(Map.of(LAB_CODE_KEY, LAB_CODE_VALUE_RELOADED));
    Mockito.when(futsClientMock.getConceptMap("NotificationDiseaseCategoryToTransmissionCategory"))
        .thenReturn(Map.of(DIS_CODE_KEY, DIS_CODE_VALUE, "xxx", "yyy"))
        .thenThrow(FeignException.class);

    underTest = new CodeMappingService(futsClientMock);
    underTest.loadConceptMaps();
  }

  @Test
  void existing_DiseaseCode() {
    final String result = underTest.getMappedDiseaseCode(DIS_CODE_KEY);
    assertThat(result).isEqualTo(DIS_CODE_VALUE);
  }

  @Test
  void existing_PathogenCode() {
    final String result = underTest.getMappedPathogenCode(LAB_CODE_KEY);
    assertThat(result).isEqualTo(LAB_CODE_VALUE);
  }

  @Test
  void not_existing_DiseaseCode() {
    final String result = underTest.getMappedDiseaseCode(LAB_CODE_KEY);
    assertThat(result).isNull();
  }

  @Test
  void not_existing_PathogenCode() {
    final String result = underTest.getMappedPathogenCode(DIS_CODE_KEY);
    assertThat(result).isNull();
  }

  @Test
  void reload() {
    assertThat(underTest.getMappedPathogenCode(LAB_CODE_KEY)).isEqualTo(LAB_CODE_VALUE);
    assertThat(underTest.getMappedDiseaseCode(DIS_CODE_KEY)).isEqualTo(DIS_CODE_VALUE);

    underTest.loadConceptMaps();
    // new value is expected
    assertThat(underTest.getMappedPathogenCode(LAB_CODE_KEY)).isEqualTo(LAB_CODE_VALUE_RELOADED);
    // since second disease futs call throws exception, we expected here the old value
    assertThat(underTest.getMappedDiseaseCode(DIS_CODE_KEY)).isEqualTo(DIS_CODE_VALUE);
    // loadConceptMaps is called 2 time, each call produces 2 futs calls -> 4 futs call expected
    Mockito.verify(futsClientMock, times(4)).getConceptMap(Mockito.anyString());

    // getCode call do not produce futs calls rather is responded from cache
    underTest.getMappedPathogenCode(LAB_CODE_KEY);
    underTest.getMappedDiseaseCode(DIS_CODE_KEY);
    Mockito.verifyNoMoreInteractions(futsClientMock);
  }
}

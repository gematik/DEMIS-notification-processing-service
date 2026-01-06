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

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Provides the mapping of the concept maps 'NotificationCategoryToTransmissionCategory' or
 * 'NotificationDiseaseCategoryToTransmissionCategory'. The mapping is not contained in NPS rather
 * it is defined in the profiles and requested from FUTS.
 *
 * <p>Caching strategy: To avoid a request per each notification and since the mapping is almost
 * static, caching is required. Every 30 minutes (configurable via cron in application.yaml) all
 * entries of the two concept maps are requested, which means 2 rest calls. Is done in an extra
 * thread independent of the notification processing. If the request was successfully, the whole map
 * is replaced otherwise not, i.e. the old cached map will be continued to be used. At start up we
 * load the mapping. If not successfully, we retry on first request.
 */
@Slf4j
public class CodeMappingService {

  private static final String CONCEPT_MAP_LABORATORY = "NotificationCategoryToTransmissionCategory";
  private static final String CONCEPT_MAP_DISEASE =
      "NotificationDiseaseCategoryToTransmissionCategory";

  private final ReloadableCache<String, String> laboratoryCodeCache;
  private final ReloadableCache<String, String> diseaseCodeCache;

  CodeMappingService(final FutsClient futsClient) {
    this.laboratoryCodeCache =
        new ReloadableCache<>("laboratory", () -> futsClient.getConceptMap(CONCEPT_MAP_LABORATORY));
    this.diseaseCodeCache =
        new ReloadableCache<>("disease", () -> futsClient.getConceptMap(CONCEPT_MAP_DISEASE));
  }

  public String getMappedDiseaseCode(final String diseaseCode) {
    return diseaseCodeCache.getValue(diseaseCode);
  }

  public String getMappedPathogenCode(final String pathogenCode) {
    return laboratoryCodeCache.getValue(pathogenCode);
  }

  @PostConstruct
  @Scheduled(cron = "${nps.codemapping.cache.reload.cron}")
  void loadConceptMaps() {
    laboratoryCodeCache.loadCache();
    diseaseCodeCache.loadCache();
  }
}

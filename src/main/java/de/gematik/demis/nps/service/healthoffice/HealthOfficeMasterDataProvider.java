package de.gematik.demis.nps.service.healthoffice;

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

import de.gematik.demis.nps.config.NpsConfigProperties;
import java.util.Map;
import java.util.stream.Collectors;
import org.hl7.fhir.r4.model.Organization;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class HealthOfficeMasterDataProvider {

  private static final String HEALTH_OFFICE_MASTER_DATA =
      "/health-office/TransmittingSiteSearchText.xml";

  private static final String HEALTH_OFFICE_MASTER_DATA_LIVE_TEST =
      "/health-office/TransmittingSiteSearchText-live-test.xml";

  @Bean
  public HealthOfficeMasterDataService healthOfficeMasterDataService(
      final TransmittingSiteSearchParser parser,
      final SubsidiaryService subsidiaryService,
      final NpsConfigProperties config,
      @Value("${feature.flag.notifications.7_4}") boolean isNewProcessing) {
    final String dataFile = getDataFile(config.healthOfficesLiveTest());

    final Map<String, Organization> map =
        parser.readTransmittingSiteSearchXml(dataFile).stream()
            .filter(data -> !subsidiaryService.isSubsidiary(data.code()))
            .collect(
                Collectors.toMap(
                    TransmittingSite::code, HealthOfficeOrganizationCreator::createOrganization));

    return new HealthOfficeMasterDataService(map, isNewProcessing);
  }

  private String getDataFile(final boolean forLiveTest) {
    return forLiveTest ? HEALTH_OFFICE_MASTER_DATA_LIVE_TEST : HEALTH_OFFICE_MASTER_DATA;
  }
}

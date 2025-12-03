package de.gematik.demis.nps.config;

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

import jakarta.annotation.PostConstruct;
import java.util.Map;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "feature")
@Validated
@Builder
@Slf4j
public record FeatureFlagsConfigProperties(Map<String, Object> flag) {

  @PostConstruct
  void log() {
    log.info("NPS FEATURE FLAGS: {}", this.flag);
  }

  /**
   * Checks if a feature is enabled.
   *
   * @param featureName the name of the feature to check, without the "feature.flag." prefix
   * @return true if the feature is enabled, false otherwise
   */
  public boolean isEnabled(final String featureName) {
    final var result = flag.getOrDefault(featureName, false);
    if (result instanceof Boolean enabled) {
      return enabled;
    }
    // in Integration Tests, the feature flags are defined as Strings from SpringBootTest
    if (result instanceof String str) {
      return Boolean.parseBoolean(str);
    }

    // fallback
    return false;
  }
}

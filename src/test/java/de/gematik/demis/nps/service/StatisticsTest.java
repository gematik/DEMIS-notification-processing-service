package de.gematik.demis.nps.service;

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
import static org.assertj.core.api.Assertions.entry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class StatisticsTest {
  @Autowired private MeterRegistry meterRegistry;

  @Test
  void incNotificationEndpointCounter_WithApiVersion() {
    Statistics statistics = new Statistics(null, meterRegistry);

    statistics.incNotificationEndpointCounter("v6", "pathogen", "internal");

    final Map<Tags, Double> counters =
        meterRegistry.find("nps_notifications_internal_pathogen_api_v6").counters().stream()
            .collect(Collectors.toMap(c -> Tags.of(c.getId().getTags()), Counter::count));
    assertThat(counters).containsOnly(entry(Tags.empty(), 1.0d));
  }

  @Test
  void incNotificationEndpointCounter_WithoutApiVersion() {
    Statistics statistics = new Statistics(null, meterRegistry);

    statistics.incNotificationEndpointCounter(null, "pathogen", "internal");

    final Map<Tags, Double> counters =
        meterRegistry.find("nps_notifications_legacy_api").counters().stream()
            .collect(Collectors.toMap(c -> Tags.of(c.getId().getTags()), Counter::count));
    assertThat(counters).containsOnly(entry(Tags.empty(), 1.0d));
  }
}

package de.gematik.demis.nps.integrationtest;

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
 * #L%
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.service.notification.NotificationType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class CounterVerifier {

  private static final String COUNTER_NOTIFICATION_SUCCESS = "notification_success";
  private static final String COUNTER_NOTIFICATION_ERROR = "notification_error";

  private final MeterRegistry meterRegistry;
  private final String user;

  void assertSuccessCounter(final NotificationType expectedType, final String expectedDisease) {
    final Map<Tags, Double> counters = getCounters(COUNTER_NOTIFICATION_SUCCESS);
    assertThat(counters)
        .containsOnly(
            entry(
                Tags.of("type", expectedType.name(), "user", user, "disease_code", expectedDisease),
                1.0d));
    assertZeroCounter(COUNTER_NOTIFICATION_ERROR);
  }

  void assertErrorCounter(final ErrorCode expectedErrorCode) {
    final Map<Tags, Double> counters = getCounters(COUNTER_NOTIFICATION_ERROR);
    assertThat(counters)
        .containsOnly(
            entry(Tags.of("user", user, "error_code", expectedErrorCode.getCode()), 1.0d));
    assertZeroCounter(COUNTER_NOTIFICATION_SUCCESS);
  }

  void assertZeroCounter(final String name) {
    assertThat(meterRegistry.find(name).counters()).isEmpty();
  }

  private Map<Tags, Double> getCounters(final String name) {
    return meterRegistry.find(name).counters().stream()
        .collect(Collectors.toMap(c -> Tags.of(c.getId().getTags()), Counter::count));
  }
}

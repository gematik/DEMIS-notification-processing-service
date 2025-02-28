package de.gematik.demis.nps.service.routing;

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

import de.gematik.demis.nps.config.TestUserConfiguration;
import de.gematik.demis.nps.service.notification.Notification;
import java.util.List;
import org.junit.jupiter.api.Test;

class NRSRoutingInputTest {

  private static final TestUserConfiguration WITH_FORWARDING =
      new TestUserConfiguration(List.of("test-user"), "fallback-test", true);
  private static final TestUserConfiguration WITHOUT_FORWARDING =
      new TestUserConfiguration(List.of("test-user"), "fallback-test", false);

  @Test
  void thatTestUserIsConfiguredWhenRequested() {
    // GIVEN a notification with test user request
    final Notification notification =
        Notification.builder().testUser(true).sender("test-user").build();

    // WHEN we calculate the routing information
    final NRSRoutingInput from = NRSRoutingInput.from(notification, WITH_FORWARDING);

    // THEN the test user is honored
    assertThat(from.isTestUser()).isTrue();
    assertThat(from.testUserId()).isEqualTo("test-user");
  }

  @Test
  void thatTestUserIsConfiguredWhenRequestedFromNonTestUser() {
    // GIVEN a notification with test user request
    final Notification notification =
        Notification.builder().testUser(true).sender("not-a-test-user").build();

    // WHEN we calculate the routing information
    final NRSRoutingInput from = NRSRoutingInput.from(notification, WITH_FORWARDING);

    // THEN the test user is honored
    assertThat(from.isTestUser()).isTrue();
    assertThat(from.testUserId()).isEqualTo("fallback-test");
  }

  @Test
  void thatTestUserIsOverwritten() {
    final Notification notification =
        Notification.builder().testUser(true).sender("test-user").build();

    // WHEN we calculate the routing information
    final NRSRoutingInput from = NRSRoutingInput.from(notification, WITHOUT_FORWARDING);

    // THEN the test user is discarded and the fallback used
    assertThat(from.isTestUser()).isTrue();
    assertThat(from.testUserId()).isEqualTo("fallback-test");
  }

  @Test
  void thatTestUserIsBlankWhenNotTesting() {
    final Notification notification =
        Notification.builder().testUser(false).sender("some-sender").build();

    // WHEN we calculate the routing information
    final NRSRoutingInput from = NRSRoutingInput.from(notification, WITHOUT_FORWARDING);

    // THEN the test user information is disabled and blank
    assertThat(from.isTestUser()).isFalse();
    assertThat(from.testUserId()).isEqualTo("");
  }

  @Test
  void thatTestUserIsBlankWhenNotTestingAndTestUser() {
    final Notification notification =
        Notification.builder().testUser(false).sender("test-user").build();

    // WHEN we calculate the routing information
    final NRSRoutingInput from = NRSRoutingInput.from(notification, WITHOUT_FORWARDING);

    // THEN the test user information is disabled and blank
    assertThat(from.isTestUser()).isFalse();
    assertThat(from.testUserId()).isEqualTo("");
  }
}

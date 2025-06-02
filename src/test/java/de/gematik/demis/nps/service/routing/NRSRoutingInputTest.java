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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.demis.nps.service.notification.Notification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NRSRoutingInputTest {

  @Test
  void thatTestUserRecipientIsAlwaysTakenFromNotification() {
    final Notification notification =
        Notification.builder()
            .testUser(true)
            .testUserRecipient("fallback-test")
            .sender("test-user")
            .build();

    // WHEN we calculate the routing information
    final NRSRoutingInput from = NRSRoutingInput.from(notification);

    // THEN the test user is discarded and the fallback used
    assertThat(from.isTestUser()).isTrue();
    assertThat(from.testUserId()).isEqualTo("fallback-test");
  }

  @Test
  void thatTestUserIsEmptyStringWhenNotTesting() {
    final Notification notification =
        Notification.builder()
            .testUser(false)
            .testUserRecipient("must-not-be-forwarded")
            .sender("some-sender")
            .build();

    // WHEN we calculate the routing information
    final NRSRoutingInput from = NRSRoutingInput.from(notification);

    // THEN the test user information is disabled and blank
    assertThat(from.isTestUser()).isFalse();
    assertThat(from.testUserId()).isEqualTo("");
  }

  @ParameterizedTest
  @CsvSource({
    // original,   reparsed,   expected
    "original,   reparsed,   reparsed",
    "original,   ,           original",
    "original,   '',         original",
    ",           ,           "
  })
  void testOriginalNotificationAsJSON(String original, String reparsed, String expected) {
    Notification notification =
        Notification.builder()
            .originalNotificationAsJson(original)
            .reparsedNotification(reparsed)
            .testUser(false)
            .testUserRecipient("irrelevant")
            .sender("sender")
            .build();

    NRSRoutingInput input = NRSRoutingInput.from(notification);

    assertThat(input.originalNotificationAsJSON()).isEqualTo(expected);
  }
}

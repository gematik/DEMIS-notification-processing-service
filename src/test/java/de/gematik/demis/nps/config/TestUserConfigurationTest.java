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
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * A test to verify that the configuration matches the TestUserConfiguration definition. Changing
 * the constructor parameter names would lead to an invalid configuration, which is hard to discover
 * without explicit testing on a local cluster.
 */
@SpringBootTest
@TestPropertySource(
    properties = {
      // The default application.yaml sets this value to false. False is also the default in case
      // Spring can't find a
      // property. Our test wouldn't verify that the properties match!
      "nps.testuser.sender-is-destination=true"
    })
class NpsConfigPropertiesTest {

  @Autowired private NpsConfigProperties npsConfigProperties;

  @Test
  void thatConfigurationPropertiesAreCorrectlyForwarded() {
    final TestUserConfiguration testUserConfiguration = npsConfigProperties.testuser();
    assertThat(testUserConfiguration.healthOffice()).isEqualTo("test-int");
    assertThat(testUserConfiguration.ids()).isEqualTo(List.of("test-int"));
    assertThat(testUserConfiguration.senderIsDestination()).isEqualTo(true);
  }

  @Test
  void thatUnknownUserIdLeadsToFallback() {
    TestUserConfiguration testUserConfiguration =
        new TestUserConfiguration(List.of("known"), "fallback", true);
    assertThat(testUserConfiguration.getReceiver("unknown")).isEqualTo("fallback");

    testUserConfiguration = new TestUserConfiguration(List.of("known"), "fallback", false);
    assertThat(testUserConfiguration.getReceiver("unknown")).isEqualTo("fallback");
  }

  @Test
  void thatKnownUserIdLeadsToFallbackIfFlagIsDisabled() {
    TestUserConfiguration testUserConfiguration =
        new TestUserConfiguration(List.of("known"), "fallback", false);
    assertThat(testUserConfiguration.getReceiver("known")).isEqualTo("fallback");
  }

  @Test
  void thatKnownUserIdLeadsToIdenticalReceiverIfFlagIsEnabled() {
    TestUserConfiguration testUserConfiguration =
        new TestUserConfiguration(List.of("known"), "fallback", true);
    assertThat(testUserConfiguration.getReceiver("known")).isEqualTo("known");
  }
}

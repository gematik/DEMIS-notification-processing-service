package de.gematik.demis.nps.api;

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

import de.gematik.demis.nps.config.TestUserConfiguration;
import de.gematik.demis.nps.service.Processor;
import de.gematik.demis.nps.service.response.FhirConverter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

@ExtendWith(MockitoExtension.class)
public class NotificationControllerTestRegression {

  @Mock private Processor processor;

  @Mock private FhirConverter fhirConverter;

  @Test
  void thatTestUserCanBeIdentifiedByConfigurationAlone() {
    // GIVEN a configuration that recognizes test-user as test user
    final TestUserConfiguration config = new TestUserConfiguration(List.of("test-user"), "", true);
    final NotificationController notificationController =
        new NotificationController(processor, fhirConverter, config, false);
    notificationController.processNotification(
        "", MediaType.APPLICATION_XML, "", "test-user", false, null, "", null);

    Mockito.verify(processor)
        .execute(
            Mockito.anyString(),
            Mockito.any(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.eq(true),
            Mockito.eq("test-user"),
            Mockito.anyString());
  }

  @Test
  void thatTestUserCanBeIdentifiedByConfigurationAloneAndStillUseFallback() {
    // GIVEN a configuration that recognizes test-user as test user, but we can only forward to the
    // fallback
    final TestUserConfiguration config =
        new TestUserConfiguration(List.of("test-user"), "fallback", false);
    final NotificationController notificationController =
        new NotificationController(processor, fhirConverter, config, false);
    notificationController.processNotification(
        "", MediaType.APPLICATION_XML, "", "test-user", false, null, "", null);

    Mockito.verify(processor)
        .execute(
            Mockito.anyString(),
            Mockito.any(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.eq(true),
            Mockito.eq("fallback"),
            Mockito.anyString());
  }

  @Test
  void thatTestUserFallbackIsSetForTestMessageAndNoTestUser() {
    // GIVEN a configuration with a fallback health-office
    final TestUserConfiguration config =
        new TestUserConfiguration(List.of("some-test-user"), "fallback", true);
    final NotificationController notificationController =
        new NotificationController(processor, fhirConverter, config, false);
    // AND the test header is set to true
    notificationController.processNotification(
        "", MediaType.APPLICATION_XML, "", "regular-user", true, null, "", null);

    // THEN we expect to fall back to the fallback health-office
    Mockito.verify(processor)
        .execute(
            Mockito.anyString(),
            Mockito.any(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.eq(true),
            Mockito.eq("fallback"),
            Mockito.anyString());
  }

  @Test
  void thatTestUserConfigIsIgnoredWhenNotTestNotification() {
    // GIVEN a configuration with a fallback health-office
    final TestUserConfiguration config =
        new TestUserConfiguration(List.of("some-test-user"), "fallback", true);
    final NotificationController notificationController =
        new NotificationController(processor, fhirConverter, config, false);
    // AND the test header is set to false, and we have a regular user
    notificationController.processNotification(
        "", MediaType.APPLICATION_XML, "", "regular-user", false, null, "", null);

    // THEN we expect to get an empty string to avoid null issues
    Mockito.verify(processor)
        .execute(
            Mockito.anyString(),
            Mockito.any(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.eq(false),
            Mockito.eq(""),
            Mockito.anyString());
  }
}

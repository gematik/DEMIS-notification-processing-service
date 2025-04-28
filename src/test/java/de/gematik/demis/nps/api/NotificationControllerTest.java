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

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.times;

import de.gematik.demis.nps.config.TestUserConfiguration;
import de.gematik.demis.nps.error.NpsServiceException;
import de.gematik.demis.nps.service.Processor;
import de.gematik.demis.nps.service.response.FhirConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

@ExtendWith(MockitoExtension.class)
public class NotificationControllerTest {

  @Mock private TestUserConfiguration testUserConfiguration;

  @Mock private Processor processor;

  @Mock private FhirConverter fhirConverter;

  private NotificationController notificationController;

  @BeforeEach
  void setup() {
    notificationController =
        new NotificationController(processor, fhirConverter, testUserConfiguration, true);
  }

  @AfterEach
  void verifyNoInteractionWithTestUserConfiguration() {
    Mockito.verifyNoInteractions(testUserConfiguration);
  }

  @Test
  void thatErrorIsThrownWhenIsTestButRecipientHeaderIsBlank() {
    assertThatExceptionOfType(NpsServiceException.class)
        .isThrownBy(
            () ->
                notificationController.processNotification(
                    "", MediaType.APPLICATION_XML, "", null, true, "  ", "", null));

    Mockito.verifyNoInteractions(processor);
  }

  @Test
  void thatErrorIsThrownWhenIsTestButRecipientHeaderIsEmpty() {
    assertThatExceptionOfType(NpsServiceException.class)
        .isThrownBy(
            () ->
                notificationController.processNotification(
                    "", MediaType.APPLICATION_XML, "", null, true, "", "", null));

    Mockito.verifyNoInteractions(processor);
  }

  @Test
  void thatErrorIsThrownWhenIsTestButRecipientHeaderIsNull() {
    assertThatExceptionOfType(NpsServiceException.class)
        .isThrownBy(
            () ->
                notificationController.processNotification(
                    "", MediaType.APPLICATION_XML, "", null, true, null, "", null));

    Mockito.verifyNoInteractions(processor);
  }

  @Test
  void thatRecipientIsEmptyForRegularNotificationsDespiteHeader() {
    // GIVEN a regular notification (testUserFlag = false), but a recipient is provided
    notificationController.processNotification(
        "", MediaType.APPLICATION_XML, "", "sender", false, "$sender", "", null);
    notificationController.processNotification(
        "", MediaType.APPLICATION_XML, "", "sender", false, "test-recipient", "", null);
    Mockito.verify(processor, times(2))
        .execute(
            Mockito.anyString(),
            Mockito.any(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.eq(false),
            Mockito.eq(""),
            Mockito.anyString());
  }

  @Test
  void thatErrorIsThrownIfNoSenderPresentBut$SenderIsUsed() {
    assertThatExceptionOfType(NpsServiceException.class)
        .isThrownBy(
            () ->
                notificationController.processNotification(
                    "", MediaType.APPLICATION_XML, "", null, true, "$sender", "", null));
    assertThatExceptionOfType(NpsServiceException.class)
        .isThrownBy(
            () ->
                notificationController.processNotification(
                    "", MediaType.APPLICATION_XML, "", "  ", true, "$sender", "", null));

    Mockito.verifyNoInteractions(processor);
  }

  @Test
  void that$SenderIsReplacedWithSenderForTestNotification() {
    // GIVEN a test header is present AND the recipient is $sender
    notificationController.processNotification(
        "", MediaType.APPLICATION_XML, "", "test-user", true, "$sender", "", null);

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
  void thatRecipientHeaderIsUsedForTestNotification() {
    notificationController.processNotification(
        "", MediaType.APPLICATION_XML, "", "sender", true, "test-recipient", "", null);
    Mockito.verify(processor)
        .execute(
            Mockito.anyString(),
            Mockito.any(),
            Mockito.anyString(),
            Mockito.anyString(),
            Mockito.eq(true),
            Mockito.eq("test-recipient"),
            Mockito.anyString());
  }
}

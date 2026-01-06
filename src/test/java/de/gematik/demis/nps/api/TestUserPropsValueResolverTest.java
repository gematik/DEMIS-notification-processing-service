package de.gematik.demis.nps.api;

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

import static de.gematik.demis.nps.api.NotificationController.HEADER_SENDER;
import static de.gematik.demis.nps.api.TestUserPropsValueResolver.HEADER_IS_TEST_NOTIFICATION;
import static de.gematik.demis.nps.api.TestUserPropsValueResolver.HEADER_TEST_USER_RECIPIENT;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import de.gematik.demis.nps.error.NpsServiceException;
import java.util.Map;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class TestUserPropsValueResolverTest {

  public static final MethodParameter MOCK = mock(MethodParameter.class);
  private final TestUserPropsValueResolver resolver = new TestUserPropsValueResolver();

  private static ServletWebRequest withHeaders(@Nonnull final Map<String, String> headers) {
    final MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
    headers.forEach(mockHttpServletRequest::addHeader);
    return new ServletWebRequest(mockHttpServletRequest);
  }

  @Test
  void thatAnAbsentIsTestNotificationHeaderEvaluatesToFalse() {
    // GIVEN we don't set HEADER_IS_TEST_NOTIFICATION
    final ServletWebRequest request = withHeaders(Map.of());
    // THEN we can still the method
    assertThatNoException().isThrownBy(() -> resolver.resolveArgument(MOCK, null, request, null));
  }

  @Test
  void thatErrorIsThrownWhenIsTestButRecipientHeaderIsBlank() {
    final ServletWebRequest request =
        withHeaders(Map.of(HEADER_IS_TEST_NOTIFICATION, "true", HEADER_TEST_USER_RECIPIENT, " "));

    assertThatExceptionOfType(NpsServiceException.class)
        .isThrownBy(() -> resolver.resolveArgument(MOCK, null, request, null));
  }

  @Test
  void thatErrorIsThrownWhenIsTestButRecipientHeaderIsEmpty() {
    final ServletWebRequest request =
        withHeaders(Map.of(HEADER_IS_TEST_NOTIFICATION, "true", HEADER_TEST_USER_RECIPIENT, ""));

    assertThatExceptionOfType(NpsServiceException.class)
        .isThrownBy(() -> resolver.resolveArgument(MOCK, null, request, null));
  }

  @Test
  void thatErrorIsThrownWhenIsTestButRecipientHeaderIsNull() {
    final ServletWebRequest request = withHeaders(Map.of(HEADER_IS_TEST_NOTIFICATION, "true"));

    assertThatExceptionOfType(NpsServiceException.class)
        .isThrownBy(() -> resolver.resolveArgument(MOCK, null, request, null));
  }

  @ValueSource(strings = {"$sender", "recipient-id"})
  @ParameterizedTest
  void thatRecipientIsEmptyForRegularNotificationsDespiteHeader(@Nonnull final String recipient) {
    // GIVEN a regular notification (testUserFlag = false), but a recipient is provided
    final ServletWebRequest request =
        withHeaders(
            Map.of(HEADER_IS_TEST_NOTIFICATION, "false", HEADER_TEST_USER_RECIPIENT, recipient));
    final TestUserProps testUserProps = resolver.resolveArgument(MOCK, null, request, null);
    assertThat(testUserProps.isTestNotification()).isFalse();
    assertThat(testUserProps.testUserRecipient()).isEmpty();
  }

  @Test
  void thatErrorIsThrownIfNoSenderPresentBut$SenderIsUsed() {
    final ServletWebRequest request =
        withHeaders(
            Map.of(HEADER_IS_TEST_NOTIFICATION, "true", HEADER_TEST_USER_RECIPIENT, "$sender"));

    assertThatExceptionOfType(NpsServiceException.class)
        .isThrownBy(() -> resolver.resolveArgument(MOCK, null, request, null));
  }

  @Test
  void thatErrorIsThrownIfSenderIsBlankBut$SenderIsUsed() {
    final ServletWebRequest request =
        withHeaders(
            Map.of(
                HEADER_IS_TEST_NOTIFICATION,
                "true",
                HEADER_TEST_USER_RECIPIENT,
                "$sender",
                HEADER_SENDER,
                ""));

    assertThatExceptionOfType(NpsServiceException.class)
        .isThrownBy(() -> resolver.resolveArgument(MOCK, null, request, null));
  }

  @Test
  void that$SenderIsReplacedWithSenderForTestNotification() {
    final ServletWebRequest request =
        withHeaders(
            Map.of(
                HEADER_IS_TEST_NOTIFICATION,
                "true",
                HEADER_TEST_USER_RECIPIENT,
                "$sender",
                HEADER_SENDER,
                "test-user"));
    final TestUserProps testUserProps = resolver.resolveArgument(MOCK, null, request, null);
    assertThat(testUserProps.isTestNotification()).isTrue();
    assertThat(testUserProps.testUserRecipient()).isEqualTo("test-user");
  }

  @Test
  void thatRecipientHeaderIsUsedForTestNotification() {
    final ServletWebRequest request =
        withHeaders(
            Map.of(
                HEADER_IS_TEST_NOTIFICATION,
                "true",
                HEADER_TEST_USER_RECIPIENT,
                "recipient-to-use",
                HEADER_SENDER,
                "test-user"));
    final TestUserProps testUserProps = resolver.resolveArgument(MOCK, null, request, null);
    assertThat(testUserProps.isTestNotification()).isTrue();
    assertThat(testUserProps.testUserRecipient()).isEqualTo("recipient-to-use");
  }
}

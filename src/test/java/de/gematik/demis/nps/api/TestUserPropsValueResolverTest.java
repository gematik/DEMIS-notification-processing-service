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

import static de.gematik.demis.nps.api.NotificationController.HEADER_SENDER;
import static de.gematik.demis.nps.api.TestUserPropsValueResolver.HEADER_IS_TEST_NOTIFICATION;
import static de.gematik.demis.nps.api.TestUserPropsValueResolver.HEADER_TEST_USER_RECIPIENT;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import de.gematik.demis.nps.config.TestUserConfiguration;
import de.gematik.demis.nps.error.NpsServiceException;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class TestUserPropsValueResolverTest {

  public static final MethodParameter MOCK = mock(MethodParameter.class);
  // ensure that a "valid" test configuration is present in case we are not properly handling the
  // feature flag
  private final TestUserPropsValueResolver resolver =
      new TestUserPropsValueResolver(
          new TestUserConfiguration(List.of("any", "list", "is good"), "will-be-ignored", false),
          true);

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

  @Nested
  class Regression {

    @Test
    void thatTestUserCanBeIdentifiedByConfigurationAlone() {
      // GIVEN a configuration that recognizes test-user as test user
      final TestUserConfiguration config =
          new TestUserConfiguration(List.of("test-user"), "", true);
      final TestUserPropsValueResolver service = new TestUserPropsValueResolver(config, false);

      final ServletWebRequest request =
          withHeaders(Map.of(HEADER_SENDER, "test-user", HEADER_IS_TEST_NOTIFICATION, "false"));
      final TestUserProps testUserProps = service.resolveArgument(MOCK, null, request, null);
      assertThat(testUserProps.isTestNotification()).isTrue();
      assertThat(testUserProps.testUserRecipient()).isEqualTo("test-user");
    }

    @Test
    void thatTestUserCanBeIdentifiedByConfigurationAloneAndStillUseFallback() {
      // GIVEN a configuration that recognizes test-user as test user, but we can only forward to
      // the fallback
      final TestUserConfiguration config =
          new TestUserConfiguration(List.of("test-user"), "fallback", false);
      final TestUserPropsValueResolver service = new TestUserPropsValueResolver(config, false);

      final ServletWebRequest request =
          withHeaders(Map.of(HEADER_SENDER, "test-user", HEADER_IS_TEST_NOTIFICATION, "false"));
      final TestUserProps testUserProps = service.resolveArgument(MOCK, null, request, null);
      assertThat(testUserProps.isTestNotification()).isTrue();
      assertThat(testUserProps.testUserRecipient()).isEqualTo("fallback");
    }

    @Test
    void thatTestUserFallbackIsSetForTestMessageAndNoTestUser() {
      // GIVEN a configuration with a fallback health-office
      final TestUserConfiguration config =
          new TestUserConfiguration(List.of("some-test-user"), "fallback", true);
      final TestUserPropsValueResolver service = new TestUserPropsValueResolver(config, false);

      final ServletWebRequest request =
          withHeaders(
              Map.of(
                  HEADER_SENDER, "regular-user",
                  HEADER_IS_TEST_NOTIFICATION, "true"));
      final TestUserProps testUserProps = service.resolveArgument(MOCK, null, request, null);
      assertThat(testUserProps.isTestNotification()).isTrue();
      assertThat(testUserProps.testUserRecipient()).isEqualTo("fallback");
    }

    @Test
    void thatTestUserConfigIsIgnoredWhenNotTestNotification() {
      // GIVEN a configuration with a fallback health-office
      final TestUserConfiguration config =
          new TestUserConfiguration(List.of("some-test-user"), "fallback", true);
      final TestUserPropsValueResolver service = new TestUserPropsValueResolver(config, false);

      final ServletWebRequest request =
          withHeaders(
              Map.of(
                  HEADER_SENDER, "regular-user",
                  HEADER_IS_TEST_NOTIFICATION, "false"));
      final TestUserProps testUserProps = service.resolveArgument(MOCK, null, request, null);
      assertThat(testUserProps.isTestNotification()).isFalse();
      assertThat(testUserProps.testUserRecipient()).isEmpty();
    }
  }
}

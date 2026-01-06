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

import com.google.common.base.Strings;
import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.error.NpsServiceException;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class TestUserPropsValueResolver implements HandlerMethodArgumentResolver {

  public static final String HEADER_TEST_USER_RECIPIENT = "x-testuser-recipient";
  public static final String HEADER_IS_TEST_NOTIFICATION = "x-testuser";

  @Nonnull
  private TestUserProps getTestUserProps(
      final boolean isTestNotification,
      @CheckForNull String testUserRecipient,
      @CheckForNull String sender) {
    if (!isTestNotification) {
      return new TestUserProps(false, "");
    }

    testUserRecipient = Strings.nullToEmpty(testUserRecipient);
    if (testUserRecipient.isBlank()) {
      throw new NpsServiceException(
          ErrorCode.INVALID_TEST_CONFIGURATION,
          "Is test request, but no recipient provided with header");
    }

    sender = Strings.nullToEmpty(sender);
    if ("$sender".equals(testUserRecipient) && sender.isBlank()) {
      throw new NpsServiceException(
          ErrorCode.INVALID_TEST_CONFIGURATION,
          "Is test request, recipient is $sender but no sender provided with header");
    }
    if ("$sender".equals(testUserRecipient)) {
      testUserRecipient = sender;
    }

    return new TestUserProps(isTestNotification, testUserRecipient);
  }

  @Override
  public boolean supportsParameter(final MethodParameter parameter) {
    return TestUserProps.class.isAssignableFrom(parameter.getParameterType());
  }

  @Override
  @Nonnull
  public TestUserProps resolveArgument(
      @Nonnull final MethodParameter parameter,
      final ModelAndViewContainer mavContainer,
      final NativeWebRequest webRequest,
      final WebDataBinderFactory binderFactory) {
    final boolean isTestNotification =
        Boolean.parseBoolean(webRequest.getHeader(HEADER_IS_TEST_NOTIFICATION));
    final String testUserRecipient =
        Strings.nullToEmpty(webRequest.getHeader(HEADER_TEST_USER_RECIPIENT));
    final String sender =
        Strings.nullToEmpty(webRequest.getHeader(NotificationController.HEADER_SENDER));

    return getTestUserProps(isTestNotification, testUserRecipient, sender);
  }
}

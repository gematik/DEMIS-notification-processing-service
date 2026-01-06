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
import de.gematik.demis.service.base.security.jwt.TokenFactory;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolve {@link JwtToken} from the Authorization header and make it available as method parameter
 * for controllers.
 */
@Slf4j
@Component
public class JwtTokenValueResolver implements HandlerMethodArgumentResolver {

  @Override
  public boolean supportsParameter(final MethodParameter parameter) {
    return JwtToken.class.isAssignableFrom(parameter.getParameterType());
  }

  @Override
  public Object resolveArgument(
      @Nonnull final MethodParameter parameter,
      final ModelAndViewContainer mavContainer,
      @Nonnull final NativeWebRequest webRequest,
      final WebDataBinderFactory binderFactory) {
    final String authorization =
        Strings.nullToEmpty(webRequest.getHeader(HttpHeaders.AUTHORIZATION));
    if (authorization.isBlank()) {
      return JwtToken.EMPTY;
    }

    try {
      final TokenFactory tokenFactory = new TokenFactory(authorization);
      return JwtToken.from(tokenFactory.get());
    } catch (IllegalArgumentException e) {
      log.warn("Can't parse token from header, return empty token", e);
      return JwtToken.EMPTY;
    }
  }
}

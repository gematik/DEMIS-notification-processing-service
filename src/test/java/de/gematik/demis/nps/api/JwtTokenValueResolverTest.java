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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class JwtTokenValueResolverTest {

  private final JwtTokenValueResolver resolver = new JwtTokenValueResolver();

  @Test
  void thatItCanHandleAnAbsentHeader() {
    final Method mockMethod = mock(Method.class);
    final MethodParameter methodParameter = new MethodParameter(mockMethod, -1);
    final MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
    final ServletWebRequest servletWebRequest = new ServletWebRequest(mockHttpServletRequest);
    final Object actual = resolver.resolveArgument(methodParameter, null, servletWebRequest, null);
    assertThat(actual).isEqualTo(JwtToken.EMPTY);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "Bearer", "Bearer ", "InvalidToken", "Bearer123"})
  void thatItCanHandleInvalidHeaders(String headerValue) {
    final Method mockMethod = mock(Method.class);
    final MethodParameter methodParameter = new MethodParameter(mockMethod, -1);
    final MockHttpServletRequest mockHttpServletRequest = new MockHttpServletRequest();
    mockHttpServletRequest.addHeader(HttpHeaders.AUTHORIZATION, headerValue);
    final ServletWebRequest servletWebRequest = new ServletWebRequest(mockHttpServletRequest);
    final Object actual = resolver.resolveArgument(methodParameter, null, servletWebRequest, null);
    assertThat(actual).isEqualTo(JwtToken.EMPTY);
  }
}

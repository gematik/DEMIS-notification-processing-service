package de.gematik.demis.nps.config;

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

import de.gematik.demis.nps.api.JwtTokenValueResolver;
import de.gematik.demis.nps.api.TestUserPropsValueResolver;
import de.gematik.demis.nps.base.util.RequestInterceptor;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final RequestInterceptor requestInterceptor;
  private final JwtTokenValueResolver tokenValueResolver;
  private final TestUserPropsValueResolver testUserPropsValueResolver;

  public WebConfig(
      final JwtTokenValueResolver tokenValueResolver,
      final TestUserPropsValueResolver testUserPropsValueResolver,
      final RequestInterceptor requestInterceptor) {
    this.tokenValueResolver = tokenValueResolver;
    this.testUserPropsValueResolver = testUserPropsValueResolver;
    this.requestInterceptor = requestInterceptor;
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(tokenValueResolver);
    resolvers.add(testUserPropsValueResolver);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(requestInterceptor);
  }
}

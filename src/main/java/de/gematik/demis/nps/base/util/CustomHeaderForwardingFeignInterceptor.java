package de.gematik.demis.nps.base.util;

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

import de.gematik.demis.nps.config.NpsHeaders;
import de.gematik.demis.service.base.feign.HeadersForwardingRequestInterceptor;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.util.Collection;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * This Feign interceptor is based on the global {@link HeadersForwardingRequestInterceptor}
 * provided by service-base. Additionally, it sets the outgoing x-fhir-profile header with the value
 * resolved in {@link FhirProfileContext} if the header is not already present.
 */
@Component
@ConditionalOnProperty(name = "feature.flag.feign_interceptor_enabled", havingValue = "true")
public class CustomHeaderForwardingFeignInterceptor implements RequestInterceptor {

  private final FhirProfileContext fhirProfileContext;
  private final RequestInterceptor forwardingInterceptor;

  public CustomHeaderForwardingFeignInterceptor(FhirProfileContext fhirProfileContext) {
    this.fhirProfileContext = fhirProfileContext;
    this.forwardingInterceptor = new HeadersForwardingRequestInterceptor();
  }

  @Override
  public void apply(RequestTemplate requestTemplate) {
    if (!inHttpContext()) {
      // If there is no HTTP context, we cannot forward headers, so we skip the interceptor logic.
      return;
    }

    forwardingInterceptor.apply(requestTemplate);

    if (!hasXFhirProfileHeader(requestTemplate)) {
      requestTemplate.header(
          NpsHeaders.HEADER_FHIR_PROFILE, fhirProfileContext.getOutgoingFhirProfileHeaderValue());
    }
  }

  private boolean hasXFhirProfileHeader(RequestTemplate requestTemplate) {
    return requestTemplate.headers().entrySet().stream()
        .anyMatch(this::isNonBlankFhirProfileHeader);
  }

  private boolean isNonBlankFhirProfileHeader(Map.Entry<String, Collection<String>> entry) {
    Collection<String> values = entry.getValue();

    return entry.getKey().equalsIgnoreCase(NpsHeaders.HEADER_FHIR_PROFILE)
        && values != null
        && values.stream().anyMatch(StringUtils::isNotBlank);
  }

  private boolean inHttpContext() {
    return RequestContextHolder.getRequestAttributes() != null;
  }
}

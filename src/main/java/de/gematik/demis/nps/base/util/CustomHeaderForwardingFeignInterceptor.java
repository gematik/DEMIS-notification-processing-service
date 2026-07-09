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

import static de.gematik.demis.nps.base.util.FhirPackageContext.FEATURE_FLAG_FHIR_CORE_SPLIT;
import static de.gematik.demis.nps.config.NpsHeaders.HEADER_FHIR_PACKAGE;

import de.gematik.demis.nps.config.FeatureFlagsConfigProperties;
import de.gematik.demis.nps.config.NpsHeaders;
import de.gematik.demis.service.base.feign.HeadersForwardingRequestInterceptor;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * This Feign interceptor is based on the global {@link HeadersForwardingRequestInterceptor}
 * provided by service-base. Additionally, it sets the outgoing headers {@link
 * NpsHeaders#HEADER_FHIR_PACKAGE } and {@link NpsHeaders#HEADER_FHIR_PACKAGE_VERSION } with the
 * value resolved in {@link FhirPackageContext} if they were not already present in the incoming
 * request
 */
@Component
@ConditionalOnProperty(name = "feature.flag.feign_interceptor_enabled", havingValue = "true")
public class CustomHeaderForwardingFeignInterceptor implements RequestInterceptor {

  private final FhirPackageContext fhirPackageContext;
  private final RequestInterceptor forwardingInterceptor;
  private final FeatureFlagsConfigProperties featureFlagsConfigProperties;

  public CustomHeaderForwardingFeignInterceptor(
      FhirPackageContext fhirPackageContext,
      FeatureFlagsConfigProperties featureFlagsConfigProperties) {
    this.fhirPackageContext = fhirPackageContext;
    this.featureFlagsConfigProperties = featureFlagsConfigProperties;
    this.forwardingInterceptor = new HeadersForwardingRequestInterceptor();
  }

  /**
   * @param requestTemplate
   */
  @Override
  public void apply(RequestTemplate requestTemplate) {
    if (!inHttpContext()) {
      // If there is no HTTP context, we cannot forward headers, so we skip the interceptor logic.
      return;
    }

    forwardingInterceptor.apply(requestTemplate);
    setMissingHeadersFromContext(requestTemplate);
  }

  private void setMissingHeadersFromContext(RequestTemplate requestTemplate) {
    if (!hasXFhirPackageHeader(requestTemplate)) {
      requestTemplate.header(
          HEADER_FHIR_PACKAGE, fhirPackageContext.getOutgoingFhirPackageHeaderValue());
    }

    if (!hasXFhirPackageVersionHeader(requestTemplate)
        && featureFlagsConfigProperties.isEnabled(FEATURE_FLAG_FHIR_CORE_SPLIT)) {
      requestTemplate.header(
          NpsHeaders.HEADER_FHIR_PACKAGE_VERSION,
          fhirPackageContext.getOutgoingFhirPackageVersionHeaderValue());
    }
  }

  private boolean hasXFhirPackageHeader(RequestTemplate requestTemplate) {
    return hasNonBlankHeader(requestTemplate, NpsHeaders.HEADER_FHIR_PACKAGE);
  }

  private boolean hasXFhirPackageVersionHeader(RequestTemplate requestTemplate) {
    return hasNonBlankHeader(requestTemplate, NpsHeaders.HEADER_FHIR_PACKAGE_VERSION);
  }

  private boolean hasNonBlankHeader(RequestTemplate requestTemplate, String headerName) {
    return requestTemplate.headers().entrySet().stream()
        .filter(entry -> entry.getKey().equalsIgnoreCase(headerName))
        .map(Map.Entry::getValue)
        .filter(Objects::nonNull)
        .flatMap(Collection::stream)
        .anyMatch(StringUtils::isNotBlank);
  }

  private boolean inHttpContext() {
    return RequestContextHolder.getRequestAttributes() != null;
  }
}

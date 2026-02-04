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

import de.gematik.demis.nps.service.Statistics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class RequestInterceptor implements HandlerInterceptor {
  private static final String HEADER_FHIR_API_VERSION = "x-fhir-api-version";
  private static final String HEADER_FHIR_SUBMISSION_TYPE = "x-fhir-api-submission-type";
  private static final String HEADER_FHIR_REQUEST_ORIGIN = "x-fhir-api-request-origin";
  public static final String HEADER_SENDER = "x-sender";

  private final RequestNotificationProperties requestNotificationProperties;
  private final Statistics statistics;

  @SuppressWarnings("unusued")
  @Override
  public boolean preHandle(
      @NonNull final HttpServletRequest request,
      @NonNull final HttpServletResponse response,
      @NonNull final Object handler) {
    final String apiVersion = request.getHeader(HEADER_FHIR_API_VERSION);
    final String submissionType = request.getHeader(HEADER_FHIR_SUBMISSION_TYPE);
    final String requestOriginHeader = request.getHeader(HEADER_FHIR_REQUEST_ORIGIN);
    final String sender = request.getHeader(HEADER_SENDER);

    statistics.incNotificationEndpointCounter(apiVersion, submissionType, requestOriginHeader);

    requestNotificationProperties.setSubmissionType(submissionType);
    requestNotificationProperties.setApiVersion(apiVersion);
    requestNotificationProperties.setSender(sender);
    return true;
  }

  @Override
  public void afterCompletion(
      @NonNull final HttpServletRequest request,
      @NonNull final HttpServletResponse response,
      @NonNull final Object handler,
      final Exception ex) {
    if (response.getStatus() == 200) {
      statistics.incSuccessCounter();
      NotificationLogger.logSuccessfulNotification(requestNotificationProperties);
    }
  }
}

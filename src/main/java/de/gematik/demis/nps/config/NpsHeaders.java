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

/**
 * Defines constants for HTTP headers used in the NPS application (incoming and outgoing). This
 * class is a central place to manage header names, ensuring consistency across the application and
 * avoiding hard-coded strings scattered throughout the codebase.
 */
public final class NpsHeaders {
  private NpsHeaders() {}

  public static final String HEADER_FHIR_REQUEST_ORIGIN = "x-fhir-api-request-origin";
  public static final String HEADER_FHIR_SUBMISSION_TYPE = "x-fhir-api-submission-type";
  @Deprecated public static final String HEADER_FHIR_API_VERSION = "x-fhir-api-version";
  public static final String HEADER_FHIR_PACKAGE_VERSION = "x-fhir-package-version";
  @Deprecated public static final String HEADER_FHIR_PROFILE = "x-fhir-profile";
  public static final String HEADER_FHIR_PACKAGE = "x-fhir-package";
  public static final String HEADER_SENDER = "x-sender";
}

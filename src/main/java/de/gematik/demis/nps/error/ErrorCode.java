package de.gematik.demis.nps.error;

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

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum ErrorCode {
  UNSUPPORTED_PROFILE(HttpStatus.BAD_REQUEST),
  FHIR_VALIDATION_FATAL(HttpStatus.BAD_REQUEST),
  FHIR_VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_ENTITY),
  LIFECYCLE_VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_ENTITY),
  MISSING_RESPONSIBLE(HttpStatus.UNPROCESSABLE_ENTITY),
  HEALTH_OFFICE_CERTIFICATE(HttpStatus.INTERNAL_SERVER_ERROR),
  ENCRYPTION(HttpStatus.INTERNAL_SERVER_ERROR),
  STORING(HttpStatus.INTERNAL_SERVER_ERROR),
  NRS_PARSING_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
  NRS_PROCESSING_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
  MISSING_ROLES(HttpStatus.FORBIDDEN),

  // errors that do not abort the process
  NO_PSEUDONYM(null),
  NO_PDF(null),
  INVALID_TEST_CONFIGURATION(HttpStatus.INTERNAL_SERVER_ERROR);

  private final HttpStatus httpStatus;

  public String getCode() {
    return name();
  }
}

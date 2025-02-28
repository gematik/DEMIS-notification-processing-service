package de.gematik.demis.nps.service.notification;

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
 * #L%
 */

import static de.gematik.demis.nps.error.ErrorCode.NRS_PARSING_ERROR;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import de.gematik.demis.nps.error.NpsServiceException;

public enum Action {
  ENCRYPTION,
  PSEUDO_COPY,
  NO_ACTION;

  @JsonCreator
  public static Action fromValue(String value) {
    try {
      return Action.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new NpsServiceException(NRS_PARSING_ERROR, "Invalid action: " + value);
    }
  }

  @JsonValue
  public String toValue() {
    return this.name().toLowerCase();
  }
}

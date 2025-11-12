package de.gematik.demis.nps.base.util;

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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UUIDValidatorTest {
  @Test
  void toUUIDIfValid_Valid_UUID_ReturnsUUID() {
    final UUID uuid = UUID.randomUUID();
    assertThat(UUIDValidator.isValidUUID(uuid.toString())).isTrue();
  }

  @Test
  void toUUIDIfValid_Invalid_UUID_ThrowsException() {
    assertThat(UUIDValidator.isValidUUID("12345")).isFalse();
  }
}

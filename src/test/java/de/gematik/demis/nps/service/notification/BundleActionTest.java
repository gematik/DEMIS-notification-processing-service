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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik,
 * find details in the "Readme" file.
 * #L%
 */

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.demis.nps.service.processing.BundleAction;
import de.gematik.demis.nps.service.processing.BundleActionType;
import org.junit.jupiter.api.Test;

class BundleActionTest {

  @Test
  void thatEqualsIgnoresIsOptional() {
    final BundleAction required = BundleAction.requiredOf(BundleActionType.NO_ACTION);
    final BundleAction optional = BundleAction.optionalOf(BundleActionType.NO_ACTION);

    assertThat(required).isEqualTo(optional);
  }

  @Test
  void thatTwoActionsOfDifferentTypeAreNotEqual() {
    final BundleAction required = BundleAction.requiredOf(BundleActionType.CREATE_PSEUDONYM_RECORD);
    final BundleAction optional = BundleAction.optionalOf(BundleActionType.NO_ACTION);

    assertThat(required).isNotEqualTo(optional);
  }

  @Test
  void thatTwoUnknownActionsAreEqual() {
    /*
     * This is a tricky one. Imagine the NRS config changes to include two new actions: A, B
     * When we parse them here in the NPS we would generate UNKNOWN, UNKNOWN. Which should break our SequencedSet
     * setup. We might want to changes this at one point, e.g. by adding the original action name, then we'd have
     * UNKNOWN(A), UNKNOWN(B).
     *
     * If we implement equals() to always return false, the contract for hashCode will fall apart. So we'd really
     * need the original action name to make it work correctly.
     */
    final BundleAction b = BundleAction.requiredOf(BundleActionType.UNKNOWN);
    final BundleAction a = BundleAction.requiredOf(BundleActionType.UNKNOWN);

    assertThat(a).isEqualTo(b);
  }
}

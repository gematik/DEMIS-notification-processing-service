package de.gematik.demis.nps.service.processing;

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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Represents a transformation on a bundle that can be optional. The equals contract states that two
 * BundleActions are equal if their type is equal. See {@link BundleAction#equals(Object)} for more
 * details.
 */
public record BundleAction(BundleActionType type, @JsonProperty("optional") boolean isOptional) {

  /** Create a new BundleAction that is optional */
  public static BundleAction optionalOf(BundleActionType type) {
    return new BundleAction(type, true);
  }

  /** Create a new BundleAction that is required. */
  public static BundleAction requiredOf(BundleActionType type) {
    return new BundleAction(type, false);
  }

  /**
   * We redefine equals only based on the type not whether the BundleAction is optional. If we use
   * this in a Set we want to make sure that there is only one BundleAction of Type X. If there was
   * an optional X and X this wouldn't make any sense and the required X should win.
   */
  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final BundleAction action = (BundleAction) o;
    return type == action.type;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(type);
  }
}

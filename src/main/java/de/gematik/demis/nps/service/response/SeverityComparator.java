package de.gematik.demis.nps.service.response;

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

import java.util.Comparator;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;

/**
 * Comparator for IssueSeverity. (e.g. {@code IssueSeverity.WARNING > IssueSeverity.INFORMATION})
 */
class SeverityComparator implements Comparator<IssueSeverity> {

  private static int getOrderValue(final IssueSeverity is) {
    if (is == null) {
      return -1;
    }

    return switch (is) {
      case FATAL -> 4;
      case ERROR -> 3;
      case WARNING -> 2;
      case INFORMATION -> 1;
      case NULL -> 0;
      default -> -1;
    };
  }

  @Override
  public int compare(final IssueSeverity o1, final IssueSeverity o2) {
    return Integer.compare(getOrderValue(o1), getOrderValue(o2));
  }
}

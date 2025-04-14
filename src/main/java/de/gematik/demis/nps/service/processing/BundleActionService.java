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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.error.NpsServiceException;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.service.pseudonymization.PseudoService;
import java.util.SequencedSet;
import javax.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BundleActionService {

  private final PseudoService pseudoService;

  @Autowired
  public BundleActionService(final PseudoService pseudoService) {
    this.pseudoService = pseudoService;
  }

  public void process(
      @Nonnull final Notification notification, @Nonnull final SequencedSet<BundleAction> actions) {
    if (actions.isEmpty()) {
      throw new NpsServiceException(ErrorCode.NRS_PROCESSING_ERROR, "Received no bundle actions");
    }

    for (final BundleAction action : actions) {
      switch (action.type()) {
        case CREATE_PSEUDONYM_RECORD -> {
          try {
            final boolean wasSuccessful =
                pseudoService.createAndStorePseudonymAndAddToNotification(notification);
            final boolean requiredActionFailed = !wasSuccessful && !action.isOptional();
            if (requiredActionFailed) {
              throw new NpsServiceException(
                  ErrorCode.NRS_PROCESSING_ERROR, "Required action CREATE_PSEUDONYM_RECORD failed");
            }
          } catch (Exception e) {
            throw new NpsServiceException(
                ErrorCode.NRS_PROCESSING_ERROR, "Error occurred during pseudonym creation", e);
          }
        }
        case NO_ACTION -> {}
        case UNKNOWN ->
            throw new NpsServiceException(
                ErrorCode.NRS_PROCESSING_ERROR, "Unknown bundle action encountered");
      }
    }
  }
}

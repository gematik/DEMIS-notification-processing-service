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

import static de.gematik.demis.nps.service.processing.BundleActionType.*;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.demis.nps.base.util.SequencedSets;
import de.gematik.demis.nps.error.NpsServiceException;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.service.pseudonymization.PseudoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BundleActionServiceTest {

  @Mock private PseudoService pseudoService;

  private BundleActionService bundleActionService;

  @BeforeEach
  void setup() {
    bundleActionService = new BundleActionService(pseudoService);
  }

  private static Notification notification() {
    return Notification.builder().build();
  }

  @Test
  void thatPseudoServiceIsCalledForCreatePseudonymRecord() {
    // GIVEN an arbitrary notification
    final Notification notification = notification();
    // AND the pseudo-service returns a success indicator
    when(pseudoService.createAndStorePseudonymAndAddToNotification(eq(notification)))
        .thenReturn(true);

    // WHEN we process the action
    bundleActionService.process(
        notification, SequencedSets.of(BundleAction.requiredOf(CREATE_PSEUDONYM_RECORD)));

    // THEN we can verify the service was called
    verify(pseudoService).createAndStorePseudonymAndAddToNotification(eq(notification));
  }

  @Test
  void thatOptionalActionsFailingCanBeIgnored() {
    // GIVEN an arbitrary notification
    final Notification notification = notification();
    // AND the pseudo-service returns a failure indicator
    when(pseudoService.createAndStorePseudonymAndAddToNotification(eq(notification)))
        .thenReturn(false);

    // WHEN we process the action
    // THEN we catch an exception
    assertThatNoException()
        .isThrownBy(
            () ->
                bundleActionService.process(
                    notification,
                    SequencedSets.of(BundleAction.optionalOf(CREATE_PSEUDONYM_RECORD))));
  }

  @Test
  void thatRequiredActionsExceptionsBubbleUp() {
    doThrow(new RuntimeException())
        .when(pseudoService)
        .createAndStorePseudonymAndAddToNotification(any(Notification.class));
    assertThatExceptionOfType(NpsServiceException.class)
        .isThrownBy(
            () ->
                bundleActionService.process(
                    notification(),
                    SequencedSets.of(BundleAction.requiredOf(CREATE_PSEUDONYM_RECORD))));
  }

  @Test
  void thatNoActionsThrowsError() {
    assertThatExceptionOfType(NpsServiceException.class)
        .isThrownBy(
            () -> {
              bundleActionService.process(notification(), SequencedSets.of());
            });
  }

  @Test
  void thatNoActionIsHandled() {
    assertThatNoException()
        .isThrownBy(
            () ->
                bundleActionService.process(
                    notification(), SequencedSets.of(BundleAction.requiredOf(NO_ACTION))));
  }

  @Test
  void thatUnknownActionAlwaysCausesException() {
    assertThatExceptionOfType(NpsServiceException.class)
        .isThrownBy(
            () ->
                bundleActionService.process(
                    notification(), SequencedSets.of(BundleAction.requiredOf(UNKNOWN))));
    // If the NRS sends an unknown Bundle Action we still need to be optimistic and process it.
    // UNKNOWN means
    // we can never process it, because we don't know it. This indicates a mismatch between NPS and
    // NRS and should
    // therefore always lead to an exception.
    assertThatExceptionOfType(NpsServiceException.class)
        .isThrownBy(
            () ->
                bundleActionService.process(
                    notification(), SequencedSets.of(BundleAction.optionalOf(UNKNOWN))));
  }

  @Test
  void thatMultipleActionsAreHandled() {
    // GIVEN we can successfully create a pseudonym
    when(pseudoService.createAndStorePseudonymAndAddToNotification(any())).thenReturn(true);
    // WHEN we process the list of actions
    // THEN no error is thrown
    assertThatNoException()
        .isThrownBy(
            () ->
                bundleActionService.process(
                    notification(),
                    SequencedSets.of(
                        BundleAction.requiredOf(CREATE_PSEUDONYM_RECORD),
                        BundleAction.requiredOf(NO_ACTION))));
  }
}

package de.gematik.demis.nps.service.processing;

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

import static de.gematik.demis.notification.builder.demis.fhir.notification.types.NotificationCategory.P_7_3;
import static de.gematik.demis.nps.service.notification.Action.ENCRYPT;
import static de.gematik.demis.nps.service.notification.Action.ENCRYPTION;

import com.google.common.collect.Queues;
import de.gematik.demis.notification.builder.demis.fhir.notification.builder.copy.CopyStrategy;
import de.gematik.demis.notification.builder.demis.fhir.notification.builder.copy.CopyStrategyFactory;
import de.gematik.demis.nps.config.NpsConfigProperties;
import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.error.NpsServiceException;
import de.gematik.demis.nps.service.encryption.EncryptionService;
import de.gematik.demis.nps.service.notbyname.NotByNameCreator;
import de.gematik.demis.nps.service.notbyname.NotByNameRegressionService;
import de.gematik.demis.nps.service.notification.Action;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.service.routing.NotificationReceiver;
import de.gematik.demis.nps.service.routing.RoutingData;
import de.gematik.demis.nps.service.validation.BundleValidationResult;
import de.gematik.demis.nps.service.validation.RKIBundleValidator;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.CheckForNull;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Process validated Bundles according to the specified actions from the NRS */
@Service
@Slf4j
public class ReceiverActionService {

  private static final Set<Action> TERMINAL_ACTIONS = Set.of(ENCRYPTION, ENCRYPT);

  private final RKIBundleValidator rkiBundleValidator;
  private final NpsConfigProperties configProperties;
  private final EncryptionService encryptionService;
  private final NotByNameRegressionService notByNameRegressionService;
  private final boolean isProcessing73Enabled;
  private final boolean isNblForNotByNameCreationEnabled;

  public ReceiverActionService(
      final RKIBundleValidator rkiBundleValidator,
      final NpsConfigProperties configProperties,
      final EncryptionService encryptionService,
      final NotByNameRegressionService notByNameRegressionService,
      @Value("${feature.flag.notifications.7_3}") boolean isProcessing73Enabled,
      @Value("${feature.flag.nbl.for.notByName.enabled}")
          boolean isNblForNotByNameCreationEnabled) {
    this.rkiBundleValidator = rkiBundleValidator;
    this.configProperties = configProperties;
    this.encryptionService = encryptionService;
    this.notByNameRegressionService = notByNameRegressionService;
    this.isProcessing73Enabled = isProcessing73Enabled;
    this.isNblForNotByNameCreationEnabled = isNblForNotByNameCreationEnabled;
  }

  /** Attempt to cast the given Resource to {@link Bundle} or return null if that fails */
  @CheckForNull
  private static Bundle castToBundleOrNull(final IBaseResource possibleBundle) {
    if (possibleBundle instanceof Bundle bundle) {
      return bundle;
    }

    return null;
  }

  private static void assertResultIsPresentForRequiredReceiver(
      final NotificationReceiver receiver,
      final String notificationId,
      final Optional<? extends IBaseResource> result) {
    if (receiver.optional()) {
      return;
    }

    if (result.isEmpty()) {
      final String message =
          String.format(
              "No bundle was produced for required receiver '%s' of notification '%s'.",
              receiver.specificReceiverId(), notificationId);
      log.error(message);
      throw new NpsServiceException(ErrorCode.NRS_PROCESSING_ERROR, message);
    }
  }

  /**
   * Verify that no actions remain to be processed.
   *
   * @return {@link Optional#empty()} if receiver is optional and actions haven't been processed
   * @throws IllegalStateException if receiver is required and actions haven't been processed
   */
  private static Optional<? extends IBaseResource> assertAllActionsHaveBeenProcessed(
      final Optional<? extends IBaseResource> intermediateResult,
      final String bundleIdentifier,
      final NotificationReceiver receiver,
      final ArrayDeque<Action> remainingActions) {
    if (remainingActions.isEmpty()) {
      return intermediateResult;
    }

    final String message =
        String.format(
            "Didn't process all actions for receiver '%s' for Notification '%s'. wanted: '%s', remaining: '%s'",
            receiver.specificReceiverId(), bundleIdentifier, receiver.actions(), remainingActions);
    if (receiver.optional()) {
      log.warn(message);
    } else {
      log.error(message);
      throw new NpsServiceException(ErrorCode.NRS_PROCESSING_ERROR, message);
    }
    return Optional.empty();
  }

  private static void logBundleValidationWarn(final String receiver, final String reason) {
    log.warn("Transformed bundle is not valid for receiver '{}'. reason: '{}'", receiver, reason);
  }

  /**
   * Transform the Bundle wrapped by the notification using the Actions and other information taken
   * from the receiver.
   *
   * @param notification
   * @param receiver
   * @return
   */
  public Optional<? extends IBaseResource> transform(
      final Notification notification, final NotificationReceiver receiver) {
    assert73ProcessingAllowed(notification);

    Optional<? extends IBaseResource> intermediateResult = Optional.of(notification.getBundle());
    final ArrayDeque<Action> remainingActions = Queues.newArrayDeque(receiver.actions());
    final String bundleIdentifier = notification.getBundleIdentifier();
    while (!remainingActions.isEmpty()) {
      final Action action = remainingActions.poll();
      if (TERMINAL_ACTIONS.contains(action)) {
        if (Objects.requireNonNull(action) == ENCRYPTION
            || Objects.requireNonNull(action) == ENCRYPT) {
          final Bundle asBundle =
              intermediateResult
                  .map(ReceiverActionService::castToBundleOrNull)
                  .orElseThrow(() -> new IllegalStateException("Can only encrypt bundles"));
          if (isProcessing73Enabled) {
            notification.putPreEncryptedBundle(receiver.specificReceiverId(), asBundle);
          }
          intermediateResult =
              processEncryption(
                  asBundle,
                  receiver.specificReceiverId(),
                  receiver.optional(),
                  notification.isTestUser());
        }
        break;
      }

      // Each of these must produce a new Bundle or something is amiss, e.g. we couldn't create a
      // pseudonym, even though NRS told us to
      final Optional<? extends IBaseResource> possible =
          switch (action) {
            case REPRODUCE, PSEUDO_ORIGINAL -> processPseudoOriginal(notification);
            case PSEUDO_COPY -> processPseudoCopy(notification);
            case ENCRYPTION, ENCRYPT ->
                throw new InternalError("Encryption should terminate processing");
            case NO_ACTION -> intermediateResult;
          };

      if (possible.isPresent()) {
        intermediateResult = possible;
      } else {
        log.error(
            "Action '{}' for Notification '{}' didn't produce a bundle.", action, bundleIdentifier);
        return Optional.empty();
      }
    }

    Optional<? extends IBaseResource> result =
        assertAllActionsHaveBeenProcessed(
            intermediateResult, bundleIdentifier, receiver, remainingActions);
    if (!notification.isTestUser()) {
      result = validateProducedBundle(receiver, result);
    }

    assertResultIsPresentForRequiredReceiver(receiver, bundleIdentifier, result);
    return result;
  }

  private Optional<? extends IBaseResource> processPseudoOriginal(final Notification notification) {
    // Error handling is performed by caller, when no Bundle is created
    return CopyStrategyFactory.getInstance(notification.getBundle()).map(CopyStrategy::copy);
  }

  /** Perform final validation steps on the produced bundle */
  private Optional<? extends IBaseResource> validateProducedBundle(
      final NotificationReceiver receiver, Optional<? extends IBaseResource> intermediateResult) {
    if (intermediateResult.isEmpty()) {
      return intermediateResult;
    }
    final IBaseResource iBaseResource = intermediateResult.get();
    if (iBaseResource instanceof Bundle asBundle) {
      final String receiverId = receiver.specificReceiverId();
      final BundleValidationResult validationResult =
          rkiBundleValidator.isValidBundle(asBundle, receiverId);
      if (!validationResult.isValid()) {
        logBundleValidationWarn(receiverId, validationResult.reason());
        return Optional.empty();
      }
    }

    return intermediateResult;
  }

  private Optional<Bundle> processPseudoCopy(Notification notification) {
    final RoutingData routingInformation = notification.getRoutingData();
    return switch (routingInformation.notificationCategory()) {
      case P_6_1, P_7_1 -> {
        if (!configProperties.anonymizedAllowed()) {
          yield Optional.empty();
        }

        yield Optional.ofNullable(switchBetweenNotByNameCreators(notification));
      }
      default ->
          throw new NpsServiceException(
              ErrorCode.NRS_PROCESSING_ERROR, "Unexpected notification category");
    };
  }

  private Bundle switchBetweenNotByNameCreators(Notification notification) {
    if (isNblForNotByNameCreationEnabled) {
      return NotByNameCreator.createNotByNameBundle(notification);
    }
    return notByNameRegressionService.createNotificationNotByName(notification);
  }

  /**
   * Attempt to encrypt the given notification. If the encryption fails an errors is thrown only if
   * the receiver is mandatory.
   *
   * @param bundle The bundle to encrypt
   * @param receiverId The receiver to encrypt for
   * @param optional defines if encryption is mandatory or optional
   * @return The encrypted binary or empty if encryption failed and the receiver is optional
   * @throws NpsServiceException When encryption fails and the receiver is mandatory.
   */
  private Optional<Binary> processEncryption(
      final Bundle bundle,
      final String receiverId,
      final boolean optional,
      final boolean isTestNotification) {
    if (!isTestNotification) {
      final BundleValidationResult validationResult =
          rkiBundleValidator.isValidBundle(bundle, receiverId);
      if (!validationResult.isValid()) {
        logBundleValidationWarn(receiverId, validationResult.reason());
        return Optional.empty();
      }
    }

    try {
      final Binary encryptedBinaryNotification = encryptionService.encryptFor(bundle, receiverId);
      return Optional.of(encryptedBinaryNotification);
    } catch (NpsServiceException e) {
      if (optional && ErrorCode.HEALTH_OFFICE_CERTIFICATE.name().contentEquals(e.getErrorCode())) {
        log.info(
            "Receiver '{}' has no valid certificate for encryption: {}",
            receiverId,
            e.getLocalizedMessage());
        return Optional.empty();
      }
      throw e; // We are returning this error to the Requester
    }
  }

  /** Throw exception if a 7.3 notification is passed, but the feature is disabled. */
  private void assert73ProcessingAllowed(final Notification notification) {
    final RoutingData routingInformation = notification.getRoutingData();
    if (routingInformation.notificationCategory().equals(P_7_3) && !isProcessing73Enabled) {
      throw new NpsServiceException(
          ErrorCode.UNSUPPORTED_PROFILE, "7.3 notifications can't be processed");
    }
  }
}

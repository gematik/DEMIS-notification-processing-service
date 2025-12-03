package de.gematik.demis.nps.service;

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

import static de.gematik.demis.notification.builder.demis.fhir.notification.types.NotificationCategory.P_7_3;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import de.gematik.demis.fhirparserlibrary.FhirParser;
import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.error.NpsServiceException;
import de.gematik.demis.nps.service.contextenrichment.ContextEnrichmentService;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.service.notification.NotificationFhirService;
import de.gematik.demis.nps.service.notification.NotificationUpdateService;
import de.gematik.demis.nps.service.processing.BundleActionService;
import de.gematik.demis.nps.service.processing.DlsService;
import de.gematik.demis.nps.service.processing.ReceiverActionService;
import de.gematik.demis.nps.service.receipt.ReceiptService;
import de.gematik.demis.nps.service.response.FhirResponseService;
import de.gematik.demis.nps.service.routing.AddressOriginEnum;
import de.gematik.demis.nps.service.routing.NRSRoutingInput;
import de.gematik.demis.nps.service.routing.NotificationReceiver;
import de.gematik.demis.nps.service.routing.RoutingData;
import de.gematik.demis.nps.service.routing.RoutingService;
import de.gematik.demis.nps.service.storage.NotificationStorageService;
import de.gematik.demis.nps.service.validation.InternalOperationOutcome;
import de.gematik.demis.nps.service.validation.LifecycleValidationService;
import de.gematik.demis.nps.service.validation.NotificationValidator;
import java.util.*;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class Processor {

  private final NotificationValidator notificationValidator;
  private final LifecycleValidationService lifecycleValidationService;
  private final NotificationFhirService notificationFhirService;
  private final RoutingService routingService;
  private final NotificationStorageService notificationStorageService;
  private final ReceiptService receiptService;
  private final FhirResponseService responseService;
  private final Statistics statistics;
  private final ContextEnrichmentService contextEnrichmentService;
  private final ReceiverActionService receiverActionService;

  private final NotificationUpdateService updateService;
  private final boolean notificationPreCheck;

  private final FhirParser fhirParser;
  private final boolean isProcessing73Enabled;
  private final BundleActionService bundleActionService;
  private final boolean isPermissionCheckEnabled;
  private final DlsService dlsService;

  public Processor(
      NotificationValidator notificationValidator,
      LifecycleValidationService lifecycleValidationService,
      NotificationFhirService notificationFhirService,
      RoutingService routingService,
      NotificationStorageService notificationStorageService,
      ReceiptService receiptService,
      FhirResponseService responseService,
      Statistics statistics,
      ContextEnrichmentService contextEnrichmentService,
      final ReceiverActionService receiverActionService,
      FhirParser fhirParser,
      final BundleActionService bundleActionService,
      final NotificationUpdateService notificationUpdateService,
      final DlsService dlsService,
      @Value("${feature.flag.notification_pre_check}") boolean notificationPreCheck,
      @Value("${feature.flag.notifications.7_3}") boolean isProcessing73Enabled,
      @Value("${feature.flag.permission.check.enabled}") boolean isPermissionCheckEnabled) {
    this.notificationValidator = notificationValidator;
    this.lifecycleValidationService = lifecycleValidationService;
    this.notificationFhirService = notificationFhirService;
    this.routingService = routingService;
    this.notificationStorageService = notificationStorageService;
    this.receiptService = receiptService;
    this.responseService = responseService;
    this.statistics = statistics;
    this.contextEnrichmentService = contextEnrichmentService;
    this.receiverActionService = receiverActionService;
    this.updateService = notificationUpdateService;
    this.notificationPreCheck = notificationPreCheck;
    this.fhirParser = fhirParser;
    this.isProcessing73Enabled = isProcessing73Enabled;
    this.bundleActionService = bundleActionService;
    this.isPermissionCheckEnabled = isPermissionCheckEnabled;
    this.dlsService = dlsService;
  }

  public Parameters execute(
      final String fhirNotification,
      final MessageType contentType,
      final String requestId,
      final String sender,
      final boolean testUserFlag,
      @Nonnull final String testUserRecipient,
      final String authorization,
      final Set<String> roles) {

    final InternalOperationOutcome internalOperationOutcome =
        validateNotification(fhirNotification, contentType);
    Notification notification =
        buildNotification(
            encodeToJson(fhirNotification, contentType),
            sender,
            testUserFlag,
            testUserRecipient,
            internalOperationOutcome);
    if (!isProcessing73Enabled
        && Objects.equals(notification.getRoutingData().notificationCategory(), P_7_3)) {
      throw new UnsupportedOperationException(
          "7.3 notifications can't be processed due to disabled feature flag");
    }

    if (isPermissionCheckEnabled) {
      final Set<String> allowedRoles = notification.getRoutingData().allowedRoles();
      // Assume that empty allowedRoles means no one is allowed to send
      boolean isMissingRoles = Sets.intersection(roles, allowedRoles).isEmpty();
      if (isMissingRoles) {
        log.info(
            "Principal '{}' needs at least one role of '{}', but only has '{}'",
            sender,
            allowedRoles,
            roles);
        throw new NpsServiceException(
            ErrorCode.MISSING_ROLES, "You don't have the required roles to perform this request");
      }
    }

    lifecycleValidationService.validateLifecycle(notification);

    // cleanup and add (overwrite) some data like timestamp, identifier and sender
    notificationFhirService.cleanAndEnrichNotification(notification, requestId);

    logInfos(notification);

    // process
    setResponsibleHealthOffice(notification);
    setHealthOfficeTags(notification);
    contextEnrichmentService.enrichBundleWithContextInformation(notification, authorization);

    bundleActionService.process(notification, notification.getRoutingData().bundleActions());

    final List<? extends IBaseResource> processedNotifications =
        createModifiedNotifications(notification);
    notificationStorageService.storeNotifications(processedNotifications);
    dlsService.store(notification);

    final Bundle receiptBundle = receiptService.generateReceipt(notification);
    final OperationOutcome validationOutcome = internalOperationOutcome.operationOutcome();
    final Parameters result = responseService.success(receiptBundle, validationOutcome);

    statistics.incSuccessCounter(notification);
    return result;
  }

  @Nonnull
  private Notification buildNotification(
      @Nonnull final String originalNotificationJson,
      @Nonnull final String sender,
      final boolean testUserFlag,
      @Nonnull final String testUserRecipient,
      @Nonnull final InternalOperationOutcome internalOperationOutcome) {
    final String testUserId;
    if (testUserFlag) {
      testUserId = testUserRecipient;
    } else {
      testUserId = "";
    }

    final String validatedJSON =
        Objects.requireNonNullElse(
            internalOperationOutcome.reparsedStringAsJsonWhenRelaxedValidationWasUsed(),
            originalNotificationJson);
    final Bundle parsedBundle =
        fhirParser.parseBundleOrParameter(originalNotificationJson, MessageType.JSON);
    final NRSRoutingInput routingInput =
        new NRSRoutingInput(validatedJSON, testUserFlag, testUserId);
    final RoutingData routingInformation = routingService.getRoutingInformation(routingInput);

    final String diseaseCodeRoot =
        notificationFhirService.getDiseaseCodeRoot(parsedBundle, routingInformation.type());
    final String diseaseCode =
        notificationFhirService.getDiseaseCode(parsedBundle, routingInformation.type());
    return Notification.builder()
        .bundle(parsedBundle)
        .originalNotificationAsJson(originalNotificationJson)
        .sender(sender)
        .diseaseCode(diseaseCode)
        .diseaseCodeRoot(diseaseCodeRoot)
        .testUser(testUserFlag)
        .testUserRecipient(testUserRecipient)
        .routingData(routingInformation)
        .build();
  }

  @Nonnull
  private String encodeToJson(
      @Nonnull final String originalFhirNotification, @Nonnull final MessageType contentType) {
    if (contentType.equals(MessageType.JSON)) {
      return originalFhirNotification;
    } else {
      return fhirParser.encodeToJson(fhirParser.parseFromXml(originalFhirNotification));
    }
  }

  @Nonnull
  private InternalOperationOutcome validateNotification(
      @Nonnull String originalFhirNotification, @Nonnull MessageType contentType) {
    // validate and find Receiver
    if (notificationPreCheck) {
      notificationFhirService.preCheckProfile(originalFhirNotification);
    }

    return notificationValidator.validateFhir(originalFhirNotification, contentType);
  }

  private void logInfos(final Notification notification) {
    log.info(
        "Notification: bundleId={}, type={}, diseaseCode={}, sender={}, testUser={}",
        notification.getBundleIdentifier(),
        notification.getType(),
        notification.getDiseaseCodeRoot(),
        notification.getSender(),
        notification.isTestUser());
  }

  private List<? extends IBaseResource> createModifiedNotifications(
      @Nonnull final Notification notification) {
    final List<NotificationReceiver> routes = notification.getRoutingData().routes();
    final ImmutableListMultimap<String, NotificationReceiver> receiverById =
        Multimaps.index(routes, NotificationReceiver::specificReceiverId);

    Multimap<String, Optional<? extends IBaseResource>> resourceByReceiver =
        Multimaps.transformValues(
            receiverById, receiver -> receiverActionService.transform(notification, receiver));
    resourceByReceiver = Multimaps.filterValues(resourceByReceiver, Optional::isPresent);

    // A receiver that should receive 3 bundles but only receives 2, will not show up here. But a
    // receiver that receives no bundle they are supposed to, will.
    final Sets.SetView<String> receiversWithoutBundle =
        Sets.difference(receiverById.keySet(), resourceByReceiver.keySet());
    for (final String receiverId : receiversWithoutBundle) {
      log.warn(
          "No bundle produced for receiver '{}' of notification '{}'",
          receiverId,
          notification.getBundleIdentifier());
    }

    return resourceByReceiver.values().stream().flatMap(Optional::stream).toList();
  }

  /** <strong>Beware: This method changes the internal state of notification</strong> */
  private void setResponsibleHealthOffice(@Nonnull final Notification notification) {
    final String healthOffice = notification.getRoutingData().responsible();
    updateService.setResponsibleDepartment(notification.getBundle(), healthOffice);
  }

  /** <strong>Beware: This method changes the internal state of notification</strong> */
  private void setHealthOfficeTags(@Nonnull final Notification notification) {
    final Map<AddressOriginEnum, String> healthOffices =
        notification.getRoutingData().healthOffices();
    updateService.addHealthOfficeTags(notification.getBundle(), healthOffices);
  }
}

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
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import static de.gematik.demis.notification.builder.demis.fhir.notification.types.NotificationCategory.P_7_3;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import de.gematik.demis.fhirparserlibrary.FhirParser;
import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.nps.config.NpsConfigProperties;
import de.gematik.demis.nps.config.TestUserConfiguration;
import de.gematik.demis.nps.service.contextenrichment.ContextEnrichmentService;
import de.gematik.demis.nps.service.encryption.EncryptionService;
import de.gematik.demis.nps.service.notbyname.NotByNameService;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.service.notification.NotificationFhirService;
import de.gematik.demis.nps.service.processing.BundleActionService;
import de.gematik.demis.nps.service.processing.ReceiverActionService;
import de.gematik.demis.nps.service.pseudonymization.PseudoService;
import de.gematik.demis.nps.service.receipt.ReceiptService;
import de.gematik.demis.nps.service.response.FhirResponseService;
import de.gematik.demis.nps.service.routing.NRSRoutingInput;
import de.gematik.demis.nps.service.routing.NotificationReceiver;
import de.gematik.demis.nps.service.routing.RoutingOutputDto;
import de.gematik.demis.nps.service.routing.RoutingService;
import de.gematik.demis.nps.service.storage.NotificationStorageService;
import de.gematik.demis.nps.service.validation.NotificationValidator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class Processor {

  private final NpsConfigProperties configProperties;
  private final NotificationValidator notificationValidator;
  private final NotificationFhirService notificationFhirService;
  private final RoutingService routingService;
  private final PseudoService pseudoService;
  private final NotByNameService notByNameService;
  private final EncryptionService encryptionService;
  private final NotificationStorageService notificationStorageService;
  private final ReceiptService receiptService;
  private final FhirResponseService responseService;
  private final Statistics statistics;
  private final ContextEnrichmentService contextEnrichmentService;
  private final ReceiverActionService receiverActionService;

  private final boolean notificationPreCheck;

  private final boolean isProcessing74Enabled;
  private final FhirParser fhirParser;
  private final TestUserConfiguration testUserConfiguration;
  private final boolean isProcessing73Enabled;
  private final BundleActionService bundleActionService;

  public Processor(
      NpsConfigProperties configProperties,
      NotificationValidator notificationValidator,
      NotificationFhirService notificationFhirService,
      RoutingService routingService,
      PseudoService pseudoService,
      NotByNameService notByNameService,
      EncryptionService encryptionService,
      NotificationStorageService notificationStorageService,
      ReceiptService receiptService,
      FhirResponseService responseService,
      Statistics statistics,
      ContextEnrichmentService contextEnrichmentService,
      final ReceiverActionService receiverActionService,
      FhirParser fhirParser,
      TestUserConfiguration testUserConfiguration,
      final BundleActionService bundleActionService,
      @Value("${feature.flag.notification_pre_check}") boolean notificationPreCheck,
      @Value("${feature.flag.notifications.7_4}") boolean isProcessing74Enabled,
      @Value("${feature.flag.notifications.7_3}") boolean isProcessing73Enabled) {
    this.configProperties = configProperties;
    this.notificationValidator = notificationValidator;
    this.notificationFhirService = notificationFhirService;
    this.routingService = routingService;
    this.pseudoService = pseudoService;
    this.notByNameService = notByNameService;
    this.encryptionService = encryptionService;
    this.notificationStorageService = notificationStorageService;
    this.receiptService = receiptService;
    this.responseService = responseService;
    this.statistics = statistics;
    this.contextEnrichmentService = contextEnrichmentService;
    this.receiverActionService = receiverActionService;
    this.testUserConfiguration = testUserConfiguration;
    this.notificationPreCheck = notificationPreCheck;
    this.isProcessing74Enabled = isProcessing74Enabled;
    this.fhirParser = fhirParser;
    this.isProcessing73Enabled = isProcessing73Enabled;
    this.bundleActionService = bundleActionService;
  }

  public Parameters execute(
      final String fhirNotification,
      final MessageType contentType,
      final String requestId,
      final String sender,
      final boolean testUserFlag,
      final String authorization) {

    // once §7.4 or §7.3 Flag is enabled, the new processing will be set as default
    if (isProcessing74Enabled || isProcessing73Enabled) {
      return processWithExtendedNotifications(
          fhirNotification, contentType, requestId, sender, testUserFlag, authorization);
    }

    return processWithCommonNotifications(
        fhirNotification, contentType, requestId, sender, testUserFlag, authorization);
  }

  /**
   * Process the §6.1 and §7.1 Notification using old Routing and Pseudonymization endpoints
   *
   * @param originalFhirNotification
   * @param contentType
   * @param requestId
   * @param sender
   * @param testUserFlag
   * @param authorization
   * @return an instance of {@link Parameters}
   */
  private Parameters processWithCommonNotifications(
      String originalFhirNotification,
      MessageType contentType,
      String requestId,
      String sender,
      boolean testUserFlag,
      String authorization) {
    if (notificationPreCheck) {
      notificationFhirService.preCheckProfile(originalFhirNotification);
    }

    final OperationOutcome validationOutcome =
        notificationValidator.validateFhir(originalFhirNotification, contentType);

    final Notification notification =
        notificationFhirService.read(originalFhirNotification, contentType, sender, testUserFlag);
    notificationFhirService.cleanAndEnrichNotification(notification, requestId);
    logInfos(notification);

    notificationValidator.validateLifecycle(notification);

    routingService.determineHealthOfficesAndAddToNotification(notification);
    pseudoService.createAndStorePseudonymAndAddToNotification(notification);

    contextEnrichmentService.enrichBundleWithContextInformation(notification, authorization);

    forwardNotification(notification);

    final Bundle receiptBundle = receiptService.generateReceipt(notification);
    final Parameters result = responseService.success(receiptBundle, validationOutcome);

    statistics.incSuccessCounter(notification);

    return result;
  }

  /**
   * Process all the known Notification Type (§6.1, §7.1, §7.3, §7.4) using new Routing rules
   *
   * @param originalFhirNotification
   * @param contentType
   * @param requestId
   * @param sender
   * @param testUserFlag
   * @param authorization
   * @return an instance of {@link Parameters}
   */
  private Parameters processWithExtendedNotifications(
      String originalFhirNotification,
      MessageType contentType,
      String requestId,
      String sender,
      boolean testUserFlag,
      String authorization) {
    final Notification notification =
        Notification.builder()
            .originalNotificationAsJson(encodeToJson(originalFhirNotification, contentType))
            .sender(sender)
            .testUser(testUserFlag || testUserConfiguration.isTestUser(sender))
            .build();

    final OperationOutcome validationOutcome =
        validateNotification(originalFhirNotification, contentType, notification);

    logInfos(notification);

    // cleanup
    notificationFhirService.cleanAndEnrichNotification(notification, requestId);

    // process
    routingService.setResponsibleHealthOffice(notification);
    routingService.setHealthOfficeTags(notification);
    contextEnrichmentService.enrichBundleWithContextInformation(notification, authorization);

    final RoutingOutputDto routingData = Objects.requireNonNull(notification.getRoutingOutputDto());
    bundleActionService.process(notification, routingData.bundleActions());

    final List<? extends IBaseResource> processedNotifications =
        createModifiedNotifications(notification, routingData);
    notificationStorageService.storeNotifications(processedNotifications);

    final Bundle receiptBundle = receiptService.generateReceipt(notification);
    final Parameters result = responseService.success(receiptBundle, validationOutcome);

    statistics.incSuccessCounter(notification);
    return result;
  }

  private String encodeToJson(String originalFhirNotification, MessageType contentType) {
    if (contentType.equals(MessageType.JSON)) {
      return originalFhirNotification;
    } else {
      return fhirParser.encodeToJson(fhirParser.parseFromXml(originalFhirNotification));
    }
  }

  private OperationOutcome validateNotification(
      String originalFhirNotification, MessageType contentType, Notification notification) {
    // validate and find Receiver
    if (notificationPreCheck) {
      notificationFhirService.preCheckProfile(originalFhirNotification);
    }

    final OperationOutcome validationOutcome =
        notificationValidator.validateFhir(originalFhirNotification, contentType);

    final NRSRoutingInput routingInput = NRSRoutingInput.from(notification, testUserConfiguration);
    final RoutingOutputDto routingInformation = routingService.getRoutingInformation(routingInput);
    notification.setRoutingOutputDto(routingInformation);
    if (!isProcessing73Enabled
        && notification.getRoutingOutputDto() != null
        && Objects.equals(notification.getRoutingOutputDto().notificationCategory(), P_7_3)) {
      throw new UnsupportedOperationException(
          "7.3 notifications can't be processed due to disabled feature flag");
    }

    notification.setBundle(
        fhirParser.parseBundleOrParameter(originalFhirNotification, contentType));
    notification.setType(notification.getRoutingOutputDto().type());
    notification.setDiseaseCode(
        notificationFhirService.getDiseaseCode(notification.getBundle(), notification.getType()));

    notificationValidator.validateLifecycle(notification);
    return validationOutcome;
  }

  private void logInfos(final Notification notification) {
    log.info(
        "Notification: bundleId={}, type={}, diseaseCode={}, sender={}, testUser={}",
        notification.getBundleIdentifier(),
        notification.getType(),
        notification.getDiseaseCode(),
        notification.getSender(),
        notification.isTestUser());
  }

  private List<? extends IBaseResource> createModifiedNotifications(
      @Nonnull final Notification notification, final @Nonnull RoutingOutputDto routingData) {
    final ImmutableListMultimap<String, NotificationReceiver> receiverById =
        Multimaps.index(routingData.routes(), NotificationReceiver::specificReceiverId);

    Multimap<String, Optional<? extends IBaseResource>> resourceByReceiver =
        Multimaps.transformValues(
            receiverById, (receiver) -> receiverActionService.transform(notification, receiver));
    resourceByReceiver = Multimaps.filterValues(resourceByReceiver, Optional::isPresent);

    // A receiver that should receive 3 bundles but only receives 2, will not show up here. But a
    // receiver that receives
    // no bundle they are supposed to, will.
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

  @Deprecated
  private void forwardNotification(final Notification notification) {
    final Bundle anonymizedNotification =
        configProperties.anonymizedAllowed()
            ? notByNameService.createNotificationNotByName(notification)
            : null;

    final Binary encryptedBinaryNotification =
        encryptionService.encryptForResponsibleHealthOffice(notification);

    final Binary encryptedSubsidiaryNotification =
        shouldSendSubsidiaryNotification(notification)
            ? encryptionService.encryptForSubsidiary(notification).orElse(null)
            : null;

    notificationStorageService.storeNotification(
        encryptedBinaryNotification, encryptedSubsidiaryNotification, anonymizedNotification);
  }

  private boolean shouldSendSubsidiaryNotification(final Notification notification) {
    final Set<String> codes = configProperties.sormasCodes();
    final String diseaseCode = notification.getDiseaseCode();
    return codes != null && diseaseCode != null && codes.contains(diseaseCode);
  }
}

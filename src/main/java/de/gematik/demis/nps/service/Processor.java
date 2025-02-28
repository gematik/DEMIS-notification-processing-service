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
 * #L%
 */

import de.gematik.demis.fhirparserlibrary.FhirParser;
import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.nps.base.profile.DemisSystems;
import de.gematik.demis.nps.config.NpsConfigProperties;
import de.gematik.demis.nps.config.TestUserConfiguration;
import de.gematik.demis.nps.error.NpsServiceException;
import de.gematik.demis.nps.service.contextenrichment.ContextEnrichmentService;
import de.gematik.demis.nps.service.encryption.EncryptionService;
import de.gematik.demis.nps.service.notbyname.NotByNameService;
import de.gematik.demis.nps.service.notification.Action;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.service.notification.NotificationFhirService;
import de.gematik.demis.nps.service.pseudonymization.PseudoService;
import de.gematik.demis.nps.service.receipt.ReceiptService;
import de.gematik.demis.nps.service.response.FhirResponseService;
import de.gematik.demis.nps.service.routing.NRSRoutingInput;
import de.gematik.demis.nps.service.routing.NotificationReceiver;
import de.gematik.demis.nps.service.routing.RoutingService;
import de.gematik.demis.nps.service.storage.NotificationStorageService;
import de.gematik.demis.nps.service.validation.NotificationValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

  private final boolean notificationPreCheck;

  private final boolean isNewProcessing;
  private final FhirParser fhirParser;
  private final TestUserConfiguration testUserConfiguration;

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
      FhirParser fhirParser,
      TestUserConfiguration testUserConfiguration,
      @Value("${feature.flag.notification_pre_check}") boolean notificationPreCheck,
      @Value("${feature.flag.notifications.7_4}") boolean isNewProcessing) {

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
    this.testUserConfiguration = testUserConfiguration;
    this.notificationPreCheck = notificationPreCheck;
    this.isNewProcessing = isNewProcessing;
    this.fhirParser = fhirParser;
  }

  public Parameters execute(
      final String originalFhirNotification,
      final MessageType contentType,
      final String requestId,
      final String sender,
      final boolean testUserFlag,
      final String authorization) {

    if (isNewProcessing) {
      final Notification notification =
          Notification.builder()
              .originalNotificationAsJson(
                  getNotificationAsJson(originalFhirNotification, contentType))
              .sender(sender)
              .testUser(testUserFlag || testUserConfiguration.isTestUser(sender))
              .build();

      final OperationOutcome validationOutcome =
          validateNotification(originalFhirNotification, contentType, notification);

      logInfos(notification);

      // cleanup
      notificationFhirService.cleanAndEnrichNotification(notification, requestId);

      // process
      addInformationsToNotification(authorization, notification);

      final List<IBaseResource> notificationsToForward = createModifiedNotifications(notification);
      notificationStorageService.storeNotifications(notificationsToForward);

      final Bundle receiptBundle = receiptService.generateReceipt(notification);
      final Parameters result = responseService.success(receiptBundle, validationOutcome);

      statistics.incSuccessCounter(notification);
      return result;

    } else {

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
  }

  private String getNotificationAsJson(String originalFhirNotification, MessageType contentType) {
    if (contentType.equals(MessageType.JSON)) {
      return originalFhirNotification;
    } else {
      return fhirParser.encodeToJson(fhirParser.parseFromXml(originalFhirNotification));
    }
  }

  private void addInformationsToNotification(String authorization, Notification notification) {
    routingService.setResponsibleHealthOffice(notification);
    routingService.setHealthOfficeTags(notification);
    pseudoService.createAndStorePseudonymAndAddToNotification(notification);
    contextEnrichmentService.enrichBundleWithContextInformation(notification, authorization);
  }

  private OperationOutcome validateNotification(
      String originalFhirNotification, MessageType contentType, Notification notification) {
    // validate and find Receiver
    if (notificationPreCheck) {
      notificationFhirService.preCheckProfile(originalFhirNotification);
    }

    final OperationOutcome validationOutcome =
        notificationValidator.validateFhir(originalFhirNotification, contentType);

    final NRSRoutingInput request = NRSRoutingInput.from(notification, testUserConfiguration);
    notification.setRoutingOutputDto(routingService.getRoutingInformation(request));

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
        "bundleId={}, type={}, diseaseCode={}, sender={}, testUser={}",
        notification.getBundleIdentifier(),
        notification.getType(),
        notification.getDiseaseCode(),
        notification.getSender(),
        notification.isTestUser());
  }

  private List<IBaseResource> createModifiedNotifications(final Notification notification) {
    final List<IBaseResource> notificationsToForward = new ArrayList<>();
    for (final NotificationReceiver receiver : notification.getRoutingOutputDto().routes()) {
      loopThroughActions(notification, receiver, notificationsToForward);
    }
    String identifier = notification.getBundle().getIdentifier().getValue();
    String system = DemisSystems.RELATED_NOTIFICATION_CODING_SYSTEM;
    String display = "Relates to message with identifier: " + identifier;
    for (IBaseResource notificationBundle : notificationsToForward) {
      // TODO remove with feature.flag.notifications.7_4
      if (notificationBundle.getMeta().getTag(system, identifier) == null) {
        // TODO keep when feature.flag.notifications.7_4 is removed
        notificationBundle
            .getMeta()
            .addTag()
            .setCode(identifier)
            .setDisplay(display)
            .setSystem(system);
      }
    }
    return notificationsToForward;
  }

  private void loopThroughActions(
      Notification notification,
      NotificationReceiver receiver,
      List<IBaseResource> notificationsToForward) {
    for (final Action action : receiver.actions()) {
      switch (action) {
        case ENCRYPTION ->
            enrcyptNotificationForReceiver(notification, receiver, notificationsToForward);
        case PSEUDO_COPY -> createPseudoNotifications(notification, notificationsToForward);

        case NO_ACTION -> notificationsToForward.add(notification.getBundle());

        default -> throw new UnsupportedOperationException();
      }
    }
  }

  private void createPseudoNotifications(
      Notification notification, List<IBaseResource> notificationsToForward) {
    if (!configProperties.anonymizedAllowed()) {
      return;
    }

    final Bundle anonymizedNotification =
        notByNameService.createNotificationNotByName(notification);
    notificationsToForward.add(anonymizedNotification);
  }

  private void enrcyptNotificationForReceiver(
      Notification notification,
      NotificationReceiver receiver,
      List<IBaseResource> notificationsToForward) {
    try {
      final Binary encryptedBinaryNotification =
          encryptionService.encryptFor(notification.getBundle(), receiver.specificReceiverId());
      notificationsToForward.add(encryptedBinaryNotification);
    } catch (NpsServiceException e) {
      log.warn(
          "Error while encrypting notification for receiver {}", receiver.specificReceiverId());
      if (!receiver.optional()) {
        throw e;
      }
    }
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

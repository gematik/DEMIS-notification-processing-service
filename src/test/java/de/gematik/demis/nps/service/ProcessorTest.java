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

import static de.gematik.demis.nps.service.notification.NotificationType.DISEASE;
import static de.gematik.demis.nps.service.processing.BundleActionType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.gematik.demis.fhirparserlibrary.FhirParser;
import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.notification.builder.demis.fhir.notification.types.NotificationCategory;
import de.gematik.demis.nps.base.util.SequencedSets;
import de.gematik.demis.nps.config.NpsConfigProperties;
import de.gematik.demis.nps.service.contextenrichment.ContextEnrichmentService;
import de.gematik.demis.nps.service.encryption.EncryptionService;
import de.gematik.demis.nps.service.notbyname.NotByNameService;
import de.gematik.demis.nps.service.notification.Action;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.service.notification.NotificationFhirService;
import de.gematik.demis.nps.service.notification.NotificationType;
import de.gematik.demis.nps.service.notification.NotificationUpdateService;
import de.gematik.demis.nps.service.processing.BundleAction;
import de.gematik.demis.nps.service.processing.BundleActionService;
import de.gematik.demis.nps.service.processing.ReceiverActionService;
import de.gematik.demis.nps.service.pseudonymization.PseudoService;
import de.gematik.demis.nps.service.receipt.ReceiptService;
import de.gematik.demis.nps.service.response.FhirResponseService;
import de.gematik.demis.nps.service.routing.NRSRoutingInput;
import de.gematik.demis.nps.service.routing.NotificationReceiver;
import de.gematik.demis.nps.service.routing.RoutingData;
import de.gematik.demis.nps.service.routing.RoutingService;
import de.gematik.demis.nps.service.storage.NotificationStorageService;
import de.gematik.demis.nps.service.validation.InternalOperationOutcome;
import de.gematik.demis.nps.service.validation.NotificationValidator;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class ProcessorTest {

  private static final String FHIR_NOTIFICATION = "Just for testing. Content does not matter";
  private static final String REPARSED_FHIR_NOTIFICATION = "someBundleString";
  private static final MessageType CONTENT_TYPE = MessageType.JSON;
  private static final String REQUEST_ID = "1234";
  private static final String SENDER = "Me";
  private static final String TOKEN = "SomeToken";

  @Mock NpsConfigProperties configProperties;
  @Mock NotificationValidator notificationValidator;
  @Mock NotificationFhirService notificationFhirService;
  @Mock RoutingService routingService;
  @Mock PseudoService pseudoService;
  @Mock NotByNameService notByNameCreator;
  @Mock EncryptionService encryptionService;
  @Mock NotificationStorageService notificationStorageService;
  @Mock ReceiptService receiptService;
  @Mock FhirResponseService responseService;
  @Mock ContextEnrichmentService contextEnrichmentService;
  @Mock ReceiverActionService receiverActionService;
  @Mock Statistics statistics;
  @Mock FhirParser fhirParser;
  @Mock NotificationUpdateService updateService;

  /** Help with checked issues on Collections: https://stackoverflow.com/a/5655702 */
  @Captor private ArgumentCaptor<Collection<? extends IBaseResource>> storageParameter;

  private static Notification createNotification(
      final Bundle bundle, final RoutingData routingData) {
    return Notification.builder()
        .bundle(bundle)
        .sender(SENDER)
        .testUser(false)
        .testUserRecipient("")
        .diseaseCode("xxx")
        .originalNotificationAsJson(FHIR_NOTIFICATION)
        .routingData(routingData)
        .build();
  }

  private static <T> ListAppender<ILoggingEvent> listenToLog(final Class<T> clazz) {
    final var log = (Logger) LoggerFactory.getLogger(clazz);
    final var listAppender = new ListAppender<ILoggingEvent>();
    listAppender.start();
    log.addAppender(listAppender);
    return listAppender;
  }

  @ParameterizedTest
  @EnumSource(MessageType.class)
  void ensureProcessOrchestratesTheFlowAsExpected(MessageType contentType) {
    final Processor service = createProcessor();
    // GIVEN a parsed bundle
    final Bundle BUNDLE = new Bundle();
    BUNDLE.setIdentifier(new Identifier().setValue("notification-id"));
    // AND routing information retrieved from the NRS
    final String HEALTH_OFFICE_1 = "1.123.312.3.";
    final String HEALTH_OFFICE_2 = "3.321.123.4.";
    NotificationReceiver receiver1 =
        new NotificationReceiver(
            "responsible_health_office",
            HEALTH_OFFICE_1,
            SequencedSets.of(Action.ENCRYPTION),
            false);
    NotificationReceiver receiver2 =
        new NotificationReceiver(
            "responsible_health_office_sormas",
            HEALTH_OFFICE_2,
            SequencedSets.of(Action.ENCRYPTION),
            false);
    RoutingData routingData =
        new RoutingData(
            DISEASE,
            NotificationCategory.P_6_1,
            SequencedSets.of(BundleAction.optionalOf(CREATE_PSEUDONYM_RECORD)),
            List.of(receiver1, receiver2),
            new LinkedHashMap<>(),
            HEALTH_OFFICE_1);
    // AND a notification that will be created
    final Notification notification = createNotification(BUNDLE, routingData);

    when(routingService.getRoutingInformation(any())).thenReturn(routingData);

    // AND we mock irrelvant services
    OperationOutcome operationOutcome = new OperationOutcome();
    InternalOperationOutcome validationOutcome =
        new InternalOperationOutcome(operationOutcome, "someBundleString");
    when(notificationValidator.validateFhir(any(), any())).thenReturn(validationOutcome);
    when(fhirParser.parseBundleOrParameter(FHIR_NOTIFICATION, MessageType.JSON)).thenReturn(BUNDLE);
    when(notificationFhirService.getDiseaseCode(BUNDLE, DISEASE)).thenReturn("xxx");

    if (contentType == MessageType.XML) {
      when(fhirParser.encodeToJson(any())).thenReturn(FHIR_NOTIFICATION);
    }

    // AND an encrypted Bundle for HEALTH_OFFICE_1 is created
    final Binary ho1_encrypted = new Binary();
    final Binary ho2_encrypted = new Binary();
    // Can't use when().doReturn() due to wildcard capture
    doReturn(Optional.of(ho1_encrypted))
        .when(receiverActionService)
        .transform(any(Notification.class), eq(receiver1));
    doReturn(Optional.of(ho2_encrypted))
        .when(receiverActionService)
        .transform(any(Notification.class), eq(receiver2));

    // AND a receipt is created with the desired outcome
    Bundle receiptBundle = new Bundle();
    when(receiptService.generateReceipt(any())).thenReturn(receiptBundle);
    when(responseService.success(receiptBundle, operationOutcome)).thenReturn(new Parameters());

    // WHEN we process the input an error with stubbing here indicates, that execute creates a
    // different Notification from the createNotification method in this test
    service.execute(FHIR_NOTIFICATION, contentType, REQUEST_ID, SENDER, false, "", TOKEN);

    // THEN
    verify(notificationValidator).validateLifecycle(notification);
    verify(pseudoService).createAndStorePseudonymAndAddToNotification(notification);
    verify(contextEnrichmentService).enrichBundleWithContextInformation(notification, TOKEN);
    verify(notificationStorageService)
        .storeNotifications(
            argThat(
                list ->
                    list.size() == 2
                        && list.contains(ho1_encrypted)
                        && list.contains(ho2_encrypted)));
  }

  private Processor createProcessor() {
    return new Processor(
        notificationValidator,
        notificationFhirService,
        routingService,
        notificationStorageService,
        receiptService,
        responseService,
        statistics,
        contextEnrichmentService,
        receiverActionService,
        fhirParser,
        new BundleActionService(pseudoService),
        updateService,
        false,
        true);
  }

  @Test
  void thatTestUserConfigurationIsCorrectlyAppliedToNRSRequest() {
    // GIVEN a Notification for a test user

    when(routingService.getRoutingInformation(any()))
        .thenReturn(
            new RoutingData(
                NotificationType.LABORATORY,
                NotificationCategory.UNKNOWN,
                SequencedSets.of(BundleAction.requiredOf(NO_ACTION)),
                List.of(),
                Map.of(),
                ""));
    final Bundle result = new Bundle();
    result.setIdentifier(new Identifier().setValue("bundle-id"));
    when(fhirParser.parseBundleOrParameter(FHIR_NOTIFICATION, CONTENT_TYPE)).thenReturn(result);
    when(notificationValidator.validateFhir(any(), any()))
        .thenReturn(new InternalOperationOutcome(new OperationOutcome(), "someBundleString"));

    final Processor processor = createProcessor();
    // WHEN we process a notification
    processor.execute(
        FHIR_NOTIFICATION, CONTENT_TYPE, REQUEST_ID, "test-user", true, "test-user-other", TOKEN);

    final ArgumentCaptor<NRSRoutingInput> routingServiceRequest =
        ArgumentCaptor.forClass(NRSRoutingInput.class);
    verify(routingService).getRoutingInformation(routingServiceRequest.capture());

    final NRSRoutingInput actualRoutingInput = routingServiceRequest.getValue();
    assertThat(actualRoutingInput.isTestUser()).isTrue();
    assertThat(actualRoutingInput.testUserId()).isEqualTo("test-user-other");
  }

  @Test
  void thatMultipleNotificationsForSameRecipientCanBeProcessed() {
    // GIVEN a Notification with two routes for one Recipient
    when(routingService.getRoutingInformation(any()))
        .thenReturn(
            new RoutingData(
                NotificationType.LABORATORY,
                NotificationCategory.UNKNOWN,
                SequencedSets.of(BundleAction.requiredOf(NO_ACTION)),
                List.of(
                    new NotificationReceiver(
                        "any", "1.", SequencedSets.of(Action.NO_ACTION), false),
                    new NotificationReceiver(
                        "any", "1.", SequencedSets.of(Action.NO_ACTION), false)),
                Map.of(),
                ""));
    final Bundle result = new Bundle();
    result.setIdentifier(new Identifier().setValue("bundle-id"));
    when(notificationValidator.validateFhir(any(), any()))
        .thenReturn(new InternalOperationOutcome(new OperationOutcome(), "someBundleString"));

    // WHEN we try to parse the bundle return a placeholder Bundle
    when(fhirParser.parseBundleOrParameter(FHIR_NOTIFICATION, CONTENT_TYPE)).thenReturn(result);
    // AND when the receiverActionService processes anything return a new placeholder Bundle
    doReturn(Optional.of(new Bundle())).when(receiverActionService).transform(any(), any());

    final Processor processor = createProcessor();
    // AND we process a notification
    // TODO come back here and check, this is essentially the RKI test case
    processor.execute(FHIR_NOTIFICATION, CONTENT_TYPE, REQUEST_ID, "1.", true, "1.", TOKEN);

    // THEN ensure that we store the two placeholder Bundles
    verify(notificationStorageService).storeNotifications(storageParameter.capture());

    assertThat(storageParameter.getValue()).hasSize(2);
  }

  @Test
  void checkAdditionalParsingIsUsed() {
    MessageType contentType = MessageType.JSON;
    final Processor service = createProcessor();
    // GIVEN a parsed bundle
    final Bundle BUNDLE = new Bundle();
    BUNDLE.setIdentifier(new Identifier().setValue("notification-id"));
    // AND routing information retrieved from the NRS
    final String HEALTH_OFFICE_1 = "1.123.312.3.";
    final String HEALTH_OFFICE_2 = "3.321.123.4.";
    NotificationReceiver receiver1 =
        new NotificationReceiver(
            "responsible_health_office",
            HEALTH_OFFICE_1,
            SequencedSets.of(Action.ENCRYPTION),
            false);
    NotificationReceiver receiver2 =
        new NotificationReceiver(
            "responsible_health_office_sormas",
            HEALTH_OFFICE_2,
            SequencedSets.of(Action.ENCRYPTION),
            false);
    RoutingData routingOutputDto =
        new RoutingData(
            DISEASE,
            NotificationCategory.P_6_1,
            SequencedSets.of(BundleAction.optionalOf(CREATE_PSEUDONYM_RECORD)),
            List.of(receiver1, receiver2),
            new LinkedHashMap<>(),
            HEALTH_OFFICE_1);
    // AND a notification that will be created
    Notification notification = createNotification(BUNDLE, routingOutputDto);

    when(routingService.getRoutingInformation(any())).thenReturn(routingOutputDto);

    // AND we mock irrelvant services
    OperationOutcome operationOutcome = new OperationOutcome();
    InternalOperationOutcome validationOutcome =
        new InternalOperationOutcome(operationOutcome, "someBundleString");
    when(notificationValidator.validateFhir(any(), any())).thenReturn(validationOutcome);
    when(fhirParser.parseBundleOrParameter(FHIR_NOTIFICATION, contentType)).thenReturn(BUNDLE);
    when(notificationFhirService.getDiseaseCode(BUNDLE, DISEASE)).thenReturn("xxx");

    // AND an encrypted Bundle for HEALTH_OFFICE_1 is created
    final Binary ho1_encrypted = new Binary();
    final Binary ho2_encrypted = new Binary();
    // Can't use when().doReturn() due to wildcard capture
    doReturn(Optional.of(ho1_encrypted))
        .when(receiverActionService)
        .transform(any(Notification.class), eq(receiver1));
    doReturn(Optional.of(ho2_encrypted))
        .when(receiverActionService)
        .transform(any(Notification.class), eq(receiver2));

    // AND a receipt is created with the desired outcome
    Bundle receiptBundle = new Bundle();
    when(receiptService.generateReceipt(any())).thenReturn(receiptBundle);
    when(responseService.success(receiptBundle, operationOutcome)).thenReturn(new Parameters());

    // WHEN we process the input an error with stubbing here indicates, that
    // execute creates a different Notification from the createNotification
    // method in this test
    service.execute(FHIR_NOTIFICATION, contentType, REQUEST_ID, SENDER, false, "", TOKEN);

    // THEN
    verify(notificationValidator).validateLifecycle(notification);
    verify(pseudoService).createAndStorePseudonymAndAddToNotification(notification);
    verify(contextEnrichmentService).enrichBundleWithContextInformation(notification, TOKEN);
    verify(notificationStorageService)
        .storeNotifications(
            argThat(
                list ->
                    list.size() == 2
                        && list.contains(ho1_encrypted)
                        && list.contains(ho2_encrypted)));

    final ArgumentCaptor<NRSRoutingInput> routingServiceRequest =
        ArgumentCaptor.forClass(NRSRoutingInput.class);
    verify(routingService).getRoutingInformation(routingServiceRequest.capture());

    final NRSRoutingInput actualRoutingInput = routingServiceRequest.getValue();
    assertThat(actualRoutingInput.originalNotificationAsJSON())
        .isEqualTo(REPARSED_FHIR_NOTIFICATION);
  }

  @Test
  void logNotificationBundleId() {
    final Bundle bundle = new Bundle();
    bundle.setIdentifier(new Identifier().setValue("notification-id"));

    final var validationOutcome =
        new InternalOperationOutcome(new OperationOutcome(), "someBundleString");
    when(notificationValidator.validateFhir(any(), any())).thenReturn(validationOutcome);
    when(fhirParser.parseBundleOrParameter(any(), any(MessageType.class))).thenReturn(bundle);
    when(routingService.getRoutingInformation(any()))
        .thenReturn(
            new RoutingData(
                NotificationType.LABORATORY,
                NotificationCategory.UNKNOWN,
                SequencedSets.of(BundleAction.requiredOf(NO_ACTION)),
                List.of(),
                Map.of(),
                ""));
    when(notificationFhirService.getDiseaseCode(any(), any())).thenReturn("xxx");

    doAnswer(
            invocation -> {
              final var notification = invocation.getArgument(0, Notification.class);
              notification
                  .getBundle()
                  .setIdentifier(new Identifier().setValue("my-new-generated-uuid"));
              return null;
            })
        .when(notificationFhirService)
        .cleanAndEnrichNotification(any(), any());

    final var listenToLog = listenToLog(Processor.class);

    final Processor service = createProcessor();
    service.execute(FHIR_NOTIFICATION, MessageType.JSON, REQUEST_ID, SENDER, false, "", TOKEN);

    final Optional<ILoggingEvent> logEntry =
        listenToLog.list.stream()
            .filter(event -> Level.INFO.equals(event.getLevel()))
            .filter(event -> event.getMessage().startsWith("Notification:"))
            .findFirst();

    final String expectedLogLine =
        "Notification: bundleId=my-new-generated-uuid, type=LABORATORY, diseaseCode=xxx, sender=Me, testUser=false";
    assertThat(logEntry)
        .isPresent()
        .get()
        .extracting(ILoggingEvent::getFormattedMessage)
        .isEqualTo(expectedLogLine);
  }
}

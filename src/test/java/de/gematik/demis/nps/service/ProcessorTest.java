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

import static de.gematik.demis.nps.error.ErrorCode.HEALTH_OFFICE_CERTIFICATE;
import static de.gematik.demis.nps.service.notification.NotificationType.DISEASE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.demis.fhirparserlibrary.FhirParser;
import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.nps.config.FeatureFlagsConfigProperties;
import de.gematik.demis.nps.config.NpsConfigProperties;
import de.gematik.demis.nps.config.TestUserConfiguration;
import de.gematik.demis.nps.error.NpsServiceException;
import de.gematik.demis.nps.service.contextenrichment.ContextEnrichmentService;
import de.gematik.demis.nps.service.encryption.EncryptionService;
import de.gematik.demis.nps.service.notbyname.NotByNameService;
import de.gematik.demis.nps.service.notification.Action;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.service.notification.NotificationFhirService;
import de.gematik.demis.nps.service.notification.NotificationType;
import de.gematik.demis.nps.service.pseudonymization.PseudoService;
import de.gematik.demis.nps.service.receipt.ReceiptService;
import de.gematik.demis.nps.service.response.FhirResponseService;
import de.gematik.demis.nps.service.routing.NRSRoutingInput;
import de.gematik.demis.nps.service.routing.NotificationReceiver;
import de.gematik.demis.nps.service.routing.RoutingOutputDto;
import de.gematik.demis.nps.service.routing.RoutingService;
import de.gematik.demis.nps.service.storage.NotificationStorageService;
import de.gematik.demis.nps.service.validation.NotificationValidator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessorTest {

  private static final String FHIR_NOTIFICATION = "Just for testing. Content does not matter";
  private static final MessageType CONTENT_TYPE = MessageType.JSON;
  private static final String REQUEST_ID = "1234";
  private static final String SENDER = "Me";
  private static final String TOKEN = "SomeToken";

  @Mock NpsConfigProperties configProperties;
  @Mock FeatureFlagsConfigProperties featureFlagsConfigProperties;
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
  @Mock Statistics statistics;
  @Mock FhirParser fhirParser;

  @Test
  void ensureProcessOrchestratesTheFlowAsExpected() {
    final Processor service = createProcessor();
    // GIVEN a parsed bundle
    final Bundle BUNDLE = new Bundle();
    BUNDLE.setIdentifier(new Identifier().setValue("notification-id"));
    // AND a notification that will be created
    Notification notification = createNotification(BUNDLE);
    // AND routing information retrieved from the NRS
    final String HEALTH_OFFICE_1 = "1.123.312.3.";
    final String HEALTH_OFFICE_2 = "3.321.123.4.";
    NotificationReceiver receiver1 =
        new NotificationReceiver(
            "responsible_health_office", HEALTH_OFFICE_1, Set.of(Action.ENCRYPTION), false);
    NotificationReceiver receiver2 =
        new NotificationReceiver(
            "responsible_health_office_sormas", HEALTH_OFFICE_2, Set.of(Action.ENCRYPTION), false);
    RoutingOutputDto routingOutputDto =
        new RoutingOutputDto(
            DISEASE, "6.1", List.of(receiver1, receiver2), new LinkedHashMap<>(), HEALTH_OFFICE_1);
    notification.setRoutingOutputDto(routingOutputDto);

    when(routingService.getRoutingInformation(any())).thenReturn(routingOutputDto);

    // AND we mock irrelvant services
    OperationOutcome validationOutcome = new OperationOutcome();
    when(notificationValidator.validateFhir(any(), any())).thenReturn(validationOutcome);
    when(fhirParser.parseBundleOrParameter(FHIR_NOTIFICATION, CONTENT_TYPE)).thenReturn(BUNDLE);
    when(notificationFhirService.getDiseaseCode(BUNDLE, DISEASE)).thenReturn("xxx");

    // AND an encrypted Bundle for HEALTH_OFFICE_1 is created
    final Binary ho1_encrypted = new Binary();
    final Binary ho2_encrypted = new Binary();
    when(encryptionService.encryptFor(any(), eq(HEALTH_OFFICE_1))).thenReturn(ho1_encrypted);
    when(encryptionService.encryptFor(any(), eq(HEALTH_OFFICE_2))).thenReturn(ho2_encrypted);

    // AND a receipt is created with the desired outcome
    Bundle receiptBundle = new Bundle();
    when(receiptService.generateReceipt(any())).thenReturn(receiptBundle);
    when(responseService.success(receiptBundle, validationOutcome)).thenReturn(new Parameters());

    // WHEN we process the input
    service.execute(FHIR_NOTIFICATION, CONTENT_TYPE, REQUEST_ID, SENDER, false, TOKEN);

    // THEN
    verify(notificationValidator).validateLifecycle(notification);
    verify(pseudoService).createAndStorePseudonymAndAddToNotification(notification);
    verify(contextEnrichmentService).enrichBundleWithContextInformation(notification, TOKEN);
    verify(notificationStorageService).storeNotifications(List.of(ho1_encrypted, ho2_encrypted));
    verify(encryptionService).encryptFor(any(), eq(HEALTH_OFFICE_2));
  }

  @Test
  void handleOptionalReceiversGracefully() {
    final Processor service = createProcessor();
    // GIVEN a parsed bundle
    final Bundle BUNDLE = new Bundle();
    BUNDLE.setIdentifier(new Identifier().setValue("notification-id"));
    // AND a notification that will be created
    Notification notification = createNotification(BUNDLE);
    // AND routing information retrieved from the NRS
    final String HEALTH_OFFICE_1 = "1.123.312.3.";
    final String HEALTH_OFFICE_2 = "3.321.123.4.";
    NotificationReceiver receiver1 =
        new NotificationReceiver(
            "responsible_health_office", HEALTH_OFFICE_1, Set.of(Action.ENCRYPTION), false);
    NotificationReceiver receiver2 =
        new NotificationReceiver(
            "responsible_health_office_sormas", HEALTH_OFFICE_2, Set.of(Action.ENCRYPTION), true);
    RoutingOutputDto routingOutputDto =
        new RoutingOutputDto(
            DISEASE, "6.1", List.of(receiver1, receiver2), new LinkedHashMap<>(), HEALTH_OFFICE_1);
    notification.setRoutingOutputDto(routingOutputDto);

    when(routingService.getRoutingInformation(any())).thenReturn(routingOutputDto);

    // AND we mock irrelvant services
    OperationOutcome validationOutcome = new OperationOutcome();
    when(notificationValidator.validateFhir(any(), any())).thenReturn(validationOutcome);
    when(fhirParser.parseBundleOrParameter(FHIR_NOTIFICATION, CONTENT_TYPE)).thenReturn(BUNDLE);
    when(notificationFhirService.getDiseaseCode(BUNDLE, DISEASE)).thenReturn("xxx");

    // AND an encrypted Bundle for HEALTH_OFFICE_1 is created
    final Binary ho1_encrypted = new Binary();
    when(encryptionService.encryptFor(any(), eq(HEALTH_OFFICE_1))).thenReturn(ho1_encrypted);
    when(encryptionService.encryptFor(any(), eq(HEALTH_OFFICE_2)))
        .thenThrow(
            new NpsServiceException(
                HEALTH_OFFICE_CERTIFICATE,
                "no certificate found for health office " + HEALTH_OFFICE_2));

    // AND a receipt is created with the desired outcome
    Bundle receiptBundle = new Bundle();
    when(receiptService.generateReceipt(any())).thenReturn(receiptBundle);
    when(responseService.success(receiptBundle, validationOutcome)).thenReturn(new Parameters());

    // WHEN we process the input
    service.execute(FHIR_NOTIFICATION, CONTENT_TYPE, REQUEST_ID, SENDER, false, TOKEN);

    // THEN
    verify(notificationValidator).validateLifecycle(notification);
    verify(pseudoService).createAndStorePseudonymAndAddToNotification(notification);
    verify(contextEnrichmentService).enrichBundleWithContextInformation(notification, TOKEN);
    verify(notificationStorageService).storeNotifications(List.of(ho1_encrypted));
  }

  @Test
  void expectExceptionForNotOptionalReceiverMissing() {
    final Processor service = createProcessor();
    // GIVEN a parsed bundle
    final Bundle BUNDLE = new Bundle();
    BUNDLE.setIdentifier(new Identifier().setValue("notification-id"));
    // AND a notification that will be created
    Notification notification = createNotification(BUNDLE);
    // AND routing information retrieved from the NRS
    final String HEALTH_OFFICE_1 = "1.123.312.3.";
    NotificationReceiver receiver1 =
        new NotificationReceiver(
            "responsible_health_office", HEALTH_OFFICE_1, Set.of(Action.ENCRYPTION), false);
    RoutingOutputDto routingOutputDto =
        new RoutingOutputDto(
            DISEASE, "6.1", List.of(receiver1), new LinkedHashMap<>(), HEALTH_OFFICE_1);
    notification.setRoutingOutputDto(routingOutputDto);

    when(routingService.getRoutingInformation(any())).thenReturn(routingOutputDto);

    // AND we mock irrelvant services
    OperationOutcome validationOutcome = new OperationOutcome();
    when(notificationValidator.validateFhir(any(), any())).thenReturn(validationOutcome);
    when(fhirParser.parseBundleOrParameter(FHIR_NOTIFICATION, CONTENT_TYPE)).thenReturn(BUNDLE);
    when(notificationFhirService.getDiseaseCode(BUNDLE, DISEASE)).thenReturn("xxx");

    // AND an encrypted Bundle for HEALTH_OFFICE_1 is created
    when(encryptionService.encryptFor(any(), eq(HEALTH_OFFICE_1)))
        .thenThrow(
            new NpsServiceException(
                HEALTH_OFFICE_CERTIFICATE,
                "no certificate found for health office " + HEALTH_OFFICE_1));

    // WHEN we process the input
    assertThatThrownBy(
            () ->
                service.execute(FHIR_NOTIFICATION, CONTENT_TYPE, REQUEST_ID, SENDER, false, TOKEN))
        .isInstanceOf(NpsServiceException.class)
        .hasMessage("no certificate found for health office " + HEALTH_OFFICE_1);
  }

  private static Notification createNotification(
      final Bundle bundle, final boolean isTestUser, final String sender) {
    return Notification.builder()
        .bundle(bundle)
        .type(DISEASE)
        .sender(sender)
        .testUser(isTestUser)
        .diseaseCode("xxx")
        .originalNotificationAsJson(FHIR_NOTIFICATION)
        .build();
  }

  private static Notification createNotification(final Bundle bundle) {
    return createNotification(bundle, false, SENDER);
  }

  private Processor createProcessor() {
    return new Processor(
        configProperties,
        notificationValidator,
        notificationFhirService,
        routingService,
        pseudoService,
        notByNameCreator,
        encryptionService,
        notificationStorageService,
        receiptService,
        responseService,
        statistics,
        contextEnrichmentService,
        fhirParser,
        new TestUserConfiguration(List.of("test-user"), "test-1.2.3", true),
        false,
        true);
  }

  @Test
  void thatTestUserIsOnlyReceiverWhenTestUserSetToTrue() {
    // GIVEN a Notification for a test user

    when(routingService.getRoutingInformation(any()))
        .thenReturn(new RoutingOutputDto(NotificationType.LABORATORY, "", List.of(), Map.of(), ""));
    final Bundle result = new Bundle();
    result.setIdentifier(new Identifier().setValue("bundle-id"));
    when(fhirParser.parseBundleOrParameter(FHIR_NOTIFICATION, CONTENT_TYPE)).thenReturn(result);

    final Processor processor = createProcessor();
    // WHEN we process a notification
    processor.execute(FHIR_NOTIFICATION, CONTENT_TYPE, REQUEST_ID, "test-user", true, TOKEN);

    final ArgumentCaptor<NRSRoutingInput> routingServiceRequest =
        ArgumentCaptor.forClass(NRSRoutingInput.class);
    verify(routingService).getRoutingInformation(routingServiceRequest.capture());

    final NRSRoutingInput actualRoutingInput = routingServiceRequest.getValue();
    assertThat(actualRoutingInput.isTestUser()).isTrue();
    assertThat(actualRoutingInput.testUserId()).isEqualTo("test-user");
  }

  @Test
  void thatTestUserIsOnlyReceiverWhenTestUserSetToTrueAndSenderEqualsReceiver() {
    // GIVEN a Notification for a test user

    when(routingService.getRoutingInformation(any()))
        .thenReturn(new RoutingOutputDto(NotificationType.LABORATORY, "", List.of(), Map.of(), ""));
    final Bundle result = new Bundle();
    result.setIdentifier(new Identifier().setValue("bundle-id"));
    when(fhirParser.parseBundleOrParameter(FHIR_NOTIFICATION, CONTENT_TYPE)).thenReturn(result);

    final Processor processor = createProcessor();
    // WHEN we process a notification
    processor.execute(FHIR_NOTIFICATION, CONTENT_TYPE, REQUEST_ID, "non-test-user", true, TOKEN);

    final ArgumentCaptor<NRSRoutingInput> routingServiceRequest =
        ArgumentCaptor.forClass(NRSRoutingInput.class);
    verify(routingService).getRoutingInformation(routingServiceRequest.capture());

    final NRSRoutingInput actualRoutingInput = routingServiceRequest.getValue();
    assertThat(actualRoutingInput.isTestUser()).isTrue();
    assertThat(actualRoutingInput.testUserId()).isEqualTo("test-1.2.3");
  }

  @Test
  void thatTestUserIsOnlyReceiverWhenTestUserSetToTrue3() {
    // GIVEN a Notification for a test user

    when(routingService.getRoutingInformation(any()))
        .thenReturn(new RoutingOutputDto(NotificationType.LABORATORY, "", List.of(), Map.of(), ""));
    final Bundle result = new Bundle();
    result.setIdentifier(new Identifier().setValue("bundle-id"));
    when(fhirParser.parseBundleOrParameter(FHIR_NOTIFICATION, CONTENT_TYPE)).thenReturn(result);

    final Processor processor = createProcessor();
    // WHEN we process a notification
    processor.execute(FHIR_NOTIFICATION, CONTENT_TYPE, REQUEST_ID, "test-user", false, TOKEN);

    final ArgumentCaptor<NRSRoutingInput> routingServiceRequest =
        ArgumentCaptor.forClass(NRSRoutingInput.class);
    verify(routingService).getRoutingInformation(routingServiceRequest.capture());

    final NRSRoutingInput actualRoutingInput = routingServiceRequest.getValue();
    assertThat(actualRoutingInput.isTestUser()).isTrue();
    assertThat(actualRoutingInput.testUserId()).isEqualTo("test-user");
  }

  @Test
  void thatTestUserIsOnlyReceiverWhenTestUserSetToTrueAndSenderIsAllowedReceiver() {
    // GIVEN a Notification for a test user

    when(routingService.getRoutingInformation(any()))
        .thenReturn(new RoutingOutputDto(NotificationType.LABORATORY, "", List.of(), Map.of(), ""));
    final Bundle result = new Bundle();
    result.setIdentifier(new Identifier().setValue("bundle-id"));
    when(fhirParser.parseBundleOrParameter(FHIR_NOTIFICATION, CONTENT_TYPE)).thenReturn(result);

    final Processor processor = createProcessor();
    // WHEN we process a notification
    processor.execute(FHIR_NOTIFICATION, CONTENT_TYPE, REQUEST_ID, "non-test-user", false, TOKEN);

    final ArgumentCaptor<NRSRoutingInput> routingServiceRequest =
        ArgumentCaptor.forClass(NRSRoutingInput.class);
    verify(routingService).getRoutingInformation(routingServiceRequest.capture());

    final NRSRoutingInput actualRoutingInput = routingServiceRequest.getValue();
    assertThat(actualRoutingInput.isTestUser()).isFalse();
    assertThat(actualRoutingInput.testUserId()).isEqualTo("");
  }

  @Test
  void testUserIsOnlyReceiverWhenSenderIsTestUser() {
    // HTTP Header testuser: false BUT x-sender = testuser
  }
}

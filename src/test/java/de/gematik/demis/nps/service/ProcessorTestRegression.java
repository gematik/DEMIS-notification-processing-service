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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.demis.fhirparserlibrary.FhirParser;
import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.nps.config.NpsConfigProperties;
import de.gematik.demis.nps.config.TestUserConfiguration;
import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.error.NpsServiceException;
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
import de.gematik.demis.nps.service.routing.RoutingService;
import de.gematik.demis.nps.service.storage.NotificationStorageService;
import de.gematik.demis.nps.service.validation.NotificationValidator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessorTestRegression {

  private static final String FHIR_NOTIFICATION = "Just for testing. Content does not matter";
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
  @Mock BundleActionService bundleActionService;

  private Processor underTest;

  @BeforeEach
  void setup() {
    underTest =
        new Processor(
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
            receiverActionService,
            fhirParser,
            new TestUserConfiguration(List.of(), "", false),
            bundleActionService,
            false,
            false,
            false);
  }

  @ParameterizedTest
  @CsvSource({"true,xxx", "false,xxx", "false,yyy"})
  void execute(final boolean anonymizedAllowed, final String sormasCode) {
    final String diseaseCode = "xxx";

    when(configProperties.anonymizedAllowed()).thenReturn(anonymizedAllowed);
    when(configProperties.sormasCodes()).thenReturn(Set.of(sormasCode));

    final var operationOutcome = setupValidation();
    final var notification = setupNotificationFhirService(diseaseCode);
    final var encryptedBinaryNotification = setupEncryptResponsibleHealthOffice(notification);
    final var encryptedSubsidiaryNotification =
        sormasCode.equals(diseaseCode) ? setupEncryptSormas(notification) : null;
    final var anonymizedNotification =
        anonymizedAllowed ? setupAnonymizedNotification(notification) : null;
    final var receiptBundle = setupReceiptService(notification);
    final var parameters = setupResponseService(receiptBundle, operationOutcome);

    final Parameters actual =
        underTest.execute(FHIR_NOTIFICATION, CONTENT_TYPE, REQUEST_ID, SENDER, false, TOKEN);

    Assertions.assertEquals(parameters, actual);

    verify(notificationValidator).validateLifecycle(notification);
    verify(routingService).determineHealthOfficesAndAddToNotification(notification);
    verify(pseudoService).createAndStorePseudonymAndAddToNotification(notification);
    verify(contextEnrichmentService).enrichBundleWithContextInformation(notification, TOKEN);
    if (anonymizedAllowed) {
      verify(notByNameCreator).createNotificationNotByName(notification);
    }
    verify(notificationStorageService)
        .storeNotification(
            encryptedBinaryNotification, encryptedSubsidiaryNotification, anonymizedNotification);
  }

  private Bundle setupAnonymizedNotification(final Notification notification) {
    final Bundle anonymizedNotification;
    anonymizedNotification = new Bundle();
    when(notByNameCreator.createNotificationNotByName(notification))
        .thenReturn(anonymizedNotification);
    return anonymizedNotification;
  }

  private Binary setupEncryptSormas(final Notification notification) {
    final Binary encryptedSubsidiaryNotification;
    encryptedSubsidiaryNotification = new Binary();
    when(encryptionService.encryptForSubsidiary(notification))
        .thenReturn(Optional.of(encryptedSubsidiaryNotification));
    return encryptedSubsidiaryNotification;
  }

  private Binary setupEncryptResponsibleHealthOffice(final Notification notification) {
    final var encryptedBinaryNotification = new Binary();
    when(encryptionService.encryptForResponsibleHealthOffice(notification))
        .thenReturn(encryptedBinaryNotification);
    return encryptedBinaryNotification;
  }

  private Parameters setupResponseService(
      final Bundle receiptBundle, final OperationOutcome operationOutcome) {
    final var parameters = new Parameters();
    when(responseService.success(receiptBundle, operationOutcome)).thenReturn(parameters);
    return parameters;
  }

  private Bundle setupReceiptService(final Notification notification) {
    final var receiptBundle = new Bundle();
    when(receiptService.generateReceipt(notification)).thenReturn(receiptBundle);
    return receiptBundle;
  }

  private Notification setupNotificationFhirService(final String diseaseCode) {
    final var bundle = new Bundle().setIdentifier(new Identifier().setValue("1234-5678-9"));
    final var notification = Notification.builder().diseaseCode(diseaseCode).bundle(bundle).build();
    when(notificationFhirService.read(FHIR_NOTIFICATION, CONTENT_TYPE, SENDER, false))
        .thenReturn(notification);
    return notification;
  }

  private OperationOutcome setupValidation() {
    final var operationOutcome = new OperationOutcome();
    when(notificationValidator.validateFhir(FHIR_NOTIFICATION, CONTENT_TYPE))
        .thenReturn(operationOutcome);
    return operationOutcome;
  }

  @Test
  void validationError() {
    final var operationOutcome = new OperationOutcome();
    when(notificationValidator.validateFhir(FHIR_NOTIFICATION, CONTENT_TYPE))
        .thenThrow(new NpsServiceException(ErrorCode.FHIR_VALIDATION_ERROR, operationOutcome));

    final NpsServiceException exception =
        catchThrowableOfType(
            () ->
                underTest.execute(
                    FHIR_NOTIFICATION, CONTENT_TYPE, REQUEST_ID, SENDER, false, TOKEN),
            NpsServiceException.class);

    assertThat(exception)
        .isNotNull()
        .returns(ErrorCode.FHIR_VALIDATION_ERROR.getCode(), NpsServiceException::getErrorCode)
        .returns(operationOutcome, NpsServiceException::getOperationOutcome);
  }
}

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

import static de.gematik.demis.nps.service.notification.Action.ENCRYPTION;
import static de.gematik.demis.nps.service.notification.Action.NO_ACTION;
import static de.gematik.demis.nps.service.notification.Action.PSEUDO_COPY;
import static de.gematik.demis.nps.service.notification.Action.PSEUDO_ORIGINAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.gematik.demis.notification.builder.demis.fhir.notification.builder.infectious.NotifiedPersonByNameDataBuilder;
import de.gematik.demis.notification.builder.demis.fhir.notification.builder.infectious.laboratory.NotificationBundleLaboratoryDataBuilder;
import de.gematik.demis.notification.builder.demis.fhir.notification.builder.infectious.laboratory.NotificationLaboratoryDataBuilder;
import de.gematik.demis.notification.builder.demis.fhir.notification.types.NotificationCategory;
import de.gematik.demis.notification.builder.demis.fhir.notification.utils.DemisConstants;
import de.gematik.demis.notification.builder.demis.fhir.notification.utils.Metas;
import de.gematik.demis.notification.builder.demis.fhir.notification.utils.Patients;
import de.gematik.demis.nps.base.util.SequencedSets;
import de.gematik.demis.nps.config.NpsConfigProperties;
import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.error.NpsServiceException;
import de.gematik.demis.nps.service.encryption.EncryptionService;
import de.gematik.demis.nps.service.notbyname.NotByNameService;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.service.notification.NotificationType;
import de.gematik.demis.nps.service.routing.AddressOriginEnum;
import de.gematik.demis.nps.service.routing.NotificationReceiver;
import de.gematik.demis.nps.service.routing.RoutingData;
import de.gematik.demis.nps.service.validation.RKIBundleValidator;
import de.gematik.demis.nps.test.TestData;
import de.gematik.demis.nps.test.TestUtil;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReceiverActionServiceTest {

  private static final NotificationReceiver RKI_RECEIVER =
      new NotificationReceiver("specific_receiver", "1.", SequencedSets.of(PSEUDO_ORIGINAL), false);
  private static final NotificationReceiver GA_RECEIVER =
      new NotificationReceiver(
          "responsible_health_office", "1.2.3.4", SequencedSets.of(ENCRYPTION), false);
  private static final NotificationReceiver SORMAS_RECEIVER =
      new NotificationReceiver(
          "responsible_health_office_sormas", "2.2.3.4", SequencedSets.of(ENCRYPTION), true);

  private static final RoutingData P73_ROUTING =
      new RoutingData(
          NotificationType.LABORATORY,
          NotificationCategory.P_7_3,
          SequencedSets.of(),
          List.of(),
          Map.of(),
          "no one");
  private static final RoutingData P73_ROUTING_WITH_TERMINAL_ACTION =
      new RoutingData(
          NotificationType.LABORATORY,
          NotificationCategory.P_7_3,
          SequencedSets.of(),
          List.of(),
          Map.of(),
          "no one");
  private static final RoutingData ROUTING =
      new RoutingData(
          NotificationType.LABORATORY,
          NotificationCategory.P_7_1,
          SequencedSets.of(),
          List.of(RKI_RECEIVER, GA_RECEIVER, SORMAS_RECEIVER),
          Map.of(
              AddressOriginEnum.NOTIFIED_PERSON_PRIMARY,
              "1.2.3.4",
              AddressOriginEnum.SUBMITTER,
              "1.2.3.4"),
          "1.2.3.4");

  @Mock private NotByNameService notByNameService;
  @Mock private EncryptionService encryptionService;
  @Mock private NpsConfigProperties npsConfigProperties;

  private final RKIBundleValidator rkiBundleValidator = new RKIBundleValidator();

  private ReceiverActionService receiverActionService;

  @BeforeEach
  void setup() {
    receiverActionService =
        new ReceiverActionService(
            rkiBundleValidator, npsConfigProperties, encryptionService, notByNameService, true);
  }

  private static Notification fromJSON(final String path, final RoutingData routingData) {
    final Bundle original = TestData.getBundle(path);
    return Notification.builder()
        .originalNotificationAsJson(TestData.readResourceAsString(path))
        .diseaseCode("xxx")
        .sender("Me")
        .bundle(original)
        .routingData(routingData)
        .build();
  }

  @Test
  void thatDisabled73FeatureFlagCausesExceptions() {
    // GIVEN a 7.3 bundle
    final Notification notification =
        fromJSON("/bundles/7_3/laboratory-nonnominal-notifiedperson.json", P73_ROUTING);
    // AND the feature toggle is disabled
    final ReceiverActionService withDisabledToggle =
        new ReceiverActionService(
            new RKIBundleValidator(),
            npsConfigProperties,
            encryptionService,
            notByNameService,
            false);
    // WHEN bundle is processed
    final NpsServiceException npsServiceException =
        catchThrowableOfType(
            NpsServiceException.class,
            () ->
                withDisabledToggle.transform(
                    notification,
                    new NotificationReceiver("", "1.", SequencedSets.of(NO_ACTION), false)));
    // THEN
    assertThat(npsServiceException).isNotNull();
    assertThat(npsServiceException.getErrorCode())
        .isEqualTo(ErrorCode.UNSUPPORTED_PROFILE.toString());
  }

  @Test
  void thatNotifiedPersonFor73IsReplacedWithNotByNameUrnUuid() {
    // GIVEN a 7.3 bundle
    final Notification notification =
        fromJSON("/bundles/7_3/laboratory-nonnominal-notifiedperson-urn-uuid.json", P73_ROUTING);
    // WHEN bundle is processed
    final Optional<? extends IBaseResource> transform =
        receiverActionService.transform(
            notification,
            new NotificationReceiver("", "1.", SequencedSets.of(PSEUDO_ORIGINAL), false));
    // THEN
    assertThat(transform).containsInstanceOf(Bundle.class);
    final Bundle transformationResult = (Bundle) transform.orElseThrow();
    final Optional<Patient> patient = Patients.subjectFrom(transformationResult);
    assertThat(patient).isNotEmpty();
    final Set<String> strings = Metas.profilesFrom(patient.orElseThrow());
    assertThat(strings).containsExactly(DemisConstants.PROFILE_NOTIFIED_PERSON_NOT_BY_NAME);

    // DEMIS-3168: ensure that we can process urn:uuid: ids
    assertThat(transformationResult.getEntry())
        .allSatisfy(e -> assertThat(e.getFullUrl()).startsWith("urn:uuid:"));
  }

  @ValueSource(
      strings = {
        "/bundles/7_3/laboratory-nonnominal-notifiedperson.json",
        "/bundles/7_3/disease-nonnominal-notifiedperson.json"
      })
  @ParameterizedTest
  void thatNotifiedPersonFor73IsReplacedWithNotByName(@Nonnull final String bundlePath) {
    // GIVEN a 7.3 bundle
    final Notification notification = fromJSON(bundlePath, P73_ROUTING);
    // WHEN bundle is processed
    final Optional<? extends IBaseResource> transform =
        receiverActionService.transform(
            notification,
            new NotificationReceiver("", "1.", SequencedSets.of(PSEUDO_ORIGINAL), false));
    // THEN
    assertThat(transform).containsInstanceOf(Bundle.class);
    final Optional<Patient> patient = Patients.subjectFrom((Bundle) transform.orElseThrow());
    assertThat(patient).isNotEmpty();
    final Set<String> strings = Metas.profilesFrom(patient.orElseThrow());
    assertThat(strings).containsExactly(DemisConstants.PROFILE_NOTIFIED_PERSON_NOT_BY_NAME);
  }

  @Test
  void thatGrosslyMisconfiguredNotificationFor73WontLeakPersonalDataToRKI() {
    // GIVEN a 7.3 bundle
    final Notification notification = nonnominalNotifiedPersonNotification(false);
    // WHEN bundle is processed
    final Optional<? extends IBaseResource> transform =
        receiverActionService.transform(
            notification, new NotificationReceiver("", "1.", SequencedSets.of(NO_ACTION), true));
    // THEN
    assertThat(transform).isEmpty();
  }

  @Test
  void thatTestNotificationsAreNotValidatedForRKI() {
    // GIVEN any bundle with a notified person
    final Notification notification = nonnominalNotifiedPersonNotification(true);
    // WHEN bundle is processed
    final RKIBundleValidator rkiBundleValidatorMock = mock(RKIBundleValidator.class);
    final ReceiverActionService service =
        new ReceiverActionService(
            rkiBundleValidatorMock, npsConfigProperties, encryptionService, notByNameService, true);
    service.transform(
        notification, new NotificationReceiver("", "1.", SequencedSets.of(NO_ACTION), true));
    // THEN
    verifyNoInteractions(rkiBundleValidatorMock);
  }

  @Test
  void thatEncryptedTestNotificationsAreNotValidatedForRKI() {
    /*
    At the time of writing this we perform bundle validation to verify the RKI never receives a notified person.
    1. After processing all actions, verify the final result doesn't contain a notified person.
    2. Before encrypting a bundle we, verify we don't accidentally encrypt a notified person for the RKI.

    After encrypting, we can't validate the resulting binary(it's encrypted!). So we always have to check before.
    However, for test notifications this is not desired. We want the RKI to be able to receive these test bundles.
     */

    // GIVEN any bundle with a notified person
    // AND we want to encrypt that bundle
    final Notification notification = nonnominalNotifiedPersonNotification(true);
    // AND we can encrypt anything and return a placeholder
    when(encryptionService.encryptFor(any(), eq("1."))).thenReturn(new Binary());

    // WHEN bundle is processed
    final RKIBundleValidator rkiBundleValidatorMock = mock(RKIBundleValidator.class);
    final ReceiverActionService service =
        new ReceiverActionService(
            rkiBundleValidatorMock, npsConfigProperties, encryptionService, notByNameService, true);
    service.transform(
        notification, new NotificationReceiver("", "1.", SequencedSets.of(ENCRYPTION), true));
    // THEN
    verifyNoInteractions(rkiBundleValidatorMock);
  }

  private static Notification nonnominalNotifiedPersonNotification(
      final boolean isTestNotification) {
    final Bundle original =
        TestData.getBundle("/bundles/7_3/laboratory-nonnominal-notifiedperson.json");
    return Notification.builder()
        .testUser(isTestNotification)
        .testUserRecipient("1.")
        // AND a 7.3 bundle with NotifiedPerson
        .originalNotificationAsJson(
            TestData.readResourceAsString("/bundles/7_3/laboratory-nonnominal-notifiedperson.json"))
        .diseaseCode("xxx")
        .sender("Me")
        .bundle(original)
        // AND a matching routing output
        .routingData(
            new RoutingData(
                NotificationType.LABORATORY,
                NotificationCategory.UNKNOWN,
                SequencedSets.of(),
                List.of(),
                Map.of(),
                "noone"))
        .build();
  }

  @Test
  void thatCreatePseudoCopyReplacesNotifiedPerson() {
    // GIVEN a 7.1 bundle
    final Notification notification = p71Notification();
    // AND anonymizedAllowed() = true
    when(npsConfigProperties.anonymizedAllowed()).thenReturn(true);
    when(notByNameService.createNotificationNotByName(notification))
        .thenReturn(new Bundle()); // just making sure we don't get an NPE

    // WHEN bundle is processed
    receiverActionService.transform(
        notification, new NotificationReceiver("", "1.", SequencedSets.of(PSEUDO_COPY), false));

    // THEN
    verify(notByNameService).createNotificationNotByName(notification);
  }

  @Test
  void thatFailedEncryptionForRequiredReceiverThrowsException() {
    // GIVEN a 7.1 bundle
    final Notification notification = p71Notification();

    // And a terminal action followed by a regular one
    final NotificationReceiver receiver =
        new NotificationReceiver("", "1.2.3.4", SequencedSets.of(ENCRYPTION), false);
    when(encryptionService.encryptFor(any(), eq("1.2.3.4")))
        .thenThrow(new NpsServiceException(ErrorCode.ENCRYPTION, ""));

    // WHEN bundle is processed
    final NpsServiceException npsException =
        catchThrowableOfType(
            NpsServiceException.class,
            () -> receiverActionService.transform(notification, receiver));

    // THEN
    assertThat(npsException).isNotNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/bundles/7_3/laboratory-nonnominal-notbyname.json",
        "/bundles/7_3/disease-nonnominal-notbyname.json",
      })
  void thatPseudoOriginalCopiesBundleFor73ForRegulatoryReasons(@Nonnull final String bundlePath) {
    // GIVEN a 7.3 bundle that in theory doesn't have to be transformed, because all Resources are
    // correct
    final Notification notification = fromJSON(bundlePath, P73_ROUTING);
    // WHEN bundle is processed
    final Optional<? extends IBaseResource> transform =
        receiverActionService.transform(
            notification,
            new NotificationReceiver("", "1.", SequencedSets.of(PSEUDO_ORIGINAL), false));
    // THEN we still get another instance, because we copied the original
    assertThat(transform).containsInstanceOf(Bundle.class);
    assertThat(transform.orElseThrow()).isNotEqualTo(notification.getBundle());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/bundles/7_3/laboratory-nonnominal-notbyname.json",
        "/bundles/7_3/disease-nonnominal-notbyname.json",
      })
  void thatPreEncryptedBundlesAreAdded(@Nonnull final String bundlePath) {
    when(encryptionService.encryptFor(any(), eq("1."))).thenReturn(new Binary());
    final Notification notification = fromJSON(bundlePath, P73_ROUTING);
    final Optional<? extends IBaseResource> transform =
        receiverActionService.transform(
            notification, new NotificationReceiver("", "1.", SequencedSets.of(ENCRYPTION), false));

    assertThat(notification.getPreEncryptedBundles()).hasSize(1);
  }

  @Test
  void ensureAnonymous73AreProcessed() {
    // GIVEN a 7.3 bundle that in theory doesn't have to be transformed, because all Resources are
    // correct
    final Notification notification =
        fromJSON("/bundles/7_3/laboratory-anonymous.json", P73_ROUTING);
    // WHEN bundle is processed
    final Optional<? extends IBaseResource> transform =
        receiverActionService.transform(
            notification,
            new NotificationReceiver("", "1.", SequencedSets.of(PSEUDO_ORIGINAL), false));
    // THEN
    assertThat(transform).containsInstanceOf(Bundle.class);
    // AND a copy of the original bundle was created
    assertThat(transform.orElseThrow()).isNotEqualTo(notification.getBundle());
  }

  @Test
  void thatUnprocessedActionsCauseExceptionForRequiredReceiver() {
    // GIVEN a 7.1 bundle
    final Notification notification = p71Notification();

    // And a terminal action followed by a regular one
    final NotificationReceiver receiver =
        new NotificationReceiver("", "1.2.3.4", SequencedSets.of(ENCRYPTION, PSEUDO_COPY), false);
    when(encryptionService.encryptFor(any(), eq("1.2.3.4"))).thenReturn(new Binary()); // avoid NPE

    // WHEN bundle is processed
    final NpsServiceException npsServiceException =
        catchThrowableOfType(
            NpsServiceException.class,
            () -> receiverActionService.transform(notification, receiver));

    // THEN
    assertThat(npsServiceException).isNotNull();
    assertThat(npsServiceException.getErrorCode())
        .isEqualTo(ErrorCode.NRS_PROCESSING_ERROR.toString());
  }

  @Test
  void thatUnprocessedActionsCauseEmptyResultForOptionalReceiver() {
    // GIVEN a 7.1 bundle
    final Notification notification = p71Notification();

    // And a terminal action followed by a regular one
    final NotificationReceiver receiver =
        new NotificationReceiver("", "1.2.3.4", SequencedSets.of(ENCRYPTION, PSEUDO_COPY), true);
    when(encryptionService.encryptFor(any(), eq("1.2.3.4"))).thenReturn(new Binary()); // avoid NPE

    // WHEN bundle is processed
    final Optional<? extends IBaseResource> transform =
        receiverActionService.transform(notification, receiver);

    // THEN
    assertThat(transform).isEmpty();
  }

  @Test
  void thatOptionalReceiverTriggersNoExceptionOnMissingCertificate() {
    // GIVEN a 7.4 bundle
    final Notification notification = p71Notification();

    when(encryptionService.encryptFor(any(), eq("1.2.3.4"))).thenReturn(new Binary());
    when(encryptionService.encryptFor(any(), eq("2.2.3.4")))
        .thenThrow(
            new NpsServiceException(ErrorCode.HEALTH_OFFICE_CERTIFICATE, "Certificate missing"));

    // WHEN bundle is processed for Health Office
    Optional<? extends IBaseResource> transform =
        receiverActionService.transform(notification, GA_RECEIVER);

    // THEN
    assertThat(transform).isNotEmpty();

    // AND WHEN bundle is processed for SORMAS
    transform = receiverActionService.transform(notification, SORMAS_RECEIVER);
    // THEN no binary is generated
    assertThat(transform).isEmpty();
  }

  private static Notification p71Notification() {
    final Patient notifiedPerson = new NotifiedPersonByNameDataBuilder().setId("1").build();
    final Composition compositionWithNotifiedPerson =
        new NotificationLaboratoryDataBuilder()
            .setDefault()
            .setNotifiedPerson(notifiedPerson)
            .build();
    final Bundle bundle =
        new NotificationBundleLaboratoryDataBuilder()
            .setDefaults()
            .setNotificationLaboratory(compositionWithNotifiedPerson)
            .build();

    final String originalJSON = TestUtil.fhirResourceToJson(bundle);
    return Notification.builder()
        .originalNotificationAsJson(originalJSON)
        .diseaseCode("xxx")
        .sender("Me")
        .bundle(bundle)
        .routingData(ROUTING)
        .build();
  }
}

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

import static de.gematik.demis.nps.service.notification.Action.ENCRYPTION;
import static de.gematik.demis.nps.service.notification.Action.NO_ACTION;
import static de.gematik.demis.nps.service.notification.Action.PSEUDO_COPY;
import static de.gematik.demis.nps.service.notification.Action.PSEUDO_ORIGINAL;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.gematik.demis.notification.builder.demis.fhir.notification.builder.infectious.NotifiedPersonNominalDataBuilder;
import de.gematik.demis.notification.builder.demis.fhir.notification.builder.infectious.laboratory.NotificationBundleLaboratoryDataBuilder;
import de.gematik.demis.notification.builder.demis.fhir.notification.builder.infectious.laboratory.NotificationLaboratoryDataBuilder;
import de.gematik.demis.notification.builder.demis.fhir.notification.types.NotificationCategory;
import de.gematik.demis.notification.builder.demis.fhir.notification.utils.DemisConstants;
import de.gematik.demis.notification.builder.demis.fhir.notification.utils.Metas;
import de.gematik.demis.notification.builder.demis.fhir.notification.utils.Patients;
import de.gematik.demis.notification.builder.demis.fhir.notification.utils.Utils;
import de.gematik.demis.nps.base.util.SequencedSets;
import de.gematik.demis.nps.base.util.UuidGenerator;
import de.gematik.demis.nps.config.NpsConfigProperties;
import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.error.NpsServiceException;
import de.gematik.demis.nps.service.encryption.EncryptionService;
import de.gematik.demis.nps.service.notbyname.NotByNameRegressionService;
import de.gematik.demis.nps.service.notbyname.PatientResourceTransformer;
import de.gematik.demis.nps.service.notification.Action;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.service.notification.NotificationType;
import de.gematik.demis.nps.service.routing.AddressOriginEnum;
import de.gematik.demis.nps.service.routing.NotificationReceiver;
import de.gematik.demis.nps.service.routing.RoutingData;
import de.gematik.demis.nps.service.validation.BundleValidationResult;
import de.gematik.demis.nps.service.validation.RKIBundleValidator;
import de.gematik.demis.nps.test.RoutingDataUtil;
import de.gematik.demis.nps.test.TestData;
import de.gematik.demis.nps.test.TestUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
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
          "no one",
          Set.of(),
          null);
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
          "1.2.3.4",
          Set.of(),
          null);
  private static final RoutingData DIS_ROUTING =
      new RoutingData(
          NotificationType.DISEASE,
          NotificationCategory.P_6_1,
          SequencedSets.of(),
          List.of(RKI_RECEIVER, GA_RECEIVER, SORMAS_RECEIVER),
          Map.of(
              AddressOriginEnum.NOTIFIED_PERSON_PRIMARY,
              "1.2.3.4",
              AddressOriginEnum.SUBMITTER,
              "1.2.3.4"),
          "1.2.3.4",
          Set.of(),
          null);

  @Mock private NotByNameRegressionService notByNameRegressionService;
  @Mock private EncryptionService encryptionService;
  @Mock private NpsConfigProperties npsConfigProperties;

  private final RKIBundleValidator rkiBundleValidator = new RKIBundleValidator();

  private ReceiverActionService receiverActionService;

  @Nested
  @DisplayName("regression version for creating notByName Excerpts")
  class RegressionVersionForCreation {

    @BeforeEach
    void setup() {
      receiverActionService =
          new ReceiverActionService(
              rkiBundleValidator,
              npsConfigProperties,
              encryptionService,
              notByNameRegressionService,
              true,
              false);
    }

    private static Notification fromJSON(final String path, final RoutingData routingData) {
      final Bundle original = TestData.getBundle(path);
      return Notification.builder()
          .originalNotificationAsJson(TestData.readResourceAsString(path))
          .diseaseCodeRoot("xxx")
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
              notByNameRegressionService,
              false,
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
              rkiBundleValidatorMock,
              npsConfigProperties,
              encryptionService,
              notByNameRegressionService,
              true,
              false);
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
              rkiBundleValidatorMock,
              npsConfigProperties,
              encryptionService,
              notByNameRegressionService,
              true,
              false);
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
              TestData.readResourceAsString(
                  "/bundles/7_3/laboratory-nonnominal-notifiedperson.json"))
          .diseaseCodeRoot("xxx")
          .sender("Me")
          .bundle(original)
          // AND a matching routing output
          .routingData(RoutingDataUtil.emptyFor("noone"))
          .build();
    }

    @Test
    void thatCreatePseudoCopyReplacesNotifiedPerson() {
      // GIVEN a 7.1 bundle
      final Notification notification = p71Notification();
      // AND anonymizedAllowed() = true
      when(npsConfigProperties.anonymizedAllowed()).thenReturn(true);
      when(notByNameRegressionService.createNotificationNotByName(notification))
          .thenReturn(new Bundle()); // just making sure we don't get an NPE

      // WHEN bundle is processed
      receiverActionService.transform(
          notification, new NotificationReceiver("", "1.", SequencedSets.of(PSEUDO_COPY), false));

      // THEN
      verify(notByNameRegressionService).createNotificationNotByName(notification);
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
      when(encryptionService.encryptFor(any(), eq("1.2.3.4")))
          .thenReturn(new Binary()); // avoid NPE

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
      when(encryptionService.encryptFor(any(), eq("1.2.3.4")))
          .thenReturn(new Binary()); // avoid NPE

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
  }

  @Nested
  @DisplayName("NBL version for creating notByName Excerpts")
  class TestsForNBLNotByNameCreation {

    @BeforeEach
    void setup() {
      receiverActionService =
          new ReceiverActionService(
              rkiBundleValidator,
              npsConfigProperties,
              encryptionService,
              notByNameRegressionService,
              true,
              true);
    }

    private static Notification fromJSON(final String path, final RoutingData routingData) {
      final Bundle original = TestData.getBundle(path);
      return Notification.builder()
          .originalNotificationAsJson(TestData.readResourceAsString(path))
          .diseaseCodeRoot("xxx")
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
              notByNameRegressionService,
              false,
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
              rkiBundleValidatorMock,
              npsConfigProperties,
              encryptionService,
              notByNameRegressionService,
              true,
              false);
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
              rkiBundleValidatorMock,
              npsConfigProperties,
              encryptionService,
              notByNameRegressionService,
              true,
              false);
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
              TestData.readResourceAsString(
                  "/bundles/7_3/laboratory-nonnominal-notifiedperson.json"))
          .diseaseCodeRoot("xxx")
          .sender("Me")
          .bundle(original)
          // AND a matching routing output
          .routingData(RoutingDataUtil.emptyFor("noone"))
          .build();
    }

    @Test
    void shouldCreateNotByNameVersionOfLabNotification() throws IOException {
      // GIVEN a 7.1 bundle
      final Notification notification = getLabNotification();
      // AND anonymizedAllowed() = true
      when(npsConfigProperties.anonymizedAllowed()).thenReturn(true);

      // WHEN bundle is processed
      Optional<? extends IBaseResource> transform =
          receiverActionService.transform(
              notification,
              new NotificationReceiver("", "1.", SequencedSets.of(PSEUDO_COPY), false));

      // THEN
      assertThat(transform).isNotEmpty();
      IBaseResource resource = transform.get();
      assertThat(resource).isInstanceOf(Bundle.class);
      Bundle bundle = (Bundle) resource;
      IBaseResource first = bundle.getEntry().getFirst().getResource();
      assertThat(first).isInstanceOf(Composition.class);
      Composition composition = (Composition) first;
      assertThat(
              composition.getSubject().getResource().getMeta().getProfile().getFirst().getValue())
          .isEqualTo("https://demis.rki.de/fhir/StructureDefinition/NotifiedPersonNotByName");
    }

    @Test
    void shouldCreateNotByNameVersionOfDisNotification() throws IOException {
      // GIVEN a 7.1 bundle
      final Notification notification = getDisNotification();
      // AND anonymizedAllowed() = true
      when(npsConfigProperties.anonymizedAllowed()).thenReturn(true);

      // WHEN bundle is processed
      Optional<? extends IBaseResource> transform =
          receiverActionService.transform(
              notification,
              new NotificationReceiver("", "1.", SequencedSets.of(PSEUDO_COPY), false));

      // THEN
      assertThat(transform).isNotEmpty();
      IBaseResource resource = transform.get();
      assertThat(resource).isInstanceOf(Bundle.class);
      Bundle bundle = (Bundle) resource;
      IBaseResource first = bundle.getEntry().getFirst().getResource();
      assertThat(first).isInstanceOf(Composition.class);
      Composition composition = (Composition) first;
      assertThat(
              composition.getSubject().getResource().getMeta().getProfile().getFirst().getValue())
          .isEqualTo("https://demis.rki.de/fhir/StructureDefinition/NotifiedPersonNotByName");
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
      when(encryptionService.encryptFor(any(), eq("1.2.3.4")))
          .thenReturn(new Binary()); // avoid NPE

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
      when(encryptionService.encryptFor(any(), eq("1.2.3.4")))
          .thenReturn(new Binary()); // avoid NPE

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
  }

  @Test
  void nblShouldProduceEqualLaboratoryNotificationExcerptAsLocalCode() throws IOException {
    try (MockedStatic<Utils> utilities = mockStatic(Utils.class)) {
      utilities.when(Utils::generateUuidString).thenReturn("someUuid");
      utilities.when(() -> Utils.getShortReferenceOrUrnUuid(any())).thenCallRealMethod();

      // given a 7.1 Bundle
      String json =
          Files.readString(
              Path.of(
                  "src/test/resources/bundles/LaboratoryNotificationTestcaseForNotByNameExcerpt.json"));
      Bundle bundle = FhirContext.forR4Cached().newJsonParser().parseResource(Bundle.class, json);

      RKIBundleValidator rkiBundleValidatorLocal = mock((RKIBundleValidator.class));
      when(rkiBundleValidatorLocal.isValidBundle(any(Bundle.class), anyString()))
          .thenReturn(new BundleValidationResult(true, null));
      NpsConfigProperties npsConfigPropertiesLocal = mock(NpsConfigProperties.class);
      when(npsConfigPropertiesLocal.anonymizedAllowed()).thenReturn(true);
      EncryptionService encryptionServiceLocal = mock(EncryptionService.class);
      PatientResourceTransformer patientResourceTransformerLocal = new PatientResourceTransformer();
      FhirContext fhirContext = FhirContext.forR4Cached();
      UuidGenerator uuidGenerator = mock(UuidGenerator.class);
      when(uuidGenerator.generateUuid()).thenReturn("someUuid");
      NotByNameRegressionService notByNameRegressionServiceLocal =
          new NotByNameRegressionService(
              patientResourceTransformerLocal, fhirContext, uuidGenerator);

      RoutingData routingData =
          new RoutingData(
              NotificationType.LABORATORY,
              NotificationCategory.P_7_1,
              new LinkedHashSet<>(),
              List.of(),
              Map.of(),
              "1.",
              emptySet(),
              null);
      Notification notification =
          Notification.builder()
              .originalNotificationAsJson(json)
              .bundle(bundle)
              .testUser(false)
              .routingData(routingData)
              .build();
      java.util.SequencedSet<Action> actions = new LinkedHashSet<>();
      actions.add(PSEUDO_COPY);
      NotificationReceiver receiver =
          new NotificationReceiver("specificReceiver", "1.", actions, false);
      // first transformation with original code
      ReceiverActionService receiverActionServiceRegression =
          new ReceiverActionService(
              rkiBundleValidatorLocal,
              npsConfigPropertiesLocal,
              encryptionServiceLocal,
              notByNameRegressionServiceLocal,
              false,
              false);
      Optional<? extends IBaseResource> excerptRegression =
          receiverActionServiceRegression.transform(notification, receiver);

      // second transformation with new code
      ReceiverActionService receiverActionServiceLocal =
          new ReceiverActionService(
              rkiBundleValidatorLocal,
              npsConfigPropertiesLocal,
              encryptionServiceLocal,
              notByNameRegressionServiceLocal,
              false,
              true);
      Optional<? extends IBaseResource> excerpt =
          receiverActionServiceLocal.transform(notification, receiver);

      IParser iParser = fhirContext.newJsonParser().setPrettyPrint(true);

      String excerptJson = iParser.encodeResourceToString(excerpt.get());
      String excerptRegressionJson = iParser.encodeResourceToString(excerptRegression.get());
      // since the old implementation does some things not that great we replace some parts of the
      // string as negotiated with RKI
      excerptRegressionJson =
          excerptRegressionJson
              .replace("2b05ecfe-e489-4434-9c28-0edbc558a4d6", "someUuid")
              .replace("2000-01-01", "2000-01");

      assertThat(excerptJson).isEqualTo(excerptRegressionJson);
    }
  }

  @Test
  void nblShouldProduceEqualDiseaseNotificationExcerptAsLocalCode() throws IOException {
    try (MockedStatic<Utils> utilities = mockStatic(Utils.class)) {
      utilities.when(Utils::generateUuidString).thenReturn("someUuid");
      utilities.when(() -> Utils.getShortReferenceOrUrnUuid(any())).thenCallRealMethod();

      // given a 7.1 Bundle
      String json = Files.readString(Path.of("src/test/resources/bundles/disease_bundle_max.json"));
      Bundle bundle = FhirContext.forR4Cached().newJsonParser().parseResource(Bundle.class, json);

      RKIBundleValidator rkiBundleValidatorLocal = mock((RKIBundleValidator.class));
      when(rkiBundleValidatorLocal.isValidBundle(any(Bundle.class), anyString()))
          .thenReturn(new BundleValidationResult(true, null));
      NpsConfigProperties npsConfigPropertiesLocal = mock(NpsConfigProperties.class);
      when(npsConfigPropertiesLocal.anonymizedAllowed()).thenReturn(true);
      EncryptionService encryptionServiceLocal = mock(EncryptionService.class);
      PatientResourceTransformer patientResourceTransformer = new PatientResourceTransformer();
      FhirContext fhirContext = FhirContext.forR4Cached();
      UuidGenerator uuidGenerator = mock(UuidGenerator.class);
      when(uuidGenerator.generateUuid()).thenReturn("someUuid");
      NotByNameRegressionService notByNameRegressionServiceLocal =
          new NotByNameRegressionService(patientResourceTransformer, fhirContext, uuidGenerator);

      RoutingData routingData =
          new RoutingData(
              NotificationType.DISEASE,
              NotificationCategory.P_6_1,
              new LinkedHashSet<>(),
              List.of(),
              Map.of(),
              "1.",
              emptySet(),
              null);
      Notification notification =
          Notification.builder()
              .originalNotificationAsJson(json)
              .bundle(bundle)
              .testUser(false)
              .routingData(routingData)
              .build();
      java.util.SequencedSet<Action> actions = new LinkedHashSet<>();
      actions.add(PSEUDO_COPY);
      NotificationReceiver receiver =
          new NotificationReceiver("specificReceiver", "1.", actions, false);
      // first transformation with original code
      ReceiverActionService receiverActionServiceRegression =
          new ReceiverActionService(
              rkiBundleValidatorLocal,
              npsConfigPropertiesLocal,
              encryptionServiceLocal,
              notByNameRegressionServiceLocal,
              false,
              false);
      Optional<? extends IBaseResource> excerptRegression =
          receiverActionServiceRegression.transform(notification, receiver);

      // second transformation with new code
      ReceiverActionService receiverActionServiceLocal =
          new ReceiverActionService(
              rkiBundleValidatorLocal,
              npsConfigPropertiesLocal,
              encryptionServiceLocal,
              notByNameRegressionServiceLocal,
              false,
              true);
      Optional<? extends IBaseResource> excerpt =
          receiverActionServiceLocal.transform(notification, receiver);

      IParser iParser = fhirContext.newJsonParser().setPrettyPrint(true);

      // as far as we know any NotifiedPersonFacility shall not be copied to the excerpt. Since the
      // regression implementation ignores this we remove this resource for the comparison
      Bundle regressionBundle = (Bundle) (excerptRegression.get());
      regressionBundle.setEntry(
          regressionBundle.getEntry().stream()
              .filter(
                  e ->
                      !(e.getResource()
                          .getMeta()
                          .hasProfile(
                              "https://demis.rki.de/fhir/StructureDefinition/NotifiedPersonFacility")))
              .toList());
      // since the old implementation does some things not that great we replace some parts of the
      // string as negotiated with RKI
      String excerptJson = iParser.encodeResourceToString(excerpt.get());
      excerptJson =
          excerptJson.replace(
"""
, {
        "extension": [ {
          "url": "https://demis.rki.de/fhir/StructureDefinition/AddressUse",
          "valueCoding": {
            "system": "https://demis.rki.de/fhir/CodeSystem/addressUse",
            "code": "current",
            "display": "Derzeitiger Aufenthaltsort"
          }
        } ]
      }""",
              "");
      String excerptRegressionJson = iParser.encodeResourceToString(excerptRegression.get());
      excerptRegressionJson =
          excerptRegressionJson
              .replace("458a9cb4-1e94-424e-bfd2-64cbd3efa41e", "someUuid")
              .replace("2000-01-01", "2000-01")
              .replace("1999-06-01", "1999-06");

      assertThat(excerptJson).isEqualTo(excerptRegressionJson);
    }
  }

  @Test
  void createLaboratoryExcerptSmokeTest() throws IOException {
    int[] idHelper = {50};
    try (MockedStatic<Utils> utilities = mockStatic(Utils.class)) {
      utilities
          .when(Utils::generateUuidString)
          .thenAnswer(invocation -> Integer.toString(idHelper[0]++));
      utilities.when(() -> Utils.getShortReferenceOrUrnUuid(any())).thenCallRealMethod();

      // given a 7.1 Bundle
      String json =
          Files.readString(
              Path.of(
                  "src/test/resources/bundles/LaboratoryNotificationTestcaseForNotByNameExcerpt.json"));
      Bundle bundle = FhirContext.forR4Cached().newJsonParser().parseResource(Bundle.class, json);

      RKIBundleValidator rkiBundleValidatorLocal = mock((RKIBundleValidator.class));
      when(rkiBundleValidatorLocal.isValidBundle(any(Bundle.class), anyString()))
          .thenReturn(new BundleValidationResult(true, null));
      NpsConfigProperties npsConfigPropertiesLocal = mock(NpsConfigProperties.class);
      when(npsConfigPropertiesLocal.anonymizedAllowed()).thenReturn(true);
      EncryptionService encryptionServiceLocal = mock(EncryptionService.class);
      PatientResourceTransformer patientResourceTransformer = new PatientResourceTransformer();
      FhirContext fhirContext = FhirContext.forR4Cached();
      UuidGenerator uuidGenerator = mock(UuidGenerator.class);
      NotByNameRegressionService notByNameRegressionServiceLocal =
          new NotByNameRegressionService(patientResourceTransformer, fhirContext, uuidGenerator);

      RoutingData routingData =
          new RoutingData(
              NotificationType.LABORATORY,
              NotificationCategory.P_7_1,
              new LinkedHashSet<>(),
              List.of(),
              Map.of(),
              "1.",
              emptySet(),
              null);
      Notification notification =
          Notification.builder()
              .originalNotificationAsJson(json)
              .bundle(bundle)
              .testUser(false)
              .routingData(routingData)
              .build();
      java.util.SequencedSet<Action> actions = new LinkedHashSet<>();
      actions.add(PSEUDO_COPY);
      NotificationReceiver receiver =
          new NotificationReceiver("specificReceiver", "1.", actions, false);

      ReceiverActionService receiverActionServiceLocal =
          new ReceiverActionService(
              rkiBundleValidatorLocal,
              npsConfigPropertiesLocal,
              encryptionServiceLocal,
              notByNameRegressionServiceLocal,
              false,
              true);
      Optional<? extends IBaseResource> excerpt =
          receiverActionServiceLocal.transform(notification, receiver);

      IParser iParser = fhirContext.newJsonParser().setPrettyPrint(true);
      String excerptJson = iParser.encodeResourceToString(excerpt.get());
      String expectedJson =
          Files.readString(
              Path.of(
                  "src/test/resources/bundles/LaboratoryNotificationTestcaseForNotByNameExcerptExpected.json"));

      assertThat(excerptJson).isEqualTo(expectedJson);
    }
  }

  @Test
  void createDiseaseExcerptSmokeTest() throws IOException {
    int[] idHelper = {50};
    try (MockedStatic<Utils> utilities = mockStatic(Utils.class)) {
      utilities
          .when(Utils::generateUuidString)
          .thenAnswer(invocation -> Integer.toString(idHelper[0]++));
      utilities.when(() -> Utils.getShortReferenceOrUrnUuid(any())).thenCallRealMethod();

      // given a 7.1 Bundle
      String json = Files.readString(Path.of("src/test/resources/bundles/disease_bundle_max.json"));
      Bundle bundle = FhirContext.forR4Cached().newJsonParser().parseResource(Bundle.class, json);

      RKIBundleValidator rkiBundleValidatorLocal = mock((RKIBundleValidator.class));
      when(rkiBundleValidatorLocal.isValidBundle(any(Bundle.class), anyString()))
          .thenReturn(new BundleValidationResult(true, null));
      NpsConfigProperties npsConfigPropertiesLocal = mock(NpsConfigProperties.class);
      when(npsConfigPropertiesLocal.anonymizedAllowed()).thenReturn(true);
      EncryptionService encryptionServiceLocal = mock(EncryptionService.class);
      FhirContext fhirContext = FhirContext.forR4Cached();

      RoutingData routingData =
          new RoutingData(
              NotificationType.DISEASE,
              NotificationCategory.P_6_1,
              new LinkedHashSet<>(),
              List.of(),
              Map.of(),
              "1.",
              emptySet(),
              null);
      Notification notification =
          Notification.builder()
              .originalNotificationAsJson(json)
              .bundle(bundle)
              .testUser(false)
              .routingData(routingData)
              .build();
      java.util.SequencedSet<Action> actions = new LinkedHashSet<>();
      actions.add(PSEUDO_COPY);
      NotificationReceiver receiver =
          new NotificationReceiver("specificReceiver", "1.", actions, false);

      // second transformation with new code
      ReceiverActionService receiverActionServiceLocal =
          new ReceiverActionService(
              rkiBundleValidatorLocal,
              npsConfigPropertiesLocal,
              encryptionServiceLocal,
              notByNameRegressionService,
              false,
              true);
      Optional<? extends IBaseResource> excerpt =
          receiverActionServiceLocal.transform(notification, receiver);

      IParser iParser = fhirContext.newJsonParser().setPrettyPrint(true);
      String excerptJson = iParser.encodeResourceToString(excerpt.get());

      String expectedJson =
          Files.readString(
              Path.of(
                  "src/test/resources/bundles/DiseaseNotificationTestcaseForNotByNameExcerptExpected.json"));

      assertThat(excerptJson).isEqualTo(expectedJson);
    }
  }

  private static Notification getLabNotification() throws IOException {
    String originalJSON =
        Files.readString(Path.of("src/test/resources/bundles/laboratory_cvdp_bundle.json"));
    Bundle bundle =
        FhirContext.forR4Cached().newJsonParser().parseResource(Bundle.class, originalJSON);
    return Notification.builder()
        .originalNotificationAsJson(originalJSON)
        .diseaseCodeRoot("xxx")
        .sender("Me")
        .bundle(bundle)
        .routingData(ROUTING)
        .build();
  }

  private static Notification getDisNotification() throws IOException {
    String originalJSON =
        Files.readString(Path.of("src/test/resources/bundles/disease_bundle_max.json"));
    Bundle bundle =
        FhirContext.forR4Cached().newJsonParser().parseResource(Bundle.class, originalJSON);
    return Notification.builder()
        .originalNotificationAsJson(originalJSON)
        .diseaseCodeRoot("xxx")
        .sender("Me")
        .bundle(bundle)
        .routingData(DIS_ROUTING)
        .build();
  }

  private static Notification p71Notification() {
    final Patient notifiedPerson = new NotifiedPersonNominalDataBuilder().setId("1").build();
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
        .diseaseCodeRoot("xxx")
        .sender("Me")
        .bundle(bundle)
        .routingData(ROUTING)
        .build();
  }
}

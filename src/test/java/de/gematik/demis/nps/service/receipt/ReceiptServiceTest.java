package de.gematik.demis.nps.service.receipt;

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

import static de.gematik.demis.nps.base.profile.DemisSystems.RESPONSIBLE_HEALTH_DEPARTMENT_CODING_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import de.gematik.demis.fhirparserlibrary.FhirParser;
import de.gematik.demis.notification.builder.demis.fhir.notification.types.NotificationCategory;
import de.gematik.demis.notification.builder.demis.fhir.notification.utils.Compositions;
import de.gematik.demis.nps.base.util.TimeProvider;
import de.gematik.demis.nps.base.util.UuidGenerator;
import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.service.Statistics;
import de.gematik.demis.nps.service.healthoffice.HealthOfficeMasterDataService;
import de.gematik.demis.nps.service.notification.Action;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.service.notification.NotificationType;
import de.gematik.demis.nps.service.processing.BundleAction;
import de.gematik.demis.nps.service.routing.AddressOriginEnum;
import de.gematik.demis.nps.service.routing.NotificationReceiver;
import de.gematik.demis.nps.service.routing.RoutingData;
import de.gematik.demis.nps.test.RoutingDataUtil;
import java.util.*;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

  private ReceiptBundleCreator receiptBundleCreator;
  @Mock private PdfGenServiceClient pdfGenServiceClient;
  @Mock private HealthOfficeMasterDataService healthOfficeMasterDataService;
  @Mock private FhirParser fhirParser;
  @Mock private Statistics statistics;

  private ReceiptService receiptService;

  @BeforeEach
  void setUp() {}

  //  Erfolgreiche Erstellung eines Receipt-Bundles mit gültiger Notification und Health Office
  @Test
  void shouldGenerateReceiptBundle() {
    receiptBundleCreator = new ReceiptBundleCreator(new UuidGenerator(), new TimeProvider());

    receiptService =
        new ReceiptService(
            receiptBundleCreator,
            pdfGenServiceClient,
            healthOfficeMasterDataService,
            fhirParser,
            statistics,
            false,
            false);

    Bundle bundle = new Bundle().setIdentifier(new Identifier().setValue("test-identifier"));
    bundle.setMeta(new Meta().addTag(RESPONSIBLE_HEALTH_DEPARTMENT_CODING_SYSTEM, "1.1.", null));

    Composition composition = new Composition();
    composition.setIdentifier(new Identifier().setValue("test-identifier-composition"));

    RoutingData routingData = RoutingDataUtil.empty61For("1.1.");
    Notification notification =
        Notification.builder()
            .bundle(bundle)
            .testUser(false)
            .type(NotificationType.DISEASE)
            .routingData(routingData)
            .build();

    Organization organization = new Organization();
    organization.setId("organizationId");
    when(healthOfficeMasterDataService.getHealthOfficeOrganization("1.1.", false))
        .thenReturn(organization);

    when(fhirParser.encodeToJson(bundle)).thenReturn("someBundleJsonMock");

    byte[] t = new byte[10];
    when(pdfGenServiceClient.createDiseasePdfFromJson("someBundleJsonMock")).thenReturn(t);

    Bundle receiptBundle = receiptService.generateReceipt(notification);

    verify(pdfGenServiceClient).createDiseasePdfFromJson("someBundleJsonMock");
    assertThat(receiptBundle.getType()).isEqualTo(Bundle.BundleType.COLLECTION);
    List<Bundle.BundleEntryComponent> entry = receiptBundle.getEntry();
    assertThat(entry).hasSize(4);
    assertThat(entry.get(0).getResource()).isInstanceOf(Composition.class);
    assertThat(entry.get(1).getResource()).isInstanceOf(Organization.class);
    assertThat(((Organization) entry.get(1).getResource()).getName()).isEqualTo("DEMIS");
    assertThat(entry.get(2).getResource()).isInstanceOf(Organization.class);
    assertThat(entry.get(2).getResource().getId()).isEqualTo("organizationId");
    assertThat(entry.get(3).getResource()).isInstanceOf(Binary.class);
    assertThat(receiptBundle.getMeta().getProfile().getFirst().asStringValue())
        .isEqualTo("https://demis.rki.de/fhir/StructureDefinition/ReceiptBundle");
  }

  //  Receipt-Bundle wird auch erstellt, wenn keine Health Office-Organisation gefunden wird
  @Test
  void shouldNotGenerateReceiptBundleIfBundleIsEmpty() {
    receiptBundleCreator = new ReceiptBundleCreator(new UuidGenerator(), new TimeProvider());

    receiptService =
        new ReceiptService(
            receiptBundleCreator,
            pdfGenServiceClient,
            healthOfficeMasterDataService,
            fhirParser,
            statistics,
            false,
            false);

    Bundle bundle = new Bundle().setIdentifier(new Identifier().setValue("test-identifier"));
    bundle.setMeta(new Meta().addTag(RESPONSIBLE_HEALTH_DEPARTMENT_CODING_SYSTEM, "1.1.", null));

    Composition composition = new Composition();
    composition.setIdentifier(new Identifier().setValue("test-identifier-composition"));

    HashMap<AddressOriginEnum, String> addressOriginEnumStringHashMap = new HashMap<>();
    SequencedSet<BundleAction> bundleActions = new LinkedHashSet<>();
    SequencedSet<Action> actions = new LinkedHashSet<>();
    RoutingData routingData =
        new RoutingData(
            NotificationType.LABORATORY,
            NotificationCategory.P_6_1,
            bundleActions,
            List.of(new NotificationReceiver("type", "1.1.", actions, false)),
            addressOriginEnumStringHashMap,
            "1.1.",
            Set.of(),
            null);
    Notification notification =
        Notification.builder()
            .bundle(bundle)
            .testUser(false)
            .type(NotificationType.LABORATORY)
            .routingData(routingData)
            .build();

    Organization organization = new Organization();
    organization.setId("organizationId");
    when(healthOfficeMasterDataService.getHealthOfficeOrganization("1.1.", false)).thenReturn(null);

    when(fhirParser.encodeToJson(bundle)).thenReturn("someBundleJsonMock");

    Bundle receiptBundle = receiptService.generateReceipt(notification);

    verify(pdfGenServiceClient).createLaboratoryPdfFromJson("someBundleJsonMock");
    assertThat(receiptBundle.getType()).isEqualTo(Bundle.BundleType.COLLECTION);
    List<Bundle.BundleEntryComponent> entry = receiptBundle.getEntry();
    assertThat(entry).hasSize(3);
    assertThat(entry.get(0).getResource()).isInstanceOf(Composition.class);
    assertThat(entry.get(1).getResource()).isInstanceOf(Organization.class);
    assertThat(((Organization) entry.get(1).getResource()).getName()).isEqualTo("DEMIS");
    assertThat(entry.get(2).getResource()).isInstanceOf(Binary.class);
    assertThat(receiptBundle.getMeta().getProfile().getFirst().asStringValue())
        .isEqualTo("https://demis.rki.de/fhir/StructureDefinition/ReceiptBundle");
  }

  //  Fehler beim PDF-Generieren führt nicht zum Abbruch, sondern erhöht den Fehlerzähler
  @Test
  void shouldGenerateReceiptBundleIfBundleIsEmpty() {

    receiptBundleCreator = new ReceiptBundleCreator(new UuidGenerator(), new TimeProvider());

    receiptService =
        new ReceiptService(
            receiptBundleCreator,
            pdfGenServiceClient,
            healthOfficeMasterDataService,
            fhirParser,
            statistics,
            false,
            false);

    Bundle bundle = new Bundle().setIdentifier(new Identifier().setValue("test-identifier"));
    bundle.setMeta(new Meta().addTag(RESPONSIBLE_HEALTH_DEPARTMENT_CODING_SYSTEM, "1.1.", null));

    Composition composition = new Composition();
    composition.setIdentifier(new Identifier().setValue("test-identifier-composition"));

    RoutingData routingData = RoutingDataUtil.empty61For("1.1.");
    Notification notification =
        Notification.builder()
            .bundle(bundle)
            .testUser(false)
            .type(NotificationType.DISEASE)
            .routingData(routingData)
            .build();

    Organization organization = new Organization();
    organization.setId("organizationId");
    when(healthOfficeMasterDataService.getHealthOfficeOrganization("1.1.", false)).thenReturn(null);

    when(fhirParser.encodeToJson(bundle)).thenReturn("someBundleJsonMock");
    when(pdfGenServiceClient.createDiseasePdfFromJson("someBundleJsonMock"))
        .thenThrow(new RuntimeException());

    Bundle receiptBundle = receiptService.generateReceipt(notification);

    verify(pdfGenServiceClient).createDiseasePdfFromJson("someBundleJsonMock");
    verify(statistics).incIgnoredErrorCounter(ErrorCode.NO_PDF.getCode());
    assertThat(receiptBundle.getType()).isEqualTo(Bundle.BundleType.COLLECTION);
    List<Bundle.BundleEntryComponent> entry = receiptBundle.getEntry();
    assertThat(entry).hasSize(2);
    assertThat(entry.get(0).getResource()).isInstanceOf(Composition.class);
    assertThat(entry.get(1).getResource()).isInstanceOf(Organization.class);
    assertThat(((Organization) entry.get(1).getResource()).getName()).isEqualTo("DEMIS");
    assertThat(receiptBundle.getMeta().getProfile().getFirst().asStringValue())
        .isEqualTo("https://demis.rki.de/fhir/StructureDefinition/ReceiptBundle");
  }

  //  Bei aktivierter Notification 7.3 wird das Pre-Encrypted-Bundle für die PDF-Erstellung
  // verwendet
  @Test
  void shouldGenerateNotificationFor7_3WithPreEncryptedBundle() {

    receiptBundleCreator = new ReceiptBundleCreator(new UuidGenerator(), new TimeProvider());

    receiptService =
        new ReceiptService(
            receiptBundleCreator,
            pdfGenServiceClient,
            healthOfficeMasterDataService,
            fhirParser,
            statistics,
            true,
            false);

    Bundle bundle = new Bundle().setIdentifier(new Identifier().setValue("test-identifier"));
    bundle.setMeta(new Meta().addTag(RESPONSIBLE_HEALTH_DEPARTMENT_CODING_SYSTEM, "1.1.", null));

    Composition composition = new Composition();
    composition.setIdentifier(new Identifier().setValue("test-identifier-composition"));

    HashMap<AddressOriginEnum, String> addressOriginEnumStringHashMap = new HashMap<>();
    SequencedSet<BundleAction> bundleActions = new LinkedHashSet<>();
    SequencedSet<Action> actions = new LinkedHashSet<>();
    RoutingData routingData =
        new RoutingData(
            NotificationType.DISEASE,
            NotificationCategory.P_6_1,
            bundleActions,
            List.of(new NotificationReceiver("type", "1.1.", actions, false)),
            addressOriginEnumStringHashMap,
            "1.1.",
            Set.of(),
            null);
    Notification notification =
        Notification.builder()
            .bundle(bundle)
            .testUser(false)
            .type(NotificationType.DISEASE)
            .routingData(routingData)
            .build();
    Bundle preEncryptedBundle = new Bundle();
    preEncryptedBundle.setId("originalBundle");
    notification.putPreEncryptedBundle("1.1.", preEncryptedBundle);

    Organization organization = new Organization();
    organization.setId("organizationId");
    when(healthOfficeMasterDataService.getHealthOfficeOrganization("1.1.", false)).thenReturn(null);

    when(fhirParser.encodeToJson(preEncryptedBundle)).thenReturn("someBundleJsonMock");

    receiptService.generateReceipt(notification);

    verify(pdfGenServiceClient).createDiseasePdfFromJson("someBundleJsonMock");
  }

  //  Für TestUser-Notifications wird das Pre-Encrypted-Bundle des TestUsers verwendet
  @Test
  void shouldGenerateNotificationForTestUserWithPreEncryptedBundle() {

    receiptBundleCreator = new ReceiptBundleCreator(new UuidGenerator(), new TimeProvider());

    receiptService =
        new ReceiptService(
            receiptBundleCreator,
            pdfGenServiceClient,
            healthOfficeMasterDataService,
            fhirParser,
            statistics,
            true,
            false);

    Bundle bundle = new Bundle().setIdentifier(new Identifier().setValue("test-identifier"));
    bundle.setMeta(new Meta().addTag(RESPONSIBLE_HEALTH_DEPARTMENT_CODING_SYSTEM, "1.1.", null));

    Composition composition = new Composition();
    composition.setIdentifier(new Identifier().setValue("test-identifier-composition"));

    HashMap<AddressOriginEnum, String> addressOriginEnumStringHashMap = new HashMap<>();
    SequencedSet<BundleAction> bundleActions = new LinkedHashSet<>();
    SequencedSet<Action> actions = new LinkedHashSet<>();
    RoutingData routingData =
        new RoutingData(
            NotificationType.DISEASE,
            NotificationCategory.P_6_1,
            bundleActions,
            List.of(new NotificationReceiver("type", "1.1.", actions, false)),
            addressOriginEnumStringHashMap,
            "1.1.",
            Set.of(),
            null);
    Notification notification =
        Notification.builder()
            .bundle(bundle)
            .testUser(true)
            .testUserRecipient("testUser")
            .type(NotificationType.DISEASE)
            .routingData(routingData)
            .build();
    Bundle preEncryptedBundle = new Bundle();
    preEncryptedBundle.setId("originalBundle");
    notification.putPreEncryptedBundle("1.1.", preEncryptedBundle);
    Bundle testUserPreEncryptedBundle = new Bundle();
    testUserPreEncryptedBundle.setId("testUserBundle");
    notification.putPreEncryptedBundle("testUser", testUserPreEncryptedBundle);

    Organization organization = new Organization();
    organization.setId("organizationId");
    when(healthOfficeMasterDataService.getHealthOfficeOrganization("1.1.", true)).thenReturn(null);

    when(fhirParser.encodeToJson(testUserPreEncryptedBundle)).thenReturn("someBundleJsonMock");

    receiptService.generateReceipt(notification);

    verify(pdfGenServiceClient).createDiseasePdfFromJson("someBundleJsonMock");
  }

  @Test
  void shouldUseOriginalBundleFor74Notifications() {
    receiptBundleCreator = new ReceiptBundleCreator(new UuidGenerator(), new TimeProvider());

    receiptService =
        new ReceiptService(
            receiptBundleCreator,
            pdfGenServiceClient,
            healthOfficeMasterDataService,
            fhirParser,
            statistics,
            true,
            false);

    Bundle bundle = new Bundle().setIdentifier(new Identifier().setValue("test-identifier"));
    bundle.setMeta(new Meta().addTag(RESPONSIBLE_HEALTH_DEPARTMENT_CODING_SYSTEM, "1.1.", null));

    HashMap<AddressOriginEnum, String> addressOriginEnumStringHashMap = new HashMap<>();
    SequencedSet<BundleAction> bundleActions = new LinkedHashSet<>();
    SequencedSet<Action> actions = new LinkedHashSet<>();
    RoutingData routingData =
        new RoutingData(
            NotificationType.LABORATORY,
            NotificationCategory.P_7_4,
            bundleActions,
            List.of(new NotificationReceiver("type", "1.1.", actions, false)),
            addressOriginEnumStringHashMap,
            "1.1",
            Set.of(),
            null);
    Notification notification = mock(Notification.class);
    when(notification.getBundle()).thenReturn(bundle);
    when(notification.getRoutingData()).thenReturn(routingData);
    when(notification.getResponsibleHealthOfficeId()).thenReturn(Optional.of("1.1."));
    when(notification.getType()).thenReturn(NotificationType.LABORATORY);
    when(fhirParser.encodeToJson(bundle)).thenReturn("someBundleJsonMock");

    receiptService.generateReceipt(notification);

    verify(pdfGenServiceClient).createLaboratoryPdfFromJson("someBundleJsonMock");
  }

  @Test
  void thatRegularNotificationProcessingDoesntSetACustodian() {
    final String responsibleHealthOffice = "responsible";

    receiptBundleCreator = new ReceiptBundleCreator(new UuidGenerator(), new TimeProvider());
    final Bundle processedBundle =
        new Bundle().setIdentifier(new Identifier().setValue("test-identifier"));
    receiptService =
        new ReceiptService(
            receiptBundleCreator,
            pdfGenServiceClient,
            healthOfficeMasterDataService,
            fhirParser,
            statistics,
            true,
            false);

    // We just need to ensure correct data flow, and we don't mind what's the organization exactly
    final Organization value = new Organization();
    when(healthOfficeMasterDataService.getHealthOfficeOrganization(anyString(), anyBoolean()))
        .thenReturn(value);

    final Notification notification = mock(Notification.class);
    // GIVEN a responsible health office
    when(notification.getResponsibleHealthOfficeId())
        .thenReturn(Optional.of(responsibleHealthOffice));
    when(notification.getBundle()).thenReturn(processedBundle);
    when(notification.getRoutingData())
        .thenReturn(RoutingDataUtil.emptyFor(responsibleHealthOffice));

    // WHEN we generate a receipt
    final Bundle receipt = receiptService.generateReceipt(notification);
    final Composition composition = Compositions.from(receipt).orElseThrow();

    // THEN we have the responsible health office as regular entry
    // We don't care what the service will compute it only matters that it gets the right parameters
    // passed
    verify(healthOfficeMasterDataService)
        .getHealthOfficeOrganization(responsibleHealthOffice, false);
    // AND no custodian
    // Composition::getCustodian would create an empty reference, so we just verify it's absent
    assertThat(composition.hasCustodian()).isFalse();
  }

  @Test
  void thatTestNotificationsSetACustomResponsibleHealthOffice() {
    final String responsibleHealthOffice = "responsible";

    receiptBundleCreator = new ReceiptBundleCreator(new UuidGenerator(), new TimeProvider());
    final Bundle processedBundle =
        new Bundle().setIdentifier(new Identifier().setValue("test-identifier"));
    receiptService =
        new ReceiptService(
            receiptBundleCreator,
            pdfGenServiceClient,
            healthOfficeMasterDataService,
            fhirParser,
            statistics,
            true,
            false);

    // We just need to ensure correct data flow, and we don't mind what is the organization exactly
    final Organization value = new Organization();
    when(healthOfficeMasterDataService.getHealthOfficeOrganization(anyString(), anyBoolean()))
        .thenReturn(value);

    final Notification notification = mock(Notification.class);
    // GIVEN a responsible health office
    when(notification.getResponsibleHealthOfficeId())
        .thenReturn(Optional.of(responsibleHealthOffice));
    when(notification.getBundle()).thenReturn(processedBundle);
    when(notification.getRoutingData())
        .thenReturn(RoutingDataUtil.emptyFor(responsibleHealthOffice));
    when(notification.isTestUser()).thenReturn(true);

    // WHEN we generate a receipt
    final Bundle receipt = receiptService.generateReceipt(notification);
    final Composition composition = Compositions.from(receipt).orElseThrow();

    // THEN we have the responsible health office as regular entry
    // We don't care what the service will compute it only matters that it gets the right parameters
    // passed
    verify(healthOfficeMasterDataService)
        .getHealthOfficeOrganization(responsibleHealthOffice, true);
    // AND no custodian
    // Composition::getCustodian would create an empty reference, so we just verify it's absent
    assertThat(composition.hasCustodian()).isFalse();
  }

  @Nested
  class Custodian {

    private static final String RESPONSIBLE_HEALTH_OFFICE = "responsible";

    private ReceiptService receiptService;

    @BeforeEach
    void setup() {
      receiptService =
          new ReceiptService(
              new ReceiptBundleCreator(new UuidGenerator(), new TimeProvider()),
              pdfGenServiceClient,
              healthOfficeMasterDataService,
              fhirParser,
              statistics,
              true,
              true);
    }

    @Test
    void thatTestNotificationsRetainOriginalResponsibleHealthOfficeData() {
      final String custodian = "custodian";

      // We just need to ensure correct data flow, and we don't mind what is the organization
      // exactly
      final Organization value = new Organization();
      when(healthOfficeMasterDataService.getHealthOfficeOrganization(RESPONSIBLE_HEALTH_OFFICE))
          .thenReturn(value);
      when(healthOfficeMasterDataService.getTestUserHealthOfficeOrganization(custodian))
          .thenCallRealMethod();

      final Notification notification = testNotification(custodian);

      // WHEN we generate a receipt
      final Bundle receipt = receiptService.generateReceipt(notification);
      final Composition composition = Compositions.from(receipt).orElseThrow();

      final Reference custodianRef = composition.getCustodian();
      assertThat(((Organization) custodianRef.getResource()).getId()).isEqualTo(custodian);

      // THEN we have the responsible health office as regular entry
      /* We don't test the HealthOfficeMasterDataService here, we ensure that the right parameters are passed */
      verify(healthOfficeMasterDataService).getHealthOfficeOrganization(RESPONSIBLE_HEALTH_OFFICE);
      verify(healthOfficeMasterDataService).getTestUserHealthOfficeOrganization(custodian);
    }

    @Test
    void thatMissingCustodianIsProcessedWithoutException() {
      // We just need to ensure correct data flow, and we don't mind what is the organization
      // exactly
      final Organization value = new Organization();
      when(healthOfficeMasterDataService.getHealthOfficeOrganization(RESPONSIBLE_HEALTH_OFFICE))
          .thenReturn(value);

      final Notification notification = testNotification(null);

      // WHEN we generate a receipt
      final Bundle receipt = receiptService.generateReceipt(notification);

      // THEN we have the responsible health office as regular entry
      /* We don't test the HealthOfficeMasterDataService here, we ensure that the right parameters are passed */
      verify(healthOfficeMasterDataService).getHealthOfficeOrganization(RESPONSIBLE_HEALTH_OFFICE);
      verify(healthOfficeMasterDataService, never())
          .getTestUserHealthOfficeOrganization(anyString());

      // AND no custodian
      // Composition::getCustodian would create an empty reference, so we just verify it's absent
      final Composition composition = Compositions.from(receipt).orElseThrow();
      assertThat(composition.hasCustodian()).isFalse();
    }

    private static Notification testNotification(final String custodian) {
      final Bundle processedBundle =
          new Bundle().setIdentifier(new Identifier().setValue("test-identifier"));

      final Notification notification = mock(Notification.class);
      // GIVEN a responsible health office
      when(notification.getResponsibleHealthOfficeId())
          .thenReturn(Optional.of(RESPONSIBLE_HEALTH_OFFICE));
      when(notification.getBundle()).thenReturn(processedBundle);
      when(notification.getRoutingData())
          .thenReturn(RoutingDataUtil.emptyFor(RESPONSIBLE_HEALTH_OFFICE, custodian));
      when(notification.isTestUser()).thenReturn(true);
      return notification;
    }
  }
}

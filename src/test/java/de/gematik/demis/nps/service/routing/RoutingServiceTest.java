package de.gematik.demis.nps.service.routing;

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

import static de.gematik.demis.nps.base.profile.DemisSystems.*;
import static de.gematik.demis.nps.service.routing.AddressOriginEnum.NOTIFIED_PERSON_CURRENT;
import static de.gematik.demis.nps.service.routing.AddressOriginEnum.NOTIFIED_PERSON_ORDINARY;
import static de.gematik.demis.nps.service.routing.AddressOriginEnum.NOTIFIED_PERSON_PRIMARY;
import static de.gematik.demis.nps.service.routing.AddressOriginEnum.NOTIFIER;
import static de.gematik.demis.nps.service.routing.AddressOriginEnum.SUBMITTER;
import static org.mockito.ArgumentMatchers.eq;

import de.gematik.demis.fhirparserlibrary.FhirParser;
import de.gematik.demis.nps.config.TestUserConfiguration;
import de.gematik.demis.nps.error.NpsServiceException;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.service.notification.NotificationUpdateService;
import java.util.Map;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoutingServiceTest {
  private static final String RESPONSIBLE_HEALTH_OFFICE = "HO-1";
  private static final String HEALTH_OFFICE_FOR_ADDRESS = "HO-2";
  @Mock NotificationRoutingServiceClient client;
  @Mock NotificationUpdateService updateService;
  @Mock TestUserConfiguration testUserConfiguration;
  @Mock FhirParser fhirParser;

  @InjectMocks RoutingService underTest;

  private static Stream<Arguments> addressOriginSource() {
    return Stream.of(
        Arguments.of(NOTIFIED_PERSON_PRIMARY, HEALTH_DEPARTMENT_FOR_PRIMARY_ADDRESS_CODING_SYSTEM),
        Arguments.of(NOTIFIED_PERSON_CURRENT, HEALTH_DEPARTMENT_FOR_CURRENT_ADDRESS_CODING_SYSTEM),
        Arguments.of(
            NOTIFIED_PERSON_ORDINARY, HEALTH_DEPARTMENT_FOR_ORDINARY_ADDRESS_CODING_SYSTEM),
        Arguments.of(NOTIFIER, NOTIFIER_HEALTH_DEPARTMENT_CODING_SYSTEM),
        Arguments.of(SUBMITTER, SUBMITTER_HEALTH_DEPARTMENT_CODING_SYSTEM));
  }

  private static LegacyRoutingOutputDto createRoutingOutput(final AddressOriginEnum addressOrigin) {
    final var routingOutput = new LegacyRoutingOutputDto();
    routingOutput.setResponsible(RESPONSIBLE_HEALTH_OFFICE);
    routingOutput.setHealthOffices(Map.of(addressOrigin, HEALTH_OFFICE_FOR_ADDRESS));
    return routingOutput;
  }

  @ParameterizedTest
  @MethodSource("addressOriginSource")
  void notificationRoutingService(
      final AddressOriginEnum addressOrigin,
      final String expectedHealthOfficeForAddressCodingSystem) {
    final var bundle = new Bundle();
    final var notification = Notification.builder().bundle(bundle).testUser(false).build();

    mockNrsCall(bundle, createRoutingOutput(addressOrigin));

    underTest.determineHealthOfficesAndAddToNotification(notification);

    verifyUpdateService(
        bundle,
        RESPONSIBLE_HEALTH_OFFICE,
        expectedHealthOfficeForAddressCodingSystem,
        HEALTH_OFFICE_FOR_ADDRESS);
    Mockito.verifyNoInteractions(testUserConfiguration);
  }

  private void verifyUpdateService(
      final Bundle bundle,
      final String responsibleHealthOffice,
      final String expectedHealthOfficeForAddressCodingSystem,
      final String expectedHealthOfficeForAddressValue) {
    Mockito.verify(updateService)
        .addOrReplaceMetaTag(
            bundle,
            "https://demis.rki.de/fhir/CodeSystem/ResponsibleDepartment",
            responsibleHealthOffice);
    Mockito.verify(updateService)
        .addMetaTag(
            bundle,
            expectedHealthOfficeForAddressCodingSystem,
            expectedHealthOfficeForAddressValue);
    Mockito.verifyNoMoreInteractions(updateService);
  }

  @Test
  void noHealthOfficeIsResponsible() {
    final var bundle = new Bundle();
    final var notification = Notification.builder().bundle(bundle).testUser(false).build();

    mockNrsCall(bundle, new LegacyRoutingOutputDto());

    Assertions.assertThrows(
        NpsServiceException.class,
        () -> underTest.determineHealthOfficesAndAddToNotification(notification));

    Mockito.verifyNoInteractions(testUserConfiguration, updateService);
  }

  @Test
  void testuserHealthOfficeProperty() {
    final var testHealthOffice = "My GA";
    final var bundle = new Bundle();
    final var notification =
        Notification.builder().bundle(bundle).testUser(true).sender("irrelevant").build();

    Mockito.when(testUserConfiguration.getReceiver("irrelevant")).thenReturn(testHealthOffice);

    mockNrsCall(bundle, createRoutingOutput(NOTIFIED_PERSON_PRIMARY));

    underTest.determineHealthOfficesAndAddToNotification(notification);

    verifyUpdateService(
        bundle,
        testHealthOffice,
        HEALTH_DEPARTMENT_FOR_PRIMARY_ADDRESS_CODING_SYSTEM,
        HEALTH_OFFICE_FOR_ADDRESS);
  }

  @Test
  void testuserViaGroupHealthOfficeProperty() {
    final var testHealthOffice = "My GA";
    final var bundle = new Bundle();
    final var notification =
        Notification.builder().bundle(bundle).testUser(true).sender("bund_id_user").build();

    Mockito.when(testUserConfiguration.getReceiver(eq("bund_id_user")))
        .thenReturn(testHealthOffice);

    mockNrsCall(bundle, createRoutingOutput(NOTIFIED_PERSON_PRIMARY));

    underTest.determineHealthOfficesAndAddToNotification(notification);

    verifyUpdateService(
        bundle,
        testHealthOffice,
        HEALTH_DEPARTMENT_FOR_PRIMARY_ADDRESS_CODING_SYSTEM,
        HEALTH_OFFICE_FOR_ADDRESS);
  }

  @Test
  void testuserSelfResponsible() {
    final var testHealthOffice = "My GA";
    final var bundle = new Bundle();
    final var notification =
        Notification.builder().bundle(bundle).testUser(true).sender(testHealthOffice).build();

    Mockito.when(testUserConfiguration.getReceiver(eq(testHealthOffice)))
        .thenReturn(testHealthOffice);

    mockNrsCall(bundle, createRoutingOutput(NOTIFIED_PERSON_PRIMARY));

    underTest.determineHealthOfficesAndAddToNotification(notification);

    verifyUpdateService(
        bundle,
        testHealthOffice,
        HEALTH_DEPARTMENT_FOR_PRIMARY_ADDRESS_CODING_SYSTEM,
        HEALTH_OFFICE_FOR_ADDRESS);
  }

  private void mockNrsCall(final Bundle input, final LegacyRoutingOutputDto output) {
    final String bundleAsJson = "notification as json string";
    Mockito.when(fhirParser.encodeToJson(input)).thenReturn(bundleAsJson);
    Mockito.when(client.determineRouting(bundleAsJson)).thenReturn(output);
  }
}

package de.gematik.demis.nps.service.pseudonymization;

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

import static de.gematik.demis.nps.base.profile.DemisSystems.RESPONSIBLE_HEALTH_DEPARTMENT_CODING_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.demis.notification.builder.demis.fhir.notification.builder.infectious.NotifiedPersonByNameDataBuilder;
import de.gematik.demis.notification.builder.demis.fhir.notification.builder.infectious.laboratory.NotificationBundleLaboratoryDataBuilder;
import de.gematik.demis.notification.builder.demis.fhir.notification.builder.infectious.laboratory.NotificationLaboratoryDataBuilder;
import de.gematik.demis.notification.builder.demis.fhir.notification.builder.technicals.HumanNameDataBuilder;
import de.gematik.demis.notification.builder.demis.fhir.notification.types.NotificationCategory;
import de.gematik.demis.nps.base.profile.DemisExtensions;
import de.gematik.demis.nps.base.util.SequencedSets;
import de.gematik.demis.nps.service.Statistics;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.service.notification.NotificationType;
import de.gematik.demis.nps.service.notification.NotificationUpdateService;
import de.gematik.demis.nps.service.routing.RoutingData;
import de.gematik.demis.nps.test.TestUtil;
import de.gematik.demis.service.base.error.ServiceCallException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.hl7.fhir.r4.model.Base64BinaryType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Specimen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class PseudoServiceTest {

  private static final String DISEASE_CODE = "cov";

  @Mock PseudonymizationServiceClient pseudonymizationServiceClient;
  @Mock NotificationUpdateService notificationUpdateService;
  @Mock Statistics statistics;

  private PseudoService underTest;

  private static Pseudonym createPseudonym(final String suffix) {
    return new Pseudonym(List.of("X" + suffix), List.of("Y" + suffix), "Z" + suffix, DISEASE_CODE);
  }

  @SneakyThrows
  private static PseudonymizationResponse decodePseudonym(final Extension e) {
    if (e.getValue() instanceof Base64BinaryType binaryType) {
      return new ObjectMapper().readValue(binaryType.getValue(), PseudonymizationResponse.class);
    }
    return null;
  }

  private static Bundle createBundle(
      final Patient notifiedPerson, final String bundleId, final String healthOffice) {
    final Specimen specimen = new Specimen();
    specimen.setId("s1");
    final DiagnosticReport diagnosticReport = new DiagnosticReport();
    diagnosticReport.setId("dr1");
    final var bundle =
        new NotificationBundleLaboratoryDataBuilder()
            .setNotificationLaboratory(
                new NotificationLaboratoryDataBuilder()
                    .setDefault()
                    .setNotifiedPerson(notifiedPerson)
                    .build())
            .setNotifiedPerson(notifiedPerson)
            .setSpecimen(List.of(specimen))
            .setLaboratoryReport(diagnosticReport)
            .setIdentifier(new Identifier().setValue(bundleId))
            .build();
    bundle
        .getMeta()
        .addTag()
        .setSystem(RESPONSIBLE_HEALTH_DEPARTMENT_CODING_SYSTEM)
        .setCode(healthOffice);
    return bundle;
  }

  private static Patient createPatient(
      final String birthDay, final String familyName, final String firstName) {
    final LocalDate date = LocalDate.parse(birthDay, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    return new NotifiedPersonByNameDataBuilder()
        .setHumanName(
            new HumanNameDataBuilder()
                .setFamilyName(familyName)
                .setGivenEntry(List.of(firstName))
                .build())
        .setBirthdate(new DateType(TestUtil.toDate(date)))
        .build();
  }

  private static Optional<ILoggingEvent> getFirstErrorLog(
      final ListAppender<ILoggingEvent> logEvents) {
    return logEvents.list.stream().filter(event -> event.getLevel() == Level.ERROR).findFirst();
  }

  private static ListAppender<ILoggingEvent> listenToLog() {
    final var log = (Logger) LoggerFactory.getLogger(PseudoService.class);
    final var listAppender = new ListAppender<ILoggingEvent>();
    listAppender.start();
    log.addAppender(listAppender);
    return listAppender;
  }

  void createAndStorePseudonymAndAddToNotification() {
    underTest =
        new PseudoService(
            pseudonymizationServiceClient,
            notificationUpdateService,
            new ObjectMapper(),
            statistics);

    final String familyName = "Mustermann";
    final String firstName = "Max";
    final String bundleId = "abc-def";
    final String healthOffice = "1.23.45.";
    final String birthDay = "23.05.1979";

    final RoutingData routingData =
        new RoutingData(
            NotificationType.DISEASE,
            NotificationCategory.UNKNOWN,
            SequencedSets.of(),
            List.of(),
            Map.of(),
            "");
    final var notifiedPerson = createPatient(birthDay, familyName, firstName);
    final var bundle = createBundle(notifiedPerson, bundleId, healthOffice);
    final var notification =
        Notification.builder()
            .bundle(bundle)
            .diseaseCode(DISEASE_CODE)
            .routingData(routingData)
            .testUser(false)
            .build();

    final var expectedRequest =
        new PseudonymizationRequest(bundleId, DISEASE_CODE, familyName, firstName, birthDay);

    final var activePseudonym = createPseudonym("ac");
    final PseudonymizationResponse pseudoResponse =
        new PseudonymizationResponse("demisPseudonym", createPseudonym("od"), activePseudonym);

    Mockito.when(pseudonymizationServiceClient.generatePseudonym(expectedRequest))
        .thenReturn(pseudoResponse);

    underTest.createAndStorePseudonymAndAddToNotification(notification);
  }

  private void verifyNotificationUpdate(
      final Bundle bundle,
      final Patient notifiedPerson,
      final PseudonymizationResponse pseudoResponse) {
    final var argumentCaptor = ArgumentCaptor.forClass(Extension.class);
    Mockito.verify(notificationUpdateService)
        .addExtension(Mockito.eq(bundle), Mockito.eq(notifiedPerson), argumentCaptor.capture());
    final Extension extension = argumentCaptor.getValue();

    assertThat(extension)
        .isNotNull()
        .returns(DemisExtensions.EXTENSION_URL_PSEUDONYM, Extension::getUrl)
        .returns(pseudoResponse, PseudoServiceTest::decodePseudonym);
  }

  @Test
  void pseudoServiceError() {
    underTest =
        new PseudoService(
            pseudonymizationServiceClient,
            notificationUpdateService,
            new ObjectMapper(),
            statistics);
    final RoutingData routingData =
        new RoutingData(
            NotificationType.DISEASE,
            NotificationCategory.UNKNOWN,
            SequencedSets.of(),
            List.of(),
            Map.of(),
            "");
    final var notifiedPerson = createPatient("23.05.1980", "Mustermann", "Max");
    final var bundle = createBundle(notifiedPerson, "123", "xxx");
    final var notification =
        Notification.builder()
            .bundle(bundle)
            .diseaseCode(DISEASE_CODE)
            .routingData(routingData)
            .testUser(false)
            .build();

    Mockito.when(pseudonymizationServiceClient.generatePseudonym(Mockito.any()))
        .thenThrow(new ServiceCallException(null, null, 400, null));

    final ListAppender<ILoggingEvent> logEvents = listenToLog();

    assertThatNoException()
        .isThrownBy(() -> underTest.createAndStorePseudonymAndAddToNotification(notification));

    final Optional<ILoggingEvent> log = getFirstErrorLog(logEvents);
    Assertions.assertThat(log)
        .isPresent()
        .get()
        .extracting(ILoggingEvent::getMessage)
        .asString()
        .contains("error in pseudonymization");
  }
}

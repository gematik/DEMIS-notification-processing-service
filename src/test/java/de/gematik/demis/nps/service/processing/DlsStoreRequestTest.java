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
 * For additional notes and disclaimer from gematik and in case of changes by gematik,
 * find details in the "Readme" file.
 * #L%
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import de.gematik.demis.notification.builder.demis.fhir.notification.builder.infectious.laboratory.NotificationBundleLaboratoryAnonymousDataBuilder;
import de.gematik.demis.nps.error.NpsServiceException;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.test.RoutingDataUtil;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Identifier;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class DlsStoreRequestTest {

  @Test
  void thatCorrectValuesAreExtractedFromNotification() {
    final String identifier = UUID.randomUUID().toString();
    final String responsibleDepartment = "1.";
    final Composition composition = new Composition();
    composition.setId("composition");
    composition.setIdentifier(new Identifier().setValue(identifier));
    final Notification input =
        Notification.builder()
            .bundle(
                new NotificationBundleLaboratoryAnonymousDataBuilder()
                    .setNotificationLaboratory(composition)
                    .build())
            .diseaseCodeRoot("root")
            .diseaseCode("diseaseXYZ")
            .routingData(RoutingDataUtil.emptyFor(responsibleDepartment, "custodian"))
            .build();
    final Instant timeOfRequest = Instant.now();

    final DlsStoreRequest actual =
        DlsStoreRequest.from(input, Clock.fixed(timeOfRequest, ZoneId.systemDefault()));
    assertThat(actual.notificationId()).isEqualTo(identifier);
    assertThat(actual.notificationCategory()).isEqualTo("diseaseXYZ");
    assertThat(actual.responsibleDepartment()).isEqualTo(responsibleDepartment);
    assertThat(actual.lastUpdated()).isEqualTo(timeOfRequest);
  }

  @Test
  void thatMissingNotificationIdRaisesException() {
    final Notification input =
        Notification.builder()
            .bundle(new Bundle())
            .diseaseCode("any")
            .routingData(RoutingDataUtil.emptyFor("any", "custodian"))
            .build();

    assertThatExceptionOfType(NpsServiceException.class)
        .isThrownBy(() -> DlsStoreRequest.from(input))
        .satisfies(
            e -> assertThat(e.getResponseStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
  }
}

package de.gematik.demis.nps.service.notbyname;

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

import static de.gematik.demis.nps.test.TestData.DISEASE_BUNDLE_NOTBYNAME_RESOURCE;
import static de.gematik.demis.nps.test.TestData.DISEASE_BUNDLE_RESOURCE;
import static de.gematik.demis.nps.test.TestData.LABORATORY_BUNDLE_NOTBYNAME_RESOURCE;
import static de.gematik.demis.nps.test.TestData.LABORATORY_BUNDLE_RESOURCE;
import static de.gematik.demis.nps.test.TestData.readResourceAsString;

import ca.uhn.fhir.context.FhirContext;
import de.gematik.demis.notification.builder.demis.fhir.notification.types.NotificationCategory;
import de.gematik.demis.nps.base.util.SequencedSets;
import de.gematik.demis.nps.base.util.UuidGenerator;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.service.notification.NotificationType;
import de.gematik.demis.nps.service.routing.RoutingData;
import de.gematik.demis.nps.test.TestData;
import de.gematik.demis.nps.test.TestUtil;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.groups.Tuple;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(
    classes = {NotByNameService.class, PatientResourceTransformer.class, FhirContext.class},
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NotByNameServiceIntegrationTest {

  @MockBean UuidGenerator uuidGenerator;

  @Autowired NotByNameService underTest;

  static Stream<Arguments> inputParams() {
    return Stream.of(
        Arguments.of(
            NotificationType.LABORATORY,
            LABORATORY_BUNDLE_RESOURCE,
            LABORATORY_BUNDLE_NOTBYNAME_RESOURCE),
        Arguments.of(
            NotificationType.DISEASE, DISEASE_BUNDLE_RESOURCE, DISEASE_BUNDLE_NOTBYNAME_RESOURCE));
  }

  @ParameterizedTest
  @MethodSource("inputParams")
  void test(final NotificationType type, final String bundleResourceName, final String expected) {
    Mockito.when(uuidGenerator.generateUuid()).thenReturn("u4-a1234-b9876");

    final Bundle bundle = TestData.getBundle(bundleResourceName);
    final RoutingData routingOutputDto =
        new RoutingData(
            type, NotificationCategory.P_6_1, SequencedSets.of(), List.of(), Map.of(), "");
    final Notification notification =
        Notification.builder().bundle(bundle).routingData(routingOutputDto).testUser(false).build();
    final Bundle result = underTest.createNotificationNotByName(notification);

    final SoftAssertions assertions = new SoftAssertions();

    assertions
        .assertThat(result.getMeta().getTag())
        .as("Related Notification-Bundle-Id")
        .extracting(Coding::getSystem, Coding::getCode)
        .contains(
            Tuple.tuple(
                "https://demis.rki.de/fhir/CodeSystem/RelatedNotificationBundle",
                bundle.getIdentifier().getValue()))
        .doesNotContain(Tuple.tuple("https://demis.rki.de/fhir/CodeSystem/TestUser", "testuser"));

    assertions
        .assertThat(TestUtil.fhirResourceToJson(result))
        .as("full json resource comparison")
        .isEqualToIgnoringWhitespace(readResourceAsString(expected));

    assertions.assertAll();
  }

  @Test
  void testuser() {
    final RoutingData routingData =
        new RoutingData(
            NotificationType.LABORATORY,
            NotificationCategory.P_6_1,
            SequencedSets.of(),
            List.of(),
            Map.of(),
            "");
    final Notification notification =
        Notification.builder()
            .bundle(TestData.laboratoryBundle())
            .routingData(routingData)
            .testUser(true)
            .testUserRecipient("testuser")
            .build();
    final Bundle result = underTest.createNotificationNotByName(notification);

    Assertions.assertThat(result.getMeta().getTag())
        .extracting(Coding::getSystem, Coding::getCode)
        .contains(Tuple.tuple("https://demis.rki.de/fhir/CodeSystem/TestUser", "testuser"));
  }
}

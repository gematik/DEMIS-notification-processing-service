package de.gematik.demis.nps.service.notbyname;

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

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.gematik.demis.notification.builder.demis.fhir.notification.utils.DemisConstants;
import de.gematik.demis.nps.base.profile.DemisSystems;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.service.notification.NotificationType;
import de.gematik.demis.nps.test.RoutingDataUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExcerptCreatorTest {

  private IParser fhirParser;

  @BeforeEach
  void setUp() {
    fhirParser = FhirContext.forR4Cached().newJsonParser();
  }

  @Test
  @DisplayName("createAnonymousBundle adds Test-User tag when Notification is a test user")
  void createAnonymousBundleAddsTestUserTagWhenTestUser() throws IOException {
    String json =
        Files.readString(Path.of("src/test/resources/bundles/7_3/laboratory-anonymous.json"));
    Bundle bundle = fhirParser.parseResource(Bundle.class, json);

    Notification notification =
        Notification.builder()
            .bundle(bundle)
            .testUser(true)
            .type(NotificationType.LABORATORY)
            .testUserRecipient("foobar")
            .routingData(RoutingDataUtil.laboratoryExample())
            .build();

    Bundle result = ExcerptCreator.createAnonymousBundle(notification);

    assertThat(result.getMeta().getTag()).isNotEmpty();
    assertThat(result.getMeta().getTag()).hasSize(2);
    assertThat(result.getMeta().getTag())
        .extracting("system")
        .containsExactlyInAnyOrder(
            DemisSystems.TEST_USER_CODING_SYSTEM,
            DemisConstants.RELATED_NOTIFICATION_CODING_SYSTEM);
  }

  @Test
  @DisplayName(
      "createAnonymousBundle does not add Test-User tag when Notification is not a test user")
  void createAnonymousBundleDoesNotAddTestUserTagWhenNotTestUser() throws IOException {
    String json =
        Files.readString(
            Path.of("src/test/resources/bundles/7_3/disease-nonnominal-notbyname.json"));
    Bundle bundle = fhirParser.parseResource(Bundle.class, json);

    Notification notification =
        Notification.builder()
            .bundle(bundle)
            .testUser(false)
            .type(NotificationType.DISEASE)
            .routingData(RoutingDataUtil.diseaseExample())
            .build();

    Bundle result = ExcerptCreator.createAnonymousBundle(notification);

    assertThat(result.getMeta().getTag()).isNotEmpty();
    assertThat(result.getMeta().getTag()).hasSize(1);
    assertThat(result.getMeta().getTag())
        .extracting("system")
        .containsOnly(DemisConstants.RELATED_NOTIFICATION_CODING_SYSTEM);
  }

  @Test
  @DisplayName("createNotByNameBundle adds Test-User tag when Notification is a test user")
  void createNotByNameBundleAddsTestUserTagWhenTestUser() throws IOException {
    String json =
        Files.readString(Path.of("src/test/resources/bundles/laboratory_cvdp_bundle.json"));
    Bundle bundle = fhirParser.parseResource(Bundle.class, json);

    Notification notification =
        Notification.builder()
            .bundle(bundle)
            .testUser(true)
            .type(NotificationType.LABORATORY)
            .testUserRecipient("foobar")
            .routingData(RoutingDataUtil.laboratoryExample())
            .build();

    Bundle result = ExcerptCreator.createNotByNameBundle(notification);

    assertThat(result.getMeta().getTag()).isNotEmpty();
    assertThat(result.getMeta().getTag()).hasSize(2);
    assertThat(result.getMeta().getTag())
        .extracting("system")
        .containsExactlyInAnyOrder(
            DemisSystems.TEST_USER_CODING_SYSTEM,
            DemisConstants.RELATED_NOTIFICATION_CODING_SYSTEM);
  }

  @Test
  @DisplayName(
      "createNotByNameBundle does not add Test-User tag when Notification is not a test user")
  void createNotByNameBundleDoesNotAddTestUserTagWhenNotTestUser() throws IOException {
    String json = Files.readString(Path.of("src/test/resources/bundles/disease_bundle_max.json"));
    Bundle bundle = fhirParser.parseResource(Bundle.class, json);

    Notification notification =
        Notification.builder()
            .bundle(bundle)
            .testUser(false)
            .type(NotificationType.DISEASE)
            .routingData(RoutingDataUtil.diseaseExample())
            .build();

    Bundle result = ExcerptCreator.createNotByNameBundle(notification);

    assertThat(result.getMeta().getTag()).isNotEmpty();
    assertThat(result.getMeta().getTag()).hasSize(1);
    assertThat(result.getMeta().getTag())
        .extracting("system")
        .containsOnly(DemisConstants.RELATED_NOTIFICATION_CODING_SYSTEM);
  }
}

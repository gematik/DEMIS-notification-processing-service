package de.gematik.demis.nps.service.notification;

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

import static de.gematik.demis.nps.test.TestData.DISEASE_BUNDLE_RESOURCE;
import static de.gematik.demis.nps.test.TestData.LABORATORY_BUNDLE_ESCAPED_RESOURCE;
import static de.gematik.demis.nps.test.TestData.LABORATORY_BUNDLE_RESOURCE;
import static de.gematik.demis.nps.test.TestData.getBundle;
import static de.gematik.demis.nps.test.TestData.laboratoryBundle;
import static de.gematik.demis.nps.test.TestUtil.fhirResourceToJson;
import static de.gematik.demis.nps.test.TestUtil.fhirResourceToXml;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import de.gematik.demis.fhirparserlibrary.FhirParser;
import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.nps.config.TestUserConfiguration;
import de.gematik.demis.nps.error.NpsServiceException;
import de.gematik.demis.nps.service.codemapping.CodeMappingService;
import java.util.Collections;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CanonicalType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationFhirServiceTest {

  private NotificationFhirService underTest;
  @Mock private FhirParser fhirParserMock;
  @Mock private NotificationCleaning cleanerMock;
  @Mock private NotificationEnrichment enricherMock;
  @Mock private TestUserConfiguration testUserConfigurationMock;
  @Mock private CodeMappingService codeMappingServiceMock;

  @BeforeEach
  void beforeAll() {
    underTest =
        new NotificationFhirService(
            fhirParserMock,
            cleanerMock,
            enricherMock,
            testUserConfigurationMock,
            codeMappingServiceMock);
  }

  @Nested
  class PreChecks {

    private static void updateProfile(final Bundle bundle, final String newProfile) {
      bundle.getMeta().setProfile(Collections.singletonList(new CanonicalType(newProfile)));
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "https://demis.rki.de/fhir/StructureDefinition/NotificationBundleLaboratoryNotSupported",
          "https://demis.rki.de/fhir/StructureDefinition/NotificationBundleDiseaseNotSupported",
          "https://demis.rki.de/fhir/StructureDefinition/NotificationBundleLaboratory/NotSupported",
          "https://demis.rki.de/fhir/NotificationBundleLaboratory"
        })
    void invalidProfileJson(final String profile) {
      // note: for the negative test it does not matter if we use a laboratory or disease
      // notification
      final Bundle bundle = laboratoryBundle();
      updateProfile(bundle, profile);
      final String fhirNotification = fhirResourceToJson(bundle);
      assertThatThrownBy(() -> underTest.preCheckProfile(fhirNotification))
          .isInstanceOf(NpsServiceException.class)
          .hasMessage("bundle profile not supported (pre-check).");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "https://demis.rki.de/fhir/StructureDefinition/NotificationBundleLaboratoryNotSupported",
          "https://demis.rki.de/fhir/StructureDefinition/NotificationBundleDiseaseNotSupported",
          "https://demis.rki.de/fhir/StructureDefinition/NotificationBundleLaboratory/NotSupported",
          "https://demis.rki.de/fhir/NotificationBundleLaboratory"
        })
    void invalidProfileXml(final String profile) {
      // note: for the negative test it does not matter if we use a laboratory or disease
      // notification
      final Bundle bundle = laboratoryBundle();
      updateProfile(bundle, profile);
      final String fhirNotification = fhirResourceToXml(bundle);
      assertThatThrownBy(() -> underTest.preCheckProfile(fhirNotification))
          .isInstanceOf(NpsServiceException.class)
          .hasMessage("bundle profile not supported (pre-check).");
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          LABORATORY_BUNDLE_RESOURCE,
          DISEASE_BUNDLE_RESOURCE,
          LABORATORY_BUNDLE_ESCAPED_RESOURCE,
          "/bundles/7_3/anonymous.json",
          "/bundles/7_3/nonnominal-notifiedperson.json",
          "/integrationtest/laboratory/input-notification-7_4.json"
        })
    void profileOkayJson(final String resourceName) {
      final String fhirNotification = fhirResourceToJson(getBundle(resourceName));
      assertDoesNotThrow(() -> underTest.preCheckProfile(fhirNotification));
    }

    @ParameterizedTest
    @ValueSource(strings = {LABORATORY_BUNDLE_RESOURCE, DISEASE_BUNDLE_RESOURCE})
    void profileOkayXml(final String resourceName) {
      final String fhirNotification = fhirResourceToXml(getBundle(resourceName));
      assertDoesNotThrow(() -> underTest.preCheckProfile(fhirNotification));
    }
  }

  @Nested
  class ReadNotification {
    @ParameterizedTest
    @EnumSource(MessageType.class)
    void read(final MessageType messageType) {
      final String fhirString = "valid fhir string";
      final String sender = "me";
      final var bundle = laboratoryBundle();
      Mockito.when(fhirParserMock.parseBundleOrParameter(fhirString, messageType))
          .thenReturn(bundle);
      final Notification notification = underTest.read(fhirString, messageType, sender, false);
      assertThat(notification)
          .isNotNull()
          .returns(bundle, Notification::getBundle)
          .returns(NotificationType.LABORATORY, Notification::getType)
          .returns(sender, Notification::getSender)
          .returns(false, Notification::isTestUser);
    }

    @Test
    void testUserHeaderFlag() {
      Mockito.when(fhirParserMock.parseBundleOrParameter(any(String.class), any(MessageType.class)))
          .thenReturn(laboratoryBundle());
      final Notification notification = underTest.read("fhir", MessageType.JSON, "user-name", true);
      assertThat(notification).isNotNull().returns(true, Notification::isTestUser);
    }

    @Test
    void testUserConfiguredName() {
      Mockito.when(fhirParserMock.parseBundleOrParameter(any(String.class), any(MessageType.class)))
          .thenReturn(laboratoryBundle());
      final String sender = "testusername";
      Mockito.when(testUserConfigurationMock.isTestUser(eq(sender))).thenReturn(true);
      final Notification notification = underTest.read("fhir", MessageType.JSON, sender, false);
      assertThat(notification).isNotNull().returns(true, Notification::isTestUser);
    }
  }
}

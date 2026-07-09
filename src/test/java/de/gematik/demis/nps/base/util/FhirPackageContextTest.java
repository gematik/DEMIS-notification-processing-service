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

package de.gematik.demis.nps.base.util;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.nps.config.FeatureFlagsConfigProperties;
import de.gematik.demis.nps.config.NpsHeaders;
import de.gematik.demis.nps.service.notification.NotificationType;
import de.gematik.demis.nps.service.notification.NotificationTypeResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FhirPackageContextTest {

  private static final String ANY_VERSION = "any-version";
  private static final String ANY_PACKAGE = "disease";

  @Mock private HttpServletRequest request;
  @Mock private NotificationTypeResolver notificationTypeResolver;
  @Mock private FhirPackageVersionResolver fhirPackageVersionResolver;
  @Mock private FeatureFlagsConfigProperties featureFlagsConfigProperties;

  @InjectMocks private FhirPackageContext underTest;

  // -------------------------------------------------------------------------
  // General
  // -------------------------------------------------------------------------

  @Test
  void initialize_headersTakenDirectlyFromIncomingRequestIfAvailable() {
    when(request.getHeader(NpsHeaders.HEADER_FHIR_PACKAGE)).thenReturn(ANY_PACKAGE);
    when(featureFlagsConfigProperties.isEnabled(FhirPackageContext.FEATURE_FLAG_FHIR_CORE_SPLIT))
        .thenReturn(true);
    when(request.getHeader(NpsHeaders.HEADER_FHIR_PACKAGE_VERSION)).thenReturn(ANY_VERSION);

    underTest.initialize("SHOULD_NOT_BE_USED", MessageType.JSON);

    assertEquals(ANY_PACKAGE, underTest.getOutgoingFhirPackageHeaderValue());
    assertEquals(ANY_VERSION, underTest.getOutgoingFhirPackageVersionHeaderValue());
    verifyNoInteractions(notificationTypeResolver, fhirPackageVersionResolver);
    verify(request, never()).getHeader(NpsHeaders.HEADER_DEFAULT_FHIR_PACKAGE_VERSIONS);
  }

  @Test
  void initialize_isIdempotent_subsequentCallsAreIgnored() {
    when(request.getHeader(NpsHeaders.HEADER_FHIR_PACKAGE)).thenReturn(ANY_PACKAGE);
    when(featureFlagsConfigProperties.isEnabled(FhirPackageContext.FEATURE_FLAG_FHIR_CORE_SPLIT))
        .thenReturn(true);
    when(request.getHeader(NpsHeaders.HEADER_FHIR_PACKAGE_VERSION)).thenReturn(ANY_VERSION);

    underTest.initialize("{json1}", MessageType.JSON);
    clearInvocations(
        request,
        notificationTypeResolver,
        featureFlagsConfigProperties,
        fhirPackageVersionResolver);

    underTest.initialize("{json2}", MessageType.JSON);

    assertEquals(ANY_PACKAGE, underTest.getOutgoingFhirPackageHeaderValue());
    assertEquals(ANY_VERSION, underTest.getOutgoingFhirPackageVersionHeaderValue());
    verify(featureFlagsConfigProperties).isEnabled(FhirPackageContext.FEATURE_FLAG_FHIR_CORE_SPLIT);
    verifyNoInteractions(request, notificationTypeResolver, fhirPackageVersionResolver);
  }

  // -------------------------------------------------------------------------
  // x-fhir-package header
  // -------------------------------------------------------------------------

  @Test
  void getOutgoingFhirPackageHeaderValue_throwsIfNotInitialized() {
    assertThrows(IllegalStateException.class, () -> underTest.getOutgoingFhirPackageHeaderValue());
  }

  @Test
  void initialize_packageFallsBackToLegacyWhenFeatureFlagIsOff() {
    when(request.getHeader(NpsHeaders.HEADER_FHIR_PACKAGE)).thenReturn(null);
    when(featureFlagsConfigProperties.isEnabled(FhirPackageContext.FEATURE_FLAG_FHIR_CORE_SPLIT))
        .thenReturn(false);

    underTest.initialize("{json}", MessageType.JSON);

    assertEquals(
        FhirPackageContext.LEGACY_CORE_PACKAGE, underTest.getOutgoingFhirPackageHeaderValue());
    assertThrows(
        IllegalStateException.class, () -> underTest.getOutgoingFhirPackageVersionHeaderValue());
    verifyNoInteractions(notificationTypeResolver, fhirPackageVersionResolver);
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void initialize_packageDerivedFromNotificationTypeWhenFeatureFlagIsOn(
      NotificationType notificationType) {
    when(request.getHeader(NpsHeaders.HEADER_FHIR_PACKAGE)).thenReturn(null);
    when(featureFlagsConfigProperties.isEnabled(FhirPackageContext.FEATURE_FLAG_FHIR_CORE_SPLIT))
        .thenReturn(true);
    when(notificationTypeResolver.resolveFromNotification("{json}", MessageType.JSON))
        .thenReturn(notificationType);
    when(request.getHeader(NpsHeaders.HEADER_FHIR_PACKAGE_VERSION)).thenReturn(ANY_VERSION);

    underTest.initialize("{json}", MessageType.JSON);

    String expectedPackage =
        notificationType == NotificationType.DISEASE
            ? FhirPackageContext.DISEASE_PACKAGE
            : FhirPackageContext.LABORATORY_PACKAGE;
    assertEquals(expectedPackage, underTest.getOutgoingFhirPackageHeaderValue());
  }

  // -------------------------------------------------------------------------
  // x-fhir-package-version header
  // -------------------------------------------------------------------------

  @Test
  void getOutgoingFhirPackageVersionHeaderValue_throwsIfNotInitialized() {
    assertThrows(
        IllegalStateException.class, () -> underTest.getOutgoingFhirPackageVersionHeaderValue());
  }

  @Test
  void initialize_versionResolvedFromDefaultHeaderWhenVersionHeaderAbsent() {
    when(request.getHeader(NpsHeaders.HEADER_FHIR_PACKAGE)).thenReturn("laboratory");
    when(featureFlagsConfigProperties.isEnabled(FhirPackageContext.FEATURE_FLAG_FHIR_CORE_SPLIT))
        .thenReturn(true);
    when(request.getHeader(NpsHeaders.HEADER_FHIR_PACKAGE_VERSION)).thenReturn(null);
    when(request.getHeader(NpsHeaders.HEADER_DEFAULT_FHIR_PACKAGE_VERSIONS))
        .thenReturn("disease:v7;laboratory:v6");
    when(fhirPackageVersionResolver.resolveVersion("disease:v7;laboratory:v6", "laboratory"))
        .thenReturn(Optional.of("v6"));

    underTest.initialize("{json}", MessageType.JSON);

    assertEquals("v6", underTest.getOutgoingFhirPackageVersionHeaderValue());
  }

  @Test
  void initialize_throwsWhenNoEntryMatchesPackageInDefaultHeader() {
    when(request.getHeader(NpsHeaders.HEADER_FHIR_PACKAGE)).thenReturn("laboratory");
    when(featureFlagsConfigProperties.isEnabled(FhirPackageContext.FEATURE_FLAG_FHIR_CORE_SPLIT))
        .thenReturn(true);
    when(request.getHeader(NpsHeaders.HEADER_FHIR_PACKAGE_VERSION)).thenReturn(null);
    when(request.getHeader(NpsHeaders.HEADER_DEFAULT_FHIR_PACKAGE_VERSIONS))
        .thenReturn("disease:v6");
    when(fhirPackageVersionResolver.resolveVersion("disease:v6", "laboratory"))
        .thenReturn(Optional.empty());

    assertThrows(
        IllegalStateException.class, () -> underTest.initialize("{json}", MessageType.JSON));
  }
}

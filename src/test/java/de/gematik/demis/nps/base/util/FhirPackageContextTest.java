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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FhirPackageContextTest {

  @Mock private HttpServletRequest request;
  @Mock private NotificationTypeResolver notificationTypeResolver;
  @Mock private FeatureFlagsConfigProperties featureFlagsConfigProperties;

  @InjectMocks private FhirPackageContext underTest;

  @Test
  void getOutgoingFhirProfileHeader_Value_throwsIfNotInitialized() {
    assertThrows(IllegalStateException.class, () -> underTest.getOutgoingFhirPackageHeaderValue());
  }

  @Test
  void initializeOnce_doesNotDeriveProfileHeaderIfAlreadyPresentInRequest() {
    when(request.getHeader(NpsHeaders.HEADER_FHIR_PACKAGE)).thenReturn("profile-1");

    underTest.initialize("SHOULD_NOT_BE_USED", MessageType.JSON);

    assertEquals("profile-1", underTest.getOutgoingFhirPackageHeaderValue());

    verifyNoInteractions(notificationTypeResolver);
    verifyNoInteractions(featureFlagsConfigProperties);
  }

  @Test
  void initializeOnce_setsLegacyProfileWhenHeaderMissingAndFeatureFlagIsOff() {
    when(request.getHeader(NpsHeaders.HEADER_FHIR_PACKAGE)).thenReturn(null);
    when(featureFlagsConfigProperties.isEnabled(FhirPackageContext.FEATURE_FLAG_FHIR_CORE_SPLIT))
        .thenReturn(false);

    underTest.initialize("{json}", MessageType.JSON);

    assertEquals(
        FhirPackageContext.LEGACY_CORE_PACKAGE, underTest.getOutgoingFhirPackageHeaderValue());

    verifyNoInteractions(notificationTypeResolver);
  }

  @ParameterizedTest
  @EnumSource(NotificationType.class)
  void initializeOnce_derivesSplitProfileWhenHeaderMissingAndFeatureFlagIsOn(
      NotificationType notificationType) {
    when(request.getHeader(NpsHeaders.HEADER_FHIR_PACKAGE)).thenReturn(null);
    when(featureFlagsConfigProperties.isEnabled(FhirPackageContext.FEATURE_FLAG_FHIR_CORE_SPLIT))
        .thenReturn(true);
    when(notificationTypeResolver.resolveFromNotification("{json}", MessageType.JSON))
        .thenReturn(notificationType);

    underTest.initialize("{json}", MessageType.JSON);

    switch (notificationType) {
      case DISEASE ->
          assertEquals(
              FhirPackageContext.DISEASE_PACKAGE, underTest.getOutgoingFhirPackageHeaderValue());
      case LABORATORY ->
          assertEquals(
              FhirPackageContext.LABORATORY_PACKAGE, underTest.getOutgoingFhirPackageHeaderValue());
    }
  }

  @Test
  void initializeOnce_firstCallWins_subsequentCallsIgnored() {
    when(request.getHeader(NpsHeaders.HEADER_FHIR_PACKAGE)).thenReturn("profile-1");

    underTest.initialize("{json1}", MessageType.JSON);
    clearInvocations(request, notificationTypeResolver, featureFlagsConfigProperties);

    underTest.initialize("{json2}", MessageType.JSON);

    assertEquals("profile-1", underTest.getOutgoingFhirPackageHeaderValue());
    verifyNoInteractions(request);
    verifyNoInteractions(notificationTypeResolver);
    verifyNoInteractions(featureFlagsConfigProperties);
  }
}

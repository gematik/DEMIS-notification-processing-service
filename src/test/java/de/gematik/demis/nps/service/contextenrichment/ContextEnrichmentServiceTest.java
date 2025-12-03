package de.gematik.demis.nps.service.contextenrichment;

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

import static de.gematik.demis.nps.test.TestData.PROVENANCE_RESOURCE;
import static de.gematik.demis.nps.test.TestData.diseaseBundle;
import static de.gematik.demis.nps.test.TestUtil.getJsonParser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.gematik.demis.fhirparserlibrary.FhirParser;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.service.notification.NotificationUpdateService;
import de.gematik.demis.nps.service.routing.RoutingData;
import de.gematik.demis.nps.test.RoutingDataUtil;
import de.gematik.demis.nps.test.TestData;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Provenance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContextEnrichmentServiceTest {

  @Captor private ArgumentCaptor<BundleEntryComponent> entityComponentCaptor;
  @Mock private ContextEnrichmentServiceClient contextEnrichmentServiceClient;
  @Mock private FhirParser fhirParser;
  @Mock NotificationUpdateService notificationUpdateService;
  ContextEnrichmentService underTest;

  private Bundle bundle;
  private Notification notification;
  private final String TOKEN = "SomeToken";

  @BeforeEach
  void setUp() {
    final RoutingData routingData = RoutingDataUtil.emptyFor("");
    bundle = diseaseBundle();
    notification = Notification.builder().bundle(bundle).routingData(routingData).build();
    underTest =
        new ContextEnrichmentService(
            contextEnrichmentServiceClient, fhirParser, notificationUpdateService);
  }

  @Test
  @DisplayName(
      "Test that if authorization is null, contextEnrichmentServiceClient doesn't got called")
  void testShouldNotInteractWithClientIfAuthorizationIsNotSet() {
    underTest.enrichBundleWithContextInformation(notification, null);
    verifyNoInteractions(contextEnrichmentServiceClient);
  }

  @Test
  @DisplayName("Test that fhirParser is not called if client throws an error")
  void testFhirParserDoesNotGetCalledIfClientError() {
    when(contextEnrichmentServiceClient.enrichBundleWithContextInformation(any(), any()))
        .thenThrow(new RuntimeException("Some error"));
    underTest.enrichBundleWithContextInformation(notification, TOKEN);
    verify(fhirParser, times(0)).parseFromJson(anyString());
  }

  @Test
  @DisplayName("Test that the bundle have not been changed if client throws an error")
  void testIfTheSameBundleIsReturnedAsFallbackWhenClientError() {
    String bundleString = getJsonParser().encodeToString(bundle);
    when(contextEnrichmentServiceClient.enrichBundleWithContextInformation(any(), any()))
        .thenThrow(new RuntimeException("Some error"));
    underTest.enrichBundleWithContextInformation(notification, TOKEN);
    assertThat(getJsonParser().encodeToString(notification.getBundle())).isEqualTo(bundleString);
  }

  @Test
  @DisplayName(
      "Test that the bundle have not been changed if the contextEnrichmentServiceClient returns invalid data")
  void testIfTheSameBundleIsReturnedAsFallbackWhenBundleHaveNoProvenance() {
    String bundleString = getJsonParser().encodeToString(bundle);
    when(contextEnrichmentServiceClient.enrichBundleWithContextInformation(any(), any()))
        .thenReturn("changedBundle");
    when(fhirParser.parseFromJson(anyString())).thenReturn(bundle);

    underTest.enrichBundleWithContextInformation(notification, TOKEN);

    assertThat(getJsonParser().encodeToString(notification.getBundle())).isEqualTo(bundleString);
  }

  @Test
  @DisplayName("Test that the bundle enriched correctly")
  void testProvenanceGotAppendCorrectly() {
    String response = "changedBundle";
    Provenance provenance =
        getJsonParser()
            .parseResource(Provenance.class, TestData.readResourceAsString(PROVENANCE_RESOURCE));
    when(contextEnrichmentServiceClient.enrichBundleWithContextInformation(
            TOKEN, notification.getComposition().getIdPart()))
        .thenReturn(response);
    when(fhirParser.parseFromJson(eq(response))).thenReturn(provenance);

    underTest.enrichBundleWithContextInformation(notification, TOKEN);
    assertAll(
        () ->
            verify(notificationUpdateService)
                .addEntry(eq(notification.getBundle()), entityComponentCaptor.capture()),
        () ->
            assertThat(entityComponentCaptor.getValue().getResource())
                .usingRecursiveComparison()
                .isEqualTo(provenance),
        () ->
            assertThat(entityComponentCaptor.getValue().getFullUrl())
                .isEqualTo(
                    "https://demis.rki.de/fhir/Provenance/0161eba5-e6b2-401f-8966-2d1559abca56"),
        () -> assertThat(entityComponentCaptor.getValue().getFullUrl()).contains("Provenance"));
  }
}

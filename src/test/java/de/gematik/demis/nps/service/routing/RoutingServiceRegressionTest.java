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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.demis.notification.builder.demis.fhir.notification.types.NotificationCategory;
import de.gematik.demis.nps.base.util.SequencedSets;
import de.gematik.demis.nps.error.NpsServiceException;
import de.gematik.demis.nps.error.ServiceCallErrorCode;
import de.gematik.demis.nps.service.notification.NotificationType;
import de.gematik.demis.service.base.error.ServiceCallException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

class RoutingServiceRegressionTest {

  private static final NRSRoutingInput REQUEST = new NRSRoutingInput("", true, "");
  private static final List<NotificationReceiver> VALID_RECEIVERS =
      List.of(new NotificationReceiver("", "", SequencedSets.of(), false));
  private final NotificationRoutingServiceClient mock =
      mock(NotificationRoutingServiceClient.class);
  final RoutingService routingService = new RoutingService(mock, false);

  @MethodSource("invalidResponses")
  @ParameterizedTest
  void thatInvalidResponseRaisesException(final NRSRoutingResponse response) {

    when(mock.ruleBased(anyString(), anyBoolean(), anyString())).thenReturn(response);

    assertThatExceptionOfType(NpsServiceException.class)
        .isThrownBy(() -> routingService.getRoutingInformation(REQUEST));
  }

  @Test
  void that422StatusCodeRaisesServiceCallException() {
    when(mock.ruleBased(anyString(), anyBoolean(), anyString()))
        .thenThrow(
            new ServiceCallException(
                "", ServiceCallErrorCode.NRS, HttpStatus.UNPROCESSABLE_ENTITY.value(), null));

    assertThatExceptionOfType(ServiceCallException.class)
        .isThrownBy(() -> routingService.getRoutingInformation(REQUEST));
  }

  @Test
  void thatUnexpectedStatusCodeRaisesException() {
    when(mock.ruleBased(anyString(), anyBoolean(), anyString()))
        .thenThrow(
            new ServiceCallException(
                "", ServiceCallErrorCode.NRS, HttpStatus.INTERNAL_SERVER_ERROR.value(), null));
    assertThatExceptionOfType(ServiceCallException.class)
        .isThrownBy(() -> routingService.getRoutingInformation(REQUEST));
  }

  @Test
  void thatValidResponseReturnsRoutingData() {

    when(mock.ruleBased(anyString(), anyBoolean(), anyString()))
        .thenReturn(
            new NRSRoutingResponse(
                NotificationType.LABORATORY,
                NotificationCategory.P_7_1,
                SequencedSets.of(),
                VALID_RECEIVERS,
                Map.of(),
                "1.",
                Set.of(),
                null));

    assertThat(routingService.getRoutingInformation(REQUEST)).isNotNull();
  }

  @Test
  void thatServiceCanDealWithAbsentAllowedRolesField() {
    // duplicate here because in the legacy mode we will always receive null, but later on we still
    // need to deal
    // with potential null values
    when(mock.ruleBased(anyString(), anyBoolean(), anyString()))
        .thenReturn(
            new NRSRoutingResponse(
                NotificationType.LABORATORY,
                NotificationCategory.P_7_1,
                SequencedSets.of(),
                VALID_RECEIVERS,
                Map.of(),
                "1.",
                null,
                null));
    assertThat(routingService.getRoutingInformation(REQUEST).allowedRoles()).isEmpty();
  }

  private static Stream<NRSRoutingResponse> invalidResponses() {
    return Stream.of(
        new NRSRoutingResponse(
            NotificationType.LABORATORY,
            NotificationCategory.P_7_1,
            SequencedSets.of(),
            VALID_RECEIVERS,
            Map.of(),
            "",
            Set.of(),
            null),
        new NRSRoutingResponse(
            NotificationType.LABORATORY,
            NotificationCategory.P_7_1,
            SequencedSets.of(),
            VALID_RECEIVERS,
            null,
            "1.",
            Set.of(),
            null),
        new NRSRoutingResponse(
            NotificationType.LABORATORY,
            NotificationCategory.P_7_1,
            SequencedSets.of(),
            List.of(),
            Map.of(),
            "1.",
            Set.of(),
            null));
  }
}

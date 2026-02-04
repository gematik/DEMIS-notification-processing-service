package de.gematik.demis.nps.service.routing;

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

import com.google.common.base.Strings;
import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.error.NpsServiceException;
import de.gematik.demis.service.base.error.ServiceCallException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RoutingService {

  private final NotificationRoutingServiceClient client;

  public RoutingService(final NotificationRoutingServiceClient client) {
    this.client = client;
  }

  @Nonnull
  public RoutingData getRoutingInformation(@Nonnull final NRSRoutingInput request) {

    final NRSRoutingResponse response = fetchRoutingResponse(request);
    log.info("Route to '{}'. NRS response = {}", response.responsible(), response);
    validateResponse(response);
    final Map<AddressOriginEnum, String> healthOffices =
        Objects.requireNonNull(response.healthOffices());
    final String responsible = Strings.nullToEmpty(response.responsible());
    final Set<String> allowedRoles = Objects.requireNonNullElse(response.allowedRoles(), Set.of());
    return new RoutingData(
        response.type(),
        response.notificationCategory(),
        response.bundleActions(),
        response.routes(),
        healthOffices,
        responsible,
        allowedRoles,
        response.custodian());
  }

  @Nonnull
  private NRSRoutingResponse fetchRoutingResponse(@Nonnull final NRSRoutingInput request) {
    try {
      return client.ruleBased(
          request.originalNotificationAsJSON(), request.isTestUser(), request.testUserId());
    } catch (final ServiceCallException e) {
      if (e.getHttpStatus() == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
        throw new NpsServiceException(
            ErrorCode.MISSING_RESPONSIBLE, "no health office is responsible");
      }
      throw e;
    }
  }

  private static void validateResponse(@Nonnull final NRSRoutingResponse response) {
    final Map<AddressOriginEnum, String> healthOffices = response.healthOffices();
    final String responsible = Strings.nullToEmpty(response.responsible());
    if (responsible.isBlank() || response.routes().isEmpty() || healthOffices == null) {
      throw new NpsServiceException(
          ErrorCode.MISSING_RESPONSIBLE, "no health office is responsible");
    }
  }
}

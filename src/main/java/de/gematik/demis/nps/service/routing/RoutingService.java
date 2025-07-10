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

import com.google.common.base.Strings;
import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.error.NpsServiceException;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoutingService {

  private final NotificationRoutingServiceClient client;

  @Nonnull
  public RoutingData getRoutingInformation(@Nonnull final NRSRoutingInput request) {
    final NRSRoutingResponse response =
        client.ruleBased(
            request.originalNotificationAsJSON(), request.isTestUser(), request.testUserId());
    log.info("Route to '{}'. NRS response = {}", response.responsible(), response);
    final Map<AddressOriginEnum, String> healthOffices = response.healthOffices();
    final String responsible = Strings.nullToEmpty(response.responsible());
    if (responsible.isBlank() || response.routes().isEmpty() || healthOffices == null) {
      throw new NpsServiceException(
          ErrorCode.MISSING_RESPONSIBLE, "no health office is responsible");
    }

    return new RoutingData(
        response.type(),
        response.notificationCategory(),
        response.bundleActions(),
        response.routes(),
        healthOffices,
        responsible);
  }
}

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
 * #L%
 */

import static de.gematik.demis.nps.base.profile.DemisSystems.*;

import de.gematik.demis.fhirparserlibrary.FhirParser;
import de.gematik.demis.nps.config.TestUserConfiguration;
import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.error.NpsServiceException;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.service.notification.NotificationUpdateService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoutingService {

  private final NotificationRoutingServiceClient client;
  private final NotificationUpdateService updateService;
  private final TestUserConfiguration testUserConfiguration;
  private final FhirParser fhirParser;

  private static String toResponsibleCodingSystem(final AddressOriginEnum addressOrigin) {
    return switch (addressOrigin) {
      case NOTIFIED_PERSON_PRIMARY -> HEALTH_DEPARTMENT_FOR_PRIMARY_ADDRESS_CODING_SYSTEM;
      case NOTIFIED_PERSON_CURRENT -> HEALTH_DEPARTMENT_FOR_CURRENT_ADDRESS_CODING_SYSTEM;
      case NOTIFIED_PERSON_ORDINARY -> HEALTH_DEPARTMENT_FOR_ORDINARY_ADDRESS_CODING_SYSTEM;
      case NOTIFIED_PERSON_OTHER -> null;
      case NOTIFIER -> NOTIFIER_HEALTH_DEPARTMENT_CODING_SYSTEM;
      case SUBMITTER -> SUBMITTER_HEALTH_DEPARTMENT_CODING_SYSTEM;
    };
  }

  /**
   * set bundle.meta.CodeSystem/ResponsibleDepartment and add
   * bundle.meta.CodeSystem/ResponsibleDepartmentPrimaryAddress for each address type
   *
   * @param notification
   */
  @Deprecated
  public void determineHealthOfficesAndAddToNotification(final Notification notification) {
    final Bundle bundle = notification.getBundle();
    final LegacyRoutingOutputDto routingOutput = client.determineRouting(toJson(bundle));

    final String responsibleHealthOffice =
        getResponsibleHealthOffice(notification, routingOutput.getResponsible());
    log.info("Route to '{}'. NRS response = {}", responsibleHealthOffice, routingOutput);

    if (StringUtils.isBlank(responsibleHealthOffice)) {
      throw new NpsServiceException(
          ErrorCode.MISSING_RESPONSIBLE, "no health office is responsible");
    }

    setResponsibleDepartment(bundle, responsibleHealthOffice);
    addHealthOfficeTags(bundle, routingOutput.getHealthOffices());
  }

  public void setResponsibleHealthOffice(final Notification notification) {
    final String healthOffice = notification.getRoutingOutputDto().responsible();
    setResponsibleDepartment(notification.getBundle(), healthOffice);
  }

  public void setHealthOfficeTags(final Notification notification) {
    addHealthOfficeTags(
        notification.getBundle(), notification.getRoutingOutputDto().healthOffices());
  }

  private String toJson(final Bundle bundle) {
    return fhirParser.encodeToJson(bundle);
  }

  /**
   * Allows to substitute the originally responsible health office to be substituted with test data.
   */
  private String getResponsibleHealthOffice(
      final Notification notification, final String originalResponsibleHealthOffice) {
    if (notification.isTestUser()) {
      return testUserConfiguration.getReceiver(notification.getSender());
    } else {
      return originalResponsibleHealthOffice;
    }
  }

  public void addHealthOfficeTags(
      final Bundle notification, final Map<AddressOriginEnum, String> healthOffices) {
    if (healthOffices == null || healthOffices.isEmpty()) {
      return;
    }

    healthOffices.forEach(
        (addressOrigin, healthOffice) ->
            addHealthOfficeTag(notification, addressOrigin, healthOffice));
  }

  private void addHealthOfficeTag(
      final Bundle notification, final AddressOriginEnum addressOrigin, final String healthOffice) {
    final String codingSystem = toResponsibleCodingSystem(addressOrigin);
    if (codingSystem != null) {
      updateService.addMetaTag(notification, codingSystem, healthOffice);
    }
  }

  private void setResponsibleDepartment(
      final Bundle notification, final String responsibleHealthOffice) {
    updateService.addOrReplaceMetaTag(
        notification, RESPONSIBLE_HEALTH_DEPARTMENT_CODING_SYSTEM, responsibleHealthOffice);
  }

  public RoutingOutputDto getRoutingInformation(final NRSRoutingInput request) {
    final RoutingOutputDto routingOutputDto =
        client.ruleBased(
            request.originalNotificationAsJSON(), request.isTestUser(), request.testUserId());
    log.info("Route to '{}'. NRS response = {}", routingOutputDto.responsible(), routingOutputDto);
    if (StringUtils.isBlank(routingOutputDto.responsible())
        || routingOutputDto.routes().isEmpty()) {
      throw new NpsServiceException(
          ErrorCode.MISSING_RESPONSIBLE, "no health office is responsible");
    }
    return routingOutputDto;
  }
}

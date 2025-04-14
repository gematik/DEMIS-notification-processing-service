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

import de.gematik.demis.notification.builder.demis.fhir.notification.utils.Bundles;
import de.gematik.demis.nps.base.profile.DemisSystems;
import de.gematik.demis.nps.service.routing.RoutingOutputDto;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.CheckForNull;
import lombok.Builder;
import lombok.Data;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;

@Data
@Builder
public class Notification {

  private Bundle bundle;
  private NotificationType type;
  private String sender;
  private boolean testUser;
  private String diseaseCode;
  private String originalNotificationAsJson;
  // Check for null can be removed once 7.4 OR 7.3 is available
  @CheckForNull private RoutingOutputDto routingOutputDto;

  public String getBundleIdentifier() {
    return Optional.ofNullable(bundle.getIdentifier())
        .map(Identifier::getValue)
        .orElseThrow(
            () -> new IllegalStateException("validated notification has no bundle identifier"));
  }

  public Optional<String> getCompositionIdentifier() {
    return Bundles.compositionFrom(bundle)
        .map(Composition::getIdentifier)
        .map(Identifier::getValue);
  }

  public Patient getNotifiedPerson() {
    final Optional<Patient> patient = Bundles.subjectFrom(bundle);
    return patient.orElseThrow(
        () ->
            new IllegalStateException(
                "validated notification has no Notified Person (Patient resource)"));
  }

  public Optional<String> getResponsibleHealthOfficeId() {
    return bundle.getMeta().getTag().stream()
        .filter(t -> DemisSystems.RESPONSIBLE_HEALTH_DEPARTMENT_CODING_SYSTEM.equals(t.getSystem()))
        .map(Coding::getCode)
        .filter(Objects::nonNull)
        .findFirst();
  }

  public Composition getComposition() {
    return Bundles.compositionFrom(bundle)
        .orElseThrow(() -> new IllegalStateException("Validated notification has no Composition"));
  }
}

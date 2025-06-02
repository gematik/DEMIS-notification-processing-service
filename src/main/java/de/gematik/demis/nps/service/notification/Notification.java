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

import com.google.common.base.Strings;
import de.gematik.demis.notification.builder.demis.fhir.notification.utils.Compositions;
import de.gematik.demis.notification.builder.demis.fhir.notification.utils.Patients;
import de.gematik.demis.nps.base.profile.DemisSystems;
import de.gematik.demis.nps.service.routing.RoutingOutputDto;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;

@ToString
@EqualsAndHashCode
@Getter
public class Notification {

  public static NotificationBuilder builder() {
    return new NotificationBuilder();
  }

  /**
   * Use {@link NotificationBuilder} to create a new instance. The builder will ensure that a valid
   * Notification is created, so that other parts of the code can rely on that.
   */
  private Notification(
      final Bundle bundle,
      final NotificationType type,
      final String sender,
      final boolean testUser,
      @Nonnull final String testUserRecipient,
      final String diseaseCode,
      final String originalNotificationAsJson,
      final String reparsedNotification,
      @CheckForNull final RoutingOutputDto routingOutputDto) {
    this.bundle = bundle;
    this.type = type;
    this.sender = sender;
    this.testUser = testUser;
    this.testUserRecipient = testUserRecipient;
    this.diseaseCode = diseaseCode;
    this.originalNotificationAsJson = originalNotificationAsJson;
    this.reparsedNotification = reparsedNotification;
    this.routingOutputDto = routingOutputDto;
  }

  @Setter private Bundle bundle;
  @Setter private NotificationType type;
  private final String sender;
  private final boolean testUser;

  /** In case of testUser=true, defines who is supposed to receive the test notification. */
  @Nonnull private final String testUserRecipient;

  @Setter private String diseaseCode;
  private final String originalNotificationAsJson;
  @Setter private String reparsedNotification;
  // Check for null can be removed once 7.4 OR 7.3 is available
  @Setter @CheckForNull private RoutingOutputDto routingOutputDto;

  public boolean wasReparsed() {
    return reparsedNotification != null && !reparsedNotification.isBlank();
  }

  public String getBundleIdentifier() {
    return Optional.ofNullable(bundle.getIdentifier())
        .map(Identifier::getValue)
        .orElseThrow(
            () -> new IllegalStateException("validated notification has no bundle identifier"));
  }

  public Optional<String> getCompositionIdentifier() {
    return Compositions.from(bundle).map(Composition::getIdentifier).map(Identifier::getValue);
  }

  public Patient getNotifiedPerson() {
    final Optional<Patient> patient = Patients.subjectFrom(bundle);
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
    return Compositions.from(bundle)
        .orElseThrow(() -> new IllegalStateException("Validated notification has no Composition"));
  }

  public static class NotificationBuilder {

    private boolean isTestUser;
    @CheckForNull private Bundle bundle;
    @CheckForNull private String json;
    @CheckForNull private String sender;
    @CheckForNull private String testUserRecipient;
    @CheckForNull private NotificationType type;
    @CheckForNull private String diseaseCode;
    @CheckForNull private RoutingOutputDto routingOutputDto;
    private String reparsedNotification;

    public NotificationBuilder routingOutputDto(@Nonnull final RoutingOutputDto routingOutputDto) {
      this.routingOutputDto = routingOutputDto;
      return this;
    }

    public NotificationBuilder diseaseCode(@Nonnull final String diseaseCode) {
      this.diseaseCode = diseaseCode;
      return this;
    }

    public NotificationBuilder bundle(@Nonnull final Bundle bundle) {
      this.bundle = bundle;
      return this;
    }

    public NotificationBuilder type(@Nonnull final NotificationType type) {
      this.type = type;
      return this;
    }

    public NotificationBuilder originalNotificationAsJson(@Nonnull final String json) {
      this.json = json;
      return this;
    }

    public NotificationBuilder sender(@Nonnull final String sender) {

      this.sender = sender;
      return this;
    }

    public NotificationBuilder testUser(final boolean testUser) {
      this.isTestUser = testUser;
      return this;
    }

    public NotificationBuilder testUserRecipient(@Nonnull final String testUserRecipient) {
      this.testUserRecipient = testUserRecipient;
      return this;
    }

    public NotificationBuilder reparsedNotification(@Nonnull final String reparsedNotification) {
      this.reparsedNotification = reparsedNotification;
      return this;
    }

    @Nonnull
    public Notification build() {
      if (isTestUser && (testUserRecipient == null || testUserRecipient.isBlank())) {
        // ensure we comply with business rules: if we have a test notification, we need a test
        // recipient, otherwise
        // we don't know where to send!
        throw new IllegalStateException("testUserRecipient required when isTestUser == true!");
      } else if (!isTestUser) {
        // ensure we comply with null-ness annotation
        testUserRecipient = Strings.nullToEmpty(testUserRecipient);
      }

      return new Notification(
          bundle,
          type,
          sender,
          isTestUser,
          testUserRecipient,
          diseaseCode,
          json,
          reparsedNotification,
          routingOutputDto);
    }
  }
}

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
 * For additional notes and disclaimer from gematik and in case of changes by gematik,
 * find details in the "Readme" file.
 * #L%
 */

import com.google.common.base.Strings;
import de.gematik.demis.notification.builder.demis.fhir.notification.utils.Compositions;
import de.gematik.demis.notification.builder.demis.fhir.notification.utils.Patients;
import de.gematik.demis.nps.base.profile.DemisSystems;
import de.gematik.demis.nps.service.routing.RoutingData;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
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
   *
   * @param diseaseCode The raw disease code as extracted from the underlying FHIR resource.
   * @param diseaseCodeRoot The root of the disease code, e.g. cvd for cvdd/cvdp. {@link
   *     NotificationFhirService#getDiseaseCodeRoot(Bundle, NotificationType)}
   */
  private Notification(
      final Bundle bundle,
      final NotificationType type,
      final String sender,
      final boolean testUser,
      @Nonnull final String testUserRecipient,
      final String diseaseCode,
      final String diseaseCodeRoot,
      final String originalNotificationAsJson,
      @Nonnull final RoutingData routingData) {
    this.bundle = bundle;
    this.type = type;
    this.sender = sender;
    this.testUser = testUser;
    this.testUserRecipient = testUserRecipient;
    this.diseaseCode = diseaseCode;
    this.diseaseCodeRoot = diseaseCodeRoot;
    this.originalNotificationAsJson = originalNotificationAsJson;
    this.routingData = routingData;
    this.preEncryptedBundles = new HashMap<>();
  }

  private final Bundle bundle;
  private final NotificationType type;
  private final String sender;
  private final boolean testUser;
  private Map<String, Bundle> preEncryptedBundles;

  public void putPreEncryptedBundle(String receiverId, Bundle bundle) {
    this.preEncryptedBundles.put(receiverId, bundle);
  }

  /** In case of testUser=true, defines who is supposed to receive the test notification. */
  @Nonnull private final String testUserRecipient;

  private final String diseaseCode;
  private final String diseaseCodeRoot;
  private final String originalNotificationAsJson;
  @Nonnull private final RoutingData routingData;

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
    @CheckForNull private String diseaseCode;
    @CheckForNull private String diseaseCodeRoot;
    @CheckForNull private RoutingData routingData;
    @CheckForNull private NotificationType type;

    public NotificationBuilder routingData(@Nonnull final RoutingData routingData) {
      this.routingData = routingData;
      return this;
    }

    @Nonnull
    public NotificationBuilder diseaseCode(@Nonnull final String diseaseCode) {
      this.diseaseCode = diseaseCode;
      return this;
    }

    public NotificationBuilder diseaseCodeRoot(@Nonnull final String diseaseCodeRoot) {
      this.diseaseCodeRoot = diseaseCodeRoot;
      return this;
    }

    public NotificationBuilder bundle(@Nonnull final Bundle bundle) {
      this.bundle = bundle;
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

    public NotificationBuilder type(@Nonnull final NotificationType type) {
      this.type = type;
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

      Objects.requireNonNull(routingData, "RoutingData must be set");

      return new Notification(
          bundle,
          routingData.type(),
          sender,
          isTestUser,
          testUserRecipient,
          diseaseCode,
          diseaseCodeRoot,
          json,
          routingData);
    }
  }
}

package de.gematik.demis.nps.base.profile;

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

import ca.uhn.fhir.model.api.annotation.ResourceDef;
import java.util.Objects;
import org.hl7.fhir.r4.model.*;

/**
 * Provides a set of static final fields that allow checking resources for an applied profile and
 * applying the profile to an element.
 */
public final class DemisProfiles {

  public static final String PROFILE_BASE_URL = "https://demis.rki.de/fhir/";

  private DemisProfiles() {
    throw new IllegalStateException();
  }

  /**
   * This class wraps around a profile URL and allows checking if resources of type {@code T} have
   * the profile applied and applying the profile to an instance of a resource of type {@code T}.
   *
   * @param <T> type of resource on which the profile can be applied
   */
  public static class Profile<T extends Resource> {

    private Profile(String url) {
      Objects.requireNonNull(url, "url");
      this.url = url;
    }

    private final String url;

    /**
     * Returns the profile URL
     *
     * @return profile URL
     */
    public String getUrl() {
      return url;
    }

    /**
     * Checks if resource {@code t} has the profile applied and if not applies it.
     *
     * @param t the resource to be tested
     * @return {@code true} if profile has been applied to {@code t}
     */
    public boolean applyIfNotApplied(T t) {
      if (!isApplied(t)) {
        applyTo(t);
        return true;
      }
      return false;
    }

    /**
     * Checks if resource {@code t} has the profile applied.
     *
     * @param t the resource to be tested
     * @return {@code true} if {@code t} has the profile applied
     */
    public boolean isApplied(T t) {
      return DemisProfiles.isApplied(t, url);
    }

    /**
     * Removes all applied profiles and applies the URL wrapped by this object as the profile on the
     * give resource {@code t}.
     *
     * @param t the resource on which the profile will be applied
     */
    public void applyTo(T t) {
      t.getMeta().addProfile(url);
    }

    public void clearAndApplyProfileTo(T t) {
      t.getMeta().getProfile().clear();
      applyTo(t);
    }

    @Override
    public String toString() {
      return "Profile [url=" + url + "]";
    }
  }

  /**
   * Utility method testing if the profile with the given {@code profileUrl} is applied to the given
   * {@code resource}.
   *
   * @param resource
   * @param profileUrl
   * @return {@code true} if profile with {@code profileUrl} is applied to {@code resource}, {@code
   *     false} otherwise.
   */
  public static boolean isApplied(Resource resource, String profileUrl) {
    if (profileUrl == null) {
      return false;
    }
    boolean metaMatch =
        resource.getMeta().getProfile().stream()
            .map(PrimitiveType::getValue)
            .anyMatch(profileUrl::equals);
    if (metaMatch) {
      return true;
    }
    ResourceDef resDef = resource.getClass().getAnnotation(ResourceDef.class);
    return resDef != null && profileUrl.equals(resDef.profile());
  }

  public static <T extends Resource> Profile<T> profile(String url) {
    return new Profile<>(url);
  }

  public static final Profile<Condition> CONDITION_DISEASE_CVDD_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/DiseaseCVDD");

  public static final Profile<DiagnosticReport> LABORATORY_REPORT_CVDP_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/LaboratoryReportCVDP");
  public static final Profile<Organization> SUBMITTING_FACILITY_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/SubmittingFacility");
  public static final Profile<Practitioner> SUBMITTING_PERSON_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/SubmittingPerson");
  public static final Profile<PractitionerRole> SUBMITTING_ROLE_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/SubmittingRole");
  public static final Profile<Organization> NOTIFIER_FACILITY_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/NotifierFacility");
  public static final Profile<Practitioner> NOTIFIER_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/Notifier");
  public static final Profile<PractitionerRole> NOTIFIER_ROLE_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/NotifierRole");
  public static final Profile<Bundle> NOTIFICATION_BUNDLE_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/NotificationBundle");
  public static final Profile<Bundle> NOTIFICATION_BUNDLE_LABORATORY_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/NotificationBundleLaboratory");
  public static final Profile<Composition> NOTIFICATION_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/Notification");
  public static final Profile<Composition> NOTIFICATION_LABORATORY_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/NotificationLaboratory");
  public static final Profile<Composition> NOTIFICATION_SARS_COV_2_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/NotificationLaboratorySARSCoV2");

  public static final Profile<Bundle> NOTIFICATION_BUNDLE_DISEASE_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/NotificationBundleDisease");
  public static final Profile<QuestionnaireResponse> DISEASE_INFORMATION =
      profile(PROFILE_BASE_URL + "StructureDefinition/DiseaseInformation");
  public static final Profile<QuestionnaireResponse> DISEASE_INFORMATION_COMMON =
      profile(PROFILE_BASE_URL + "StructureDefinition/DiseaseInformationCommon");
  public static final Profile<QuestionnaireResponse> DISEASE_INFORMATION_CVDD =
      profile(PROFILE_BASE_URL + "StructureDefinition/DiseaseInformationCVDD");

  public static final Profile<Composition> DISEASE_SARS_COV_2_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/NotificationDiseaseCVDD");

  public static final Profile<Observation> PATHOGEN_DETECTION_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/PathogenDetection");
  public static final Profile<Observation> PATHOGEN_DETECTION_SARS_COV_2_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/PathogenDetectionSARSCoV2");
  public static final Profile<Condition> DIAGNOSE_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/Diagnose");
  public static final Profile<Condition> DIAGNOSE_SARS_COV_2_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/DiagnoseSARSCoV2");
  public static final Profile<Specimen> SPECIMEN_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/Specimen");
  public static final Profile<Specimen> SPECIMEN_SARS_COV_2_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/SpecimenSARSCoV2");

  public static final Profile<Patient> NOTIFIED_PERSON_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/NotifiedPerson");
  public static final Profile<Organization> NOTIFIED_PERSON_FACILITY_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/NotifiedPersonFacility");
  public static final Profile<Bundle> RECEIPT_BUNDLE_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/ReceiptBundle");
  public static final Profile<OperationOutcome> PROCESS_NOTIFICATION_RESPONSE_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/ProcessNotificationResponse");
  public static final Profile<Parameters> PROCESS_NOTIFICATION_RESPONSE_PARAMETER =
      profile(PROFILE_BASE_URL + "StructureDefinition/ProcessNotificationResponseParameters");
  public static final Profile<Composition> NOTIFICATION_RECEIPT_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/NotificationReceipt");

  public static final Profile<Patient> NOTIFIED_PERSON_NOT_BY_NAME_PROFILE =
      profile(PROFILE_BASE_URL + "StructureDefinition/NotifiedPersonNotByName");

  public static final Profile<Bundle> NOTIFICATION_BUNDLE_LABORATORY_NOT_BY_NAME =
      profile(PROFILE_BASE_URL + "StructureDefinition/NotificationBundleLaboratoryNotByName");

  public static final Profile<Composition> NOTIFICATION_LABORATORY_NOT_BY_NAME =
      profile(PROFILE_BASE_URL + "StructureDefinition/NotificationLaboratoryNotByName");
}

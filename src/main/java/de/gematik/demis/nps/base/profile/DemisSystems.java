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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import lombok.experimental.UtilityClass;

/**
 * Collection of systems that are used in the application. The systems are applied to
 * interpretations, codings and others. Keeping all systems (URLs) at one place allows for easier
 * search and navigation.
 */
@UtilityClass
public class DemisSystems {

  public static final String POSTAL_CODE_CODE_SYSTEM_URL =
      DemisProfiles.PROFILE_BASE_URL + "CodeSystem/postalCode";

  public static final String RESPONSE_COMPOSITION_SYSTEM = "http://loinc.org";

  /**
   * Used for tagging unnamed notification created by a test user in order to help RKI with fitering
   * of test messages
   */
  public static final String TEST_USER_CODING_SYSTEM =
      DemisProfiles.PROFILE_BASE_URL + "CodeSystem/TestUser";

  public static final String TEST_USER_CODE = "testuser";

  /** URL for tag to notification the resource relates to */
  public static final String RELATED_NOTIFICATION_CODING_SYSTEM =
      DemisProfiles.PROFILE_BASE_URL + "CodeSystem/RelatedNotificationBundle";

  /**
   * System for specifying the observation results. Valid Codes are listed here -> {@link
   * PathogenDetectionInterpretation}.
   */
  public static final String OBSERVATION_INTERPRETATION_SYSTEM =
      "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation";

  /** System to tag sender */
  public static final String SENDER_ID_SYSTEM =
      DemisProfiles.PROFILE_BASE_URL + "NamingSystem/SendingServiceProvider";

  /** System for addressType coding */
  // TODO: Define coding systems
  public static final String ADDRESS_USE_SYSTEM =
      DemisProfiles.PROFILE_BASE_URL + "CodeSystem/addressUse";

  public static final String HEALTH_DEPARTMENT_FOR_PRIMARY_ADDRESS_CODING_SYSTEM =
      DemisProfiles.PROFILE_BASE_URL + "CodeSystem/ResponsibleDepartmentPrimaryAddress";
  public static final String RESPONSIBLE_HEALTH_DEPARTMENT_CODING_SYSTEM =
      DemisProfiles.PROFILE_BASE_URL + "CodeSystem/ResponsibleDepartment";
  public static final String SUBMITTER_HEALTH_DEPARTMENT_CODING_SYSTEM =
      DemisProfiles.PROFILE_BASE_URL + "CodeSystem/ResponsibleDepartmentSubmitter";
  public static final String NOTIFIER_HEALTH_DEPARTMENT_CODING_SYSTEM =
      DemisProfiles.PROFILE_BASE_URL + "CodeSystem/ResponsibleDepartmentNotifier";
  public static final String REPORTING_SITE_CODING_SYSTEM =
      DemisProfiles.PROFILE_BASE_URL + "CodeSystem/reportingSite";
  public static final String HEALTH_DEPARTMENT_FOR_CURRENT_ADDRESS_CODING_SYSTEM =
      DemisProfiles.PROFILE_BASE_URL + "CodeSystem/ResponsibleDepartmentCurrentAddress";
  public static final String HEALTH_DEPARTMENT_FOR_ORDINARY_ADDRESS_CODING_SYSTEM =
      DemisProfiles.PROFILE_BASE_URL + "CodeSystem/ResponsibleDepartmentOrdinaryAddress";

  public static final String GEOGRAPHIC_REGION_CODING_SYSTEM =
      DemisProfiles.PROFILE_BASE_URL + "CodeSystem/geographicRegion";

  /**
   * System of the identifier written to the notification bundle when calling {@link
   * de.rki.demis.fhir.provider.enrichment.NotificationEnrichment#enrichNotification(PreProcessedBundle)}.
   */
  public static final String NOTIFICATION_BUNDLE_IDENTIFIER_SYSTEM =
      DemisProfiles.PROFILE_BASE_URL + "NamingSystem/NotificationBundleId";

  public static final String NOTIFICATION_IDENTIFIER_SYSTEM =
      DemisProfiles.PROFILE_BASE_URL + "NamingSystem/NotificationId";

  // Value sets
  public static final String LABORATORY_TEST_VALUE_SET_SYSTEM =
      DemisProfiles.PROFILE_BASE_URL + "ValueSet/laboratoryTestSARSCoV2";

  public static final String LABORATORY_TEST_INVP_SYSTEM =
      DemisProfiles.PROFILE_BASE_URL + "ValueSet/laboratoryTestINVP";
  public static final String MATERIAL_SARSCOV2_VALUE_SET_SYSTEM =
      DemisProfiles.PROFILE_BASE_URL + "ValueSet/materialSARSCoV2";
  public static final String COUNTRY_VALUE_SET_SYSTEM =
      DemisProfiles.PROFILE_BASE_URL + "ValueSet/country";

  public static final String VACCINATION_INDICATION_VALUE_SET_SYSTEM =
      DemisProfiles.PROFILE_BASE_URL + "ValueSet/vaccinationIndication";
  public static final String OBSERVATION_METHOD =
      DemisProfiles.PROFILE_BASE_URL + "ValueSet/methodSARSCoV2";
  public static final String ORGANIZATION_TYPE =
      DemisProfiles.PROFILE_BASE_URL + "ValueSet/organizationType";
  public static final String ADDRESS_USE_VALUE_SET =
      DemisProfiles.PROFILE_BASE_URL + "ValueSet/addressUse";
  public static final String NOTIFICATION_CATEGORY_VALUE_SET =
      DemisProfiles.PROFILE_BASE_URL + "ValueSet/notificationCategory";
  public static final String CONCLUSION_CODE_VALUE_SET =
      DemisProfiles.PROFILE_BASE_URL + "ValueSet/conclusionCode";
  public static final String DIAGNOSTIC_REPORT_STATUS_VALUE_SET =
      "http://hl7.org/fhir/StructureDefinition/shareablevalueset";

  // Naming systems
  public static final String BSNR_ORGANIZATION_ID =
      "https://fhir.kbv.de/NamingSystem/KBV_NS_Base_BSNR";
  public static final String DEMIS_LABORATORY_ID =
      DemisProfiles.PROFILE_BASE_URL + "NamingSystem/DemisLaboratoryId";
}

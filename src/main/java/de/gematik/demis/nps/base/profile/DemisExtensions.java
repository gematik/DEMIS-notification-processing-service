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

/** Holds the URLs of all FHIR extensions used in the Notification API */
@UtilityClass
public class DemisExtensions {
  // DEMIS

  public static final String EXTENSION_URL_SALUTATION =
      DemisProfiles.PROFILE_BASE_URL + "StructureDefinition/Salutation";

  public static final String EXTENSION_URL_ADDRESS_USE =
      DemisProfiles.PROFILE_BASE_URL + "StructureDefinition/AddressUse";

  public static final String EXTENSION_URL_COMMENT =
      DemisProfiles.PROFILE_BASE_URL + "StructureDefinition/Comment";

  public static final String EXTENSION_URL_TRANSACTION_ID =
      DemisProfiles.PROFILE_BASE_URL + "StructureDefinition/TransactionID";

  public static final String EXTENSION_URL_FACILTY_ADDRESS_NOTIFIED_PERSON =
      DemisProfiles.PROFILE_BASE_URL + "StructureDefinition/FacilityAddressNotifiedPerson";

  // added extension by NES
  public static final String EXTENSION_URL_RECEIVED_NOTIFICATION =
      DemisProfiles.PROFILE_BASE_URL + "StructureDefinition/ReceivedNotification";

  // added extension by NES
  public static final String EXTENSION_URL_PSEUDONYM =
      DemisProfiles.PROFILE_BASE_URL + "StructureDefinition/PseudonymRecordType";

  // added extension by NES
  public static final String EXTENSION_URL_RECEPTION_TIME_STAMP_TYPE =
      DemisProfiles.PROFILE_BASE_URL + "StructureDefinition/ReceptionTimeStampType";

  // DEMIS Arztmeldung

  public static final String EXTENSION_URL_HOSPITALIZATION_NOTE =
      DemisProfiles.PROFILE_BASE_URL + "StructureDefinition/HospitalizationNote";

  public static final String EXTENSION_URL_HOSPITALIZATION_REGION =
      DemisProfiles.PROFILE_BASE_URL + "StructureDefinition/HospitalizationRegion";

  // General

  public static final String EXTENSION_URL_PRECINCT =
      "http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-precinct";

  public static final String EXTENSION_URL_STREET_NAME =
      "http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-streetName";

  public static final String EXTENSION_URL_HOUSE_NUMBER =
      "http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-houseNumber";

  public static final String EXTENSION_URL_ADDITIONAL_LOCATOR =
      "http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-additionalLocator";

  public static final String EXTENSION_URL_POST_BOX =
      "http://hl7.org/fhir/StructureDefinition/iso21090-ADXP-postBox";

  public static final String EXTENSION_URL_HUMANNAME_NAMENSZUSATZ =
      "http://fhir.de/StructureDefinition/humanname-namenszusatz";

  public static final String EXTENSION_URL_HUMANNAME_OWN_NAME =
      "http://hl7.org/fhir/StructureDefinition/humanname-own-name";

  public static final String EXTENSION_URL_HUMANNAME_OWN_PREFIX =
      "http://hl7.org/fhir/StructureDefinition/humanname-own-prefix";

  public static final String EXTENSION_URL_HUMANNAME_PREFIX =
      "http://hl7.org/fhir/StructureDefinition/iso21090-EN-qualifier";
}

package de.gematik.demis.nps.service.pseudonymization;

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
import de.gematik.demis.nps.service.notification.Notification;
import java.time.format.DateTimeFormatter;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Patient;

record PseudonymizationRequest(
    String notificationBundleId,
    String type,
    String diseaseCode,
    String familyName,
    String firstName,
    // For date format: dd.MM.yyyy
    String dateOfBirth) {

  private static final String DEFAULT_TYPE = "demisPseudonymizationRequest";

  private static final String GERMAN_DATE_PATTERN = "dd.MM.yyyy";
  private static final DateTimeFormatter SIMPLE_DATE_FORMATTER =
      DateTimeFormatter.ofPattern(GERMAN_DATE_PATTERN);

  public PseudonymizationRequest(
      final String notificationBundleId,
      final String diseaseCode,
      final String familyName,
      final String firstName,
      final String dateOfBirth) {
    this(notificationBundleId, DEFAULT_TYPE, diseaseCode, familyName, firstName, dateOfBirth);
  }

  /**
   * Create a new instance using data from the given notification. If birthday is set, a German date
   * pattern is assumed for parsing.
   */
  public static PseudonymizationRequest from(final Notification notification) {
    final Patient notifiedPerson = notification.getNotifiedPerson();

    final DateType dateOfBirth = notifiedPerson.getBirthDateElement();
    final boolean isDateOfBirthSet = dateOfBirth != null && dateOfBirth.getValue() != null;
    final String formattedDateString =
        isDateOfBirthSet ? DateUtil.format(SIMPLE_DATE_FORMATTER, dateOfBirth) : "";

    final HumanName name = notifiedPerson.getNameFirstRep();
    final String firstName = name.getGivenAsSingleString();
    final String familyName = Strings.nullToEmpty(name.getFamily());

    return new PseudonymizationRequest(
        notification.getBundleIdentifier(),
        notification.getDiseaseCodeRoot(),
        familyName,
        firstName,
        formattedDateString);
  }
}

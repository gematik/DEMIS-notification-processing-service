package de.gematik.demis.nps.service.healthoffice;

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

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Central utility regarding subsidiary health offices (Gesundheitsämter). This feature is used to
 * serve different health office programs (such as SORMAS) with incoming notices.<br>
 * This class provides static members and is not intended for instantiation.
 */
@Service
public final class SubsidiaryService {

  /** Prefix for subsidiary health office (Gesundheitsamt) IDs. */
  private static final String SUBSIDIARY_PREFIX = "2.";

  /** Prefix for central/regular health office (Gesundheitsamt) IDs. */
  private static final String CENTRAL_OFFICE_PREFIX = "1.";

  private static final Pattern CENTRAL_PREFIX_PATTERN =
      Pattern.compile("^" + Pattern.quote(CENTRAL_OFFICE_PREFIX));

  /**
   * Tests if the given {@code healthOfficeId} is an ID of a subsidiary health office.
   *
   * @param healthOfficeId the ID to be checked
   * @return {@code true} if {@code healthOfficeId} is an subsidiary health office ID.
   */
  public boolean isSubsidiary(String healthOfficeId) {
    return !healthOfficeId.startsWith(CENTRAL_OFFICE_PREFIX);
  }

  /**
   * Will return the subsidiary health office ID for the given {@code centralOfficeId}.<br>
   * If this function is not called with a main office ID, the function will return an empty
   * optional.
   *
   * @param centralOfficeId the central/regular health office ID, for which the subsidiary ID shall
   *     be returned.
   * @return subsidiary health office ID for the {@code centralOfficeId}, or empty optional if the
   *     ID does not match a main office ID.
   */
  public Optional<String> getSubsidiaryId(final String centralOfficeId) {
    Matcher matcher = CENTRAL_PREFIX_PATTERN.matcher(centralOfficeId);
    if (matcher.find()) {
      String result = matcher.replaceFirst(SUBSIDIARY_PREFIX);
      return Optional.of(result);
    } else {
      return Optional.empty();
    }
  }
}

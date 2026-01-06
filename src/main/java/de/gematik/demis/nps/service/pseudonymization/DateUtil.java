package de.gematik.demis.nps.service.pseudonymization;

/*-
 * #%L
 * notification-processing-service
 * %%
 * Copyright (C) 2025 - 2026 gematik GmbH
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

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.TimeZone;
import lombok.AllArgsConstructor;
import lombok.experimental.UtilityClass;
import org.hl7.fhir.r4.model.BaseDateTimeType;

@UtilityClass
// TODO 1:1 aus NES übernommen. noch checken
public class DateUtil {

  private static final String TARGET_TIMEZONE_ID = "Europe/Berlin";
  private static final ZoneId TARGET_TIMEZONE = ZoneId.of(TARGET_TIMEZONE_ID);

  /**
   * Formats the given {@code date} (at the target timezone) with the given {@code formatter}. This
   * is done by first converting the given {@code date} to a {@code ZonedDateTime}, then converting
   * the time to the {@link #TARGET_TIMEZONE}. The formatter is then called on the result.
   *
   * @param formatter the formatter to format the given {@code date}
   * @param date the date to be formatted
   * @return the {@code date} at the target timezone as a string representation according to the
   *     given {@code formatter}
   */
  public static String format(DateTimeFormatter formatter, BaseDateTimeType date) {
    var zonedTimeDate = toZonedDateTime(date);
    var germanDate = zonedTimeDate.withZoneSameInstant(TARGET_TIMEZONE);
    return formatter.format(germanDate);
  }

  private static ZonedDateTime toZonedDateTime(BaseDateTimeType source) {
    Date date = source.getValue();
    Instant instant = date.toInstant();
    ZoneResult zoneResult = getTimeZone(source);
    ZonedDateTime targetZoneResult = instant.atZone(zoneResult.zone);
    // We have a different default time zone as the target time zone.
    // This means we have to adjust the time zone (without adjusting the local date),
    // because the original date was created using the default time zone.
    if (zoneResult.adjustToTargetTimeZone) {
      targetZoneResult = targetZoneResult.withZoneSameLocal(TARGET_TIMEZONE);
    }
    return targetZoneResult;
  }

  private static ZoneResult getTimeZone(BaseDateTimeType timestampElement) {
    TimeZone timeZone = timestampElement.getTimeZone();
    if (timeZone == null) {
      Instant instant = timestampElement.getValue().toInstant();
      ZoneId systemDefault = ZoneId.systemDefault();
      boolean adjustToTargetTimeZone = !timeZonesEqual(systemDefault, TARGET_TIMEZONE, instant);
      return new ZoneResult(systemDefault, adjustToTargetTimeZone);
    } else {
      return new ZoneResult(timeZone.toZoneId());
    }
  }

  private static boolean timeZonesEqual(ZoneId a, ZoneId b, Instant instant) {
    ZoneOffset aOffset = a.getRules().getOffset(instant);
    ZoneOffset bOffset = b.getRules().getOffset(instant);
    return aOffset.equals(bOffset);
  }

  /**
   * Returns the target time zone and if the resulting date has to be changed to have the {@link
   * DateUtil#TARGET_TIMEZONE}.
   */
  @AllArgsConstructor
  private static class ZoneResult {
    ZoneResult(ZoneId zone) {
      this(zone, false);
    }

    /** Parsed zone id */
    final ZoneId zone;

    /**
     * Default time zone is not target timezone. Adjust date to target time zone without adjusting
     * the instant.
     */
    final boolean adjustToTargetTimeZone;
  }
}

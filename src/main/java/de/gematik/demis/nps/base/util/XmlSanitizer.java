package de.gematik.demis.nps.base.util;

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

import java.util.Set;

/**
 * Utility for sanitizing XML text by removing Unicode code points that are not allowed in XML 1.0.
 */
public final class XmlSanitizer {

  /**
   * Unicode code points disallowed by the XML 1.0 character set specification.
   *
   * @see <a href="https://www.w3.org/TR/xml/#charsets">XML 1.0 character set</a>
   */
  private static final Set<Integer> invalidCodePoints =
      Set.of(
          0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8, 0xB, 0xC, 0xE, 0xF, 0x10, 0x11, 0x12, 0x13, 0x14,
          0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F, 0x7F, 0x80, 0x81, 0x82,
          0x83, 0x84, 0x86, 0x87, 0x88, 0x89, 0x8A, 0x8B, 0x8C, 0x8D, 0x8E, 0x8F, 0x90, 0x91, 0x092,
          0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9A, 0x9B, 0x9C, 0x9D, 0x9E, 0x9F, 0xFDD0,
          0xFDD1, 0xFDD2, 0xFDD3, 0xFDD4, 0xFDD5, 0xFDD6, 0xFDD7, 0xFDD8, 0xFDD9, 0xFDDA, 0xFDDB,
          0xFDDC, 0xFDDD, 0xFDDE, 0xFDDF, 0x1FFFE, 0x1FFFF, 0x2FFFE, 0x2FFFF, 0x3FFFE, 0x3FFFF,
          0x4FFFE, 0x4FFFF, 0x5FFFE, 0x5FFFF, 0x6FFFE, 0x6FFFF, 0x7FFFE, 0x7FFFF, 0x8FFFE, 0x8FFFF,
          0x9FFFE, 0x9FFFF, 0xAFFFE, 0xAFFFF, 0xBFFFE, 0xBFFFF, 0xCFFFE, 0xCFFFF, 0xDFFFE, 0xDFFFF,
          0xEFFFE, 0xEFFFF, 0xFFFFE, 0xFFFFF, 0x10FFFE, 0x10FFFF);

  private XmlSanitizer() {}

  /**
   * Removes invalid XML 1.0 Unicode code points from the given input.
   *
   * @param xml XML content to sanitize
   * @return sanitized XML content; unchanged input if {@code xml} is {@code null} or empty
   */
  public static String filterInvalidXmlCodePoints(final String xml) {
    if (xml == null || xml.isEmpty()) {
      return xml;
    }
    return xml.codePoints()
        .filter(codePoint -> !invalidCodePoints.contains(codePoint))
        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
        .toString();
  }
}

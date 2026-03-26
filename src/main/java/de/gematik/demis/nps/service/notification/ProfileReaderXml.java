package de.gematik.demis.nps.service.notification;

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

import static de.gematik.demis.nps.service.notification.NotificationTypeResolver.BUNDLE_VALUE;
import static de.gematik.demis.nps.service.notification.NotificationTypeResolver.PROFILE_FIELD;
import static org.springframework.beans.factory.xml.BeanDefinitionParserDelegate.META_ELEMENT;

import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.error.NpsServiceException;
import java.io.StringReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.springframework.stereotype.Component;

@Component
class ProfileReaderXml {

  static final int XML_MAX_BUNDLE_DEPTH = 4;

  private final XMLInputFactory xmlInputFactory;

  public ProfileReaderXml(XMLInputFactory xmlInputFactory) {
    this.xmlInputFactory = xmlInputFactory;
  }

  /**
   * Extracts the first {@code Bundle.meta.profile/@value} URL from an XML notification payload.
   *
   * <p>The payload may contain a FHIR {@code <Bundle>} either as the document root or wrapped
   * inside other elements. Only the {@code <meta>} element that is a direct child of the first
   * encountered {@code <Bundle>} is considered.
   *
   * <p><strong>Implementation note</strong><br>
   * This method intentionally uses a streaming StAX parser instead of building a full DOM tree. The
   * code therefore looks more verbose than a typical XPath- or DOM-based solution, but it avoids
   * fully parsing untrusted input. The parser scans the document until the first {@code <Bundle>}
   * element is found (bounded by {@code XML_MAX_BUNDLE_DEPTH}), then inspects only the top-level
   * {@code <meta>} element for {@code <profile>} entries.
   *
   * <p>This approach limits memory usage, avoids unnecessary work on large payloads, and reduces
   * exposure to malicious inputs containing deeply nested or irrelevant structures.
   *
   * @param xml XML notification payload
   * @return the first {@code Bundle.meta.profile/@value} URL, or {@code null} if not present
   * @throws NpsServiceException if the XML cannot be parsed
   */
  public String readProfile(String xml) {
    try (StringReader sr = new StringReader(xml)) {
      XMLStreamReader r = xmlInputFactory.createXMLStreamReader(sr);

      boolean inBundle = false;
      boolean inTopLevelMeta = false;

      int scanningDepth = 0;
      int bundleDepth = 0;

      while (r.hasNext()) {
        int evt = r.next();

        if (evt == XMLStreamConstants.START_ELEMENT) {
          scanningDepth++;
          String name = r.getLocalName();

          // Hard limit: if we still haven't found a Bundle by this depth, stop early
          if (!inBundle) {
            if (scanningDepth > XML_MAX_BUNDLE_DEPTH) {
              return null;
            }
            if (BUNDLE_VALUE.equals(name)) {
              inBundle = true;
              bundleDepth = scanningDepth; // absolute depth where <Bundle> begins
            }
            continue;
          }

          int depthInsideBundle = scanningDepth - bundleDepth; // Bundle => 0, direct children => 1

          if (!inTopLevelMeta) {
            if (depthInsideBundle == 1 && META_ELEMENT.equals(name)) {
              inTopLevelMeta = true;
            }
            continue;
          }

          // We are inside top-level <meta>: return value of first profile
          if (PROFILE_FIELD.equals(name)) {
            return r.getAttributeValue(null, "value");
          }

        } else if (evt == XMLStreamConstants.END_ELEMENT) {
          String name = r.getLocalName();

          if (inBundle) {
            int depthInsideBundle = scanningDepth - bundleDepth;

            // If we close </meta> directly under <Bundle>, we're done (no need to parse
            // entries/resources)
            if (inTopLevelMeta && depthInsideBundle == 1 && META_ELEMENT.equals(name)) {
              return null;
            }

            // End of the first Bundle encountered (no profile found)
            if (depthInsideBundle == 0 && BUNDLE_VALUE.equals(name)) {
              return null;
            }
          }

          scanningDepth--;
        }
      }

      return null;

    } catch (XMLStreamException e) {
      throw new NpsServiceException(
          ErrorCode.INVALID_REQUEST_PAYLOAD, "Unable to parse FHIR Bundle XML.", e);
    }
  }
}

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

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

@Component
@Slf4j
class TransmittingSiteSearchParser {

  // XML tag names
  private static final String DEPARTMENT_TAG_NAME = "TransmittingSite";

  // XML attributes on TransmittingSite tag
  private static final String DEPARTMENT_TAG_ID_ATTRIBUTE = "Code";
  private static final String DEPARTMENT_TAG_NAME_ATTRIBUTE = "Name";
  private static final String DEPARTMENT_TAG_DEPARTMENT_ATTRIBUTE = "Department";
  private static final String DEPARTMENT_TAG_STREET_ATTRIBUTE = "Street";
  private static final String DEPARTMENT_TAG_POSTALCODE_ATTRIBUTE = "Postalcode";
  private static final String DEPARTMENT_TAG_PLACE_ATTRIBUTE = "Place";
  private static final String DEPARTMENT_TAG_PHONE_ATTRIBUTE = "Phone";
  private static final String DEPARTMENT_TAG_FAX_ATTRIBUTE = "Fax";
  private static final String DEPARTMENT_TAG_EMAIL_ATTRIBUTE = "Email";

  public List<TransmittingSite> readTransmittingSiteSearchXml(final String fileName) {
    try (InputStream in = getClass().getResourceAsStream(fileName)) {
      final var handler = new TransmittingSiteSearchHandler();

      final SAXParserFactory spf = SAXParserFactory.newInstance();
      spf.setFeature("http://xml.org/sax/features/external-general-entities", false);
      final SAXParser saxParser = spf.newSAXParser();
      saxParser.parse(in, handler);

      return handler.getList();
    } catch (final Exception e) {
      throw new IllegalStateException("error loading transmitting site search xml", e);
    }
  }

  @Getter
  private static class TransmittingSiteSearchHandler extends DefaultHandler {
    private final List<TransmittingSite> list = new LinkedList<>();

    private static String getValueOrEmpty(Attributes attributes, String attributeName) {
      final String attributeValue = attributes.getValue(attributeName);
      return (attributeValue == null) ? "" : attributeValue;
    }

    private static TransmittingSite readHealthOfficeData(final Attributes attributes) {
      final String id = attributes.getValue(DEPARTMENT_TAG_ID_ATTRIBUTE);
      final String tel = getValueOrEmpty(attributes, DEPARTMENT_TAG_PHONE_ATTRIBUTE);
      final String fax = getValueOrEmpty(attributes, DEPARTMENT_TAG_FAX_ATTRIBUTE);
      final String email = getValueOrEmpty(attributes, DEPARTMENT_TAG_EMAIL_ATTRIBUTE);
      final String department = getValueOrEmpty(attributes, DEPARTMENT_TAG_DEPARTMENT_ATTRIBUTE);
      final String name = getValueOrEmpty(attributes, DEPARTMENT_TAG_NAME_ATTRIBUTE);
      final String street = getValueOrEmpty(attributes, DEPARTMENT_TAG_STREET_ATTRIBUTE);
      final String zipCode = getValueOrEmpty(attributes, DEPARTMENT_TAG_POSTALCODE_ATTRIBUTE);
      final String city = getValueOrEmpty(attributes, DEPARTMENT_TAG_PLACE_ATTRIBUTE);
      return new TransmittingSite(id, tel, fax, email, department, name, street, zipCode, city);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
      if (DEPARTMENT_TAG_NAME.equals(qName)) {
        list.add(readHealthOfficeData(attributes));
      }
    }
  }
}

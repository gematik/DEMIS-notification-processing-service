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

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.error.NpsServiceException;
import java.util.stream.Stream;
import javax.xml.stream.XMLInputFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NotificationTypeResolverTest {

  private NotificationTypeResolver underTest;

  @BeforeEach
  void setUp() {
    ObjectMapper objectMapper = new ObjectMapper();

    XMLInputFactory xmlInputFactory = XMLInputFactory.newFactory();
    xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    xmlInputFactory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);

    var jsonReader = new ProfileReaderJson(objectMapper);
    var xmlReader = new ProfileReaderXml(xmlInputFactory);

    underTest = new NotificationTypeResolver(jsonReader, xmlReader);
  }

  // -------------------- HAPPY PATHS --------------------

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("happyPathCasesJson")
  void resolves_profile_in_notification_Json(
      String name, String payload, MessageType type, NotificationType expected) {
    assertThat(underTest.resolveFromNotification(payload, type)).isEqualTo(expected);
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("happyPathCasesXml")
  void resolves_profile_in_notification_Xml(
      String name, String payload, MessageType type, NotificationType expected) {
    assertThat(underTest.resolveFromNotification(payload, type)).isEqualTo(expected);
  }

  static Stream<Arguments> happyPathCasesJson() {
    return Stream.of(
        // DISEASE
        Arguments.of(
            "disease json exact",
            """
        { "resourceType":"Bundle","meta":{"profile":["https://demis.rki.de/fhir/StructureDefinition/NotificationBundleDisease"]},"entry":[] }
        """,
            MessageType.JSON,
            NotificationType.DISEASE),
        Arguments.of(
            "disease json prefix",
            """
                { "resourceType":"Bundle","meta":{"profile":["https://demis.rki.de/fhir/StructureDefinition/NotificationBundleDiseaseFoo"]},"entry":[] }
                """,
            MessageType.JSON,
            NotificationType.DISEASE),

        // LAB
        Arguments.of(
            "lab json exact",
            """
        { "resourceType":"Bundle","meta":{"profile":["https://demis.rki.de/fhir/StructureDefinition/NotificationBundleLaboratory"]} }
        """,
            MessageType.JSON,
            NotificationType.LABORATORY),
        Arguments.of(
            "lab json prefix",
            """
                { "resourceType":"Bundle","meta":{"profile":["https://demis.rki.de/fhir/StructureDefinition/NotificationBundleLaboratoryFoo"]} }
                """,
            MessageType.JSON,
            NotificationType.LABORATORY),

        // PARAM WRAPPER
        Arguments.of(
            "Notification wrapped within parameters",
            """
                {
                  "resourceType": "Parameters",
                  "meta": {
                    "profile": [
                      "https://demis.rki.de/fhir/StructureDefinition/ProcessNotificationRequestParameters"
                    ]
                  },
                  "parameter": [
                    {
                      "name": "content",
                      "resource": {
                        "resourceType": "Bundle",
                        "meta": {
                          "lastUpdated": "2021-11-12T22:50:01.000+01:00",
                          "profile": [
                            "https://demis.rki.de/fhir/StructureDefinition/NotificationBundleLaboratory"
                          ]
                        }
                      }
                    }
                  ]
                }
            """,
            MessageType.JSON,
            NotificationType.LABORATORY),

        // ORDER
        Arguments.of(
            "Order in json doesnt matter (1)",
            """
                 {
                         "meta": { "profile": ["https://demis.rki.de/fhir/StructureDefinition/ProcessNotificationRequestParameters"] },
                          "parameter": [
                            {
                              "name": "content",
                              "resource": {
                                "meta": {
                                  "lastUpdated": "2021-11-12T22:50:01.000+01:00",
                                  "profile": ["https://demis.rki.de/fhir/StructureDefinition/NotificationBundleLaboratory"]
                                },
                                "resourceType": "Bundle"
                              }
                            }
                          ],
                          "resourceType": "Parameters"
               }
              """,
            MessageType.JSON,
            NotificationType.LABORATORY),
        Arguments.of(
            "Order in json doesnt matter (2)",
            """
                     { "foo":"bar", "meta":{"profile":["https://demis.rki.de/fhir/StructureDefinition/NotificationBundleDisease"]},"entry":[], "resourceType":"Bundle", "bou":"baa" }

              """,
            MessageType.JSON,
            NotificationType.DISEASE),
        Arguments.of(
            "Escaped profile URL in JSON",
            """
                    {
                      "resourceType": "Bundle",
                      "meta": {
                        "profile": [
                          "https:\\/\\/demis.rki.de\\/fhir\\/StructureDefinition\\/NotificationBundleLaboratory"
                        ]
                      }
                    }
                    """,
            MessageType.JSON,
            NotificationType.LABORATORY));
  }

  static Stream<Arguments> happyPathCasesXml() {
    return Stream.of(
        // DISEASE
        Arguments.of(
            "disease xml exact",
            """
                    <Bundle xmlns="http://hl7.org/fhir">
                      <meta><profile value="https://demis.rki.de/fhir/StructureDefinition/NotificationBundleDisease"/></meta>
                      <entry><resource><Patient/></resource></entry>
                    </Bundle>
                    """,
            MessageType.XML,
            NotificationType.DISEASE),
        Arguments.of(
            "disease json prefix",
            """
                            { "resourceType":"Bundle","meta":{"profile":["https://demis.rki.de/fhir/StructureDefinition/NotificationBundleDiseaseFoo"]},"entry":[] }
                            """,
            MessageType.JSON,
            NotificationType.DISEASE),

        // LAB
        Arguments.of(
            "lab xml exact",
            """
                    <Bundle xmlns="http://hl7.org/fhir">
                      <meta><profile value="https://demis.rki.de/fhir/StructureDefinition/NotificationBundleLaboratory"/></meta>
                    </Bundle>
                    """,
            MessageType.XML,
            NotificationType.LABORATORY),
        Arguments.of(
            "lab xml prefix",
            """
                                <Bundle xmlns="http://hl7.org/fhir">
                                  <meta><profile value="https://demis.rki.de/fhir/StructureDefinition/NotificationBundleLaboratoryFoo"/></meta>
                                </Bundle>
                                """,
            MessageType.XML,
            NotificationType.LABORATORY),

        // WRAPPED IN PARAMS
        Arguments.of(
            "lab xml within parameters",
            """
                        <Parameters xmlns="http://hl7.org/fhir">
                           <parameter>
                              <name value="content"></name>
                              <resource>
                                 <Bundle xmlns="http://hl7.org/fhir">
                                    <meta>
                                       <lastUpdated value="2025-02-17T05:59:31.000+01:00"></lastUpdated>
                                       <profile value="https://demis.rki.de/fhir/StructureDefinition/NotificationBundleLaboratory"></profile>
                                    </meta>
                                 </Bundle>
                              </resource>
                           </parameter>
                        </Parameters>
                        """,
            MessageType.XML,
            NotificationType.LABORATORY),
        Arguments.of(
            "Formatting doesn't matter",
            """
                    <Bundle xmlns="http://hl7.org/fhir">
                      <meta>
                        <profile value    =
                        "https://demis.rki.de/fhir/StructureDefinition/NotificationBundleLaboratory"/>
                      </meta>
                    </Bundle>
                    """,
            MessageType.XML,
            NotificationType.LABORATORY));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("firstProfileWinsCases")
  void uses_first_profile_in_meta_profile(
      String name, String payload, MessageType type, NotificationType expected) {
    assertThat(underTest.resolveFromNotification(payload, type)).isEqualTo(expected);
  }

  static Stream<Arguments> firstProfileWinsCases() {
    return Stream.of(
        Arguments.of(
            "json first is disease",
            """
        {
          "resourceType":"Bundle",
          "meta":{"profile":[
            "https://demis.rki.de/fhir/StructureDefinition/NotificationBundleDisease",
            "https://demis.rki.de/fhir/StructureDefinition/NotificationBundleLaboratory"
          ]}
        }
        """,
            MessageType.JSON,
            NotificationType.DISEASE),
        Arguments.of(
            "xml first is disease",
            """
        <Bundle xmlns="http://hl7.org/fhir">
          <meta>
            <profile value="https://demis.rki.de/fhir/StructureDefinition/NotificationBundleDisease"/>
            <profile value="https://demis.rki.de/fhir/StructureDefinition/NotificationBundleLaboratory"/>
          </meta>
        </Bundle>
        """,
            MessageType.XML,
            NotificationType.DISEASE));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("errorCases")
  void throws_expected_error_code(
      String name, String payload, MessageType type, ErrorCode expectedCode) {
    assertThatThrownBy(() -> underTest.resolveFromNotification(payload, type))
        .isInstanceOf(NpsServiceException.class)
        .satisfies(
            e ->
                assertThat(((NpsServiceException) e).getErrorCode())
                    .isEqualTo(expectedCode.name()));
  }

  static Stream<Arguments> errorCases() {
    return Stream.of(
        Arguments.of(
            "json missing meta.profile",
            """
        { "resourceType":"Bundle", "meta": { "tag": [] } }
        """,
            MessageType.JSON,
            ErrorCode.INVALID_REQUEST_PAYLOAD),
        Arguments.of(
            "xml missing meta.profile",
            """
        <Bundle xmlns="http://hl7.org/fhir"><meta><tag><system value="x"/><code value="y"/></tag></meta></Bundle>
        """,
            MessageType.XML,
            ErrorCode.INVALID_REQUEST_PAYLOAD),
        Arguments.of(
            "json blank profile",
            """
        { "resourceType":"Bundle", "meta": { "profile": ["   "] } }
        """,
            MessageType.JSON,
            ErrorCode.INVALID_REQUEST_PAYLOAD),
        Arguments.of(
            "xml blank profile",
            """
        <Bundle xmlns="http://hl7.org/fhir"><meta><profile value="   "/></meta></Bundle>
        """,
            MessageType.XML,
            ErrorCode.INVALID_REQUEST_PAYLOAD),
        Arguments.of(
            "json unknown profile",
            """
        { "resourceType":"Bundle", "meta": { "profile": ["https://example.org/UnknownBundleProfile"] } }
        """,
            MessageType.JSON,
            ErrorCode.UNSUPPORTED_PROFILE),
        Arguments.of(
            "xml unknown profile",
            """
        <Bundle xmlns="http://hl7.org/fhir"><meta><profile value="https://example.org/UnknownBundleProfile"/></meta></Bundle>
        """,
            MessageType.XML,
            ErrorCode.UNSUPPORTED_PROFILE),
        Arguments.of(
            "malformed json",
            "{ not valid json",
            MessageType.JSON,
            ErrorCode.INVALID_REQUEST_PAYLOAD),
        Arguments.of(
            "malformed xml",
            "<Bundle><meta></Bundle>",
            MessageType.XML,
            ErrorCode.INVALID_REQUEST_PAYLOAD),
        Arguments.of(
            "json meta.profile not array",
            """
        { "resourceType":"Bundle", "meta": { "profile": "not-an-array" } }
        """,
            MessageType.JSON,
            ErrorCode.INVALID_REQUEST_PAYLOAD),
        Arguments.of(
            "xml profile missing value attribute",
            """
        <Bundle xmlns="http://hl7.org/fhir"><meta><profile/></meta></Bundle>
        """,
            MessageType.XML,
            ErrorCode.INVALID_REQUEST_PAYLOAD),
        Arguments.of(
            "message type null",
            """
        { "resourceType":"Bundle", "meta": { "profile": ["https://demis.rki.de/fhir/StructureDefinition/NotificationBundleDisease"] } }
        """,
            null,
            ErrorCode.INVALID_REQUEST_PAYLOAD),
        Arguments.of(
            "The Bundle element is too deep within the XML structure",
            """
                    <Parameters xmlns="http://hl7.org/fhir">
                       <parameter>
                          <name value="content"></name>
                          <resource>
                          <foo>
                             <Bundle xmlns="http://hl7.org/fhir">
                                <meta>
                                   <lastUpdated value="2025-02-17T05:59:31.000+01:00"></lastUpdated>
                                   <profile value="https://demis.rki.de/fhir/StructureDefinition/NotificationBundleLaboratory"></profile>
                                </meta>
                             </Bundle>
                            </foo>
                          </resource>
                       </parameter>
                    </Parameters>
                    """,
            MessageType.XML,
            ErrorCode.INVALID_REQUEST_PAYLOAD));
  }
}

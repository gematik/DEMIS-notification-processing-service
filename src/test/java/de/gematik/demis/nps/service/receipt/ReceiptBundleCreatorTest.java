package de.gematik.demis.nps.service.receipt;

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

import static de.gematik.demis.nps.test.TestUtil.fhirResourceToJson;
import static de.gematik.demis.nps.test.TestUtil.toDate;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

import de.gematik.demis.nps.base.util.TimeProvider;
import de.gematik.demis.nps.base.util.UuidGenerator;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import lombok.SneakyThrows;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReceiptBundleCreatorTest {

  public static final String PLACEHOLDER_ID = "PLACEHOLDER_ORGANIZATION_ID";
  public static final String PLACEHOLDER_ID_WITH_ASTERIX = "PH_WITH_ASTERIX";

  private static final String EXPECTED =
      """
          {
            "resourceType" : "Bundle",
            "meta" : {
              "profile" : [ "https://demis.rki.de/fhir/StructureDefinition/ReceiptBundle" ]
            },
            "type" : "collection",
            "entry" : [ {
              "fullUrl" : "https://demis.rki.de/fhir/Composition/U4-aaaa-bbbb-cccc",
              "resource" : {
                "resourceType" : "Composition",
                "id" : "U4-aaaa-bbbb-cccc",
                "meta" : {
                  "profile" : [ "https://demis.rki.de/fhir/StructureDefinition/NotificationReceipt" ]
                },
                "extension" : [ {
                  "url" : "https://demis.rki.de/fhir/StructureDefinition/ReceivedNotification",
                  "valueIdentifier" : {
                    "value" : "1234567890"
                  }
                } ],
                "status" : "final",
                "type" : {
                  "coding" : [ {
                    "system" : "http://loinc.org",
                    "code" : "77999-1",
                    "display" : "Case notification comment"
                  } ]
                },
                "date" : "2023-12-22T00:00:00+01:00",
                "author" : [ {
                  "reference" : "Organization/DEMIS"
                } ],
                "title" : "Meldevorgangsquittung",
                "relatesTo" : [ {
                  "code" : "appends",
                  "targetReference" : {
                    "type" : "Composition",
                    "identifier" : {
                      "system" : "https://demis.rki.de/fhir/NamingSystem/NotificationId",
                      "value" : "555999"
                    }
                  }
                } ],
                "section" : [ {
                  "title" : "Zuständiges Gesundheitsamt",
                  "code" : {
                    "text" : "Zuständiges Gesundheitsamt"
                  },
                  "entry" : [ {
                    "reference" : "Organization/1"
                  } ]
                }, {
                  "title" : "PDF Quittung",
                  "code" : {
                    "text" : "PDF Quittung"
                  },
                  "entry" : [ {
                    "reference" : "Binary/U4-aaaa-bbbb-cccc"
                  } ]
                } ]
              }
            }, {
              "fullUrl" : "https://demis.rki.de/fhir/Organization/DEMIS",
              "resource" : {
                "resourceType" : "Organization",
                "id" : "DEMIS",
                "name" : "DEMIS",
                "contact" : [ {
                  "telecom" : [ {
                    "system" : "email",
                    "value" : "demis-support@rki.de"
                  } ]
                } ]
              }
            }, {
              "fullUrl" : "https://demis.rki.de/fhir/Organization/1",
              "resource" : {
                "resourceType" : "Organization",
                "id" : "1",
                "name" : "GA-1.98"
              }
            }, {
              "fullUrl" : "https://demis.rki.de/fhir/Binary/U4-aaaa-bbbb-cccc",
              "resource" : {
                "resourceType" : "Binary",
                "id" : "U4-aaaa-bbbb-cccc",
                "contentType" : "application/pdf",
                "data" : "TWVpbiAtIFBkZg=="
              }
            } ]
          }
          """
          .formatted(PLACEHOLDER_ID, PLACEHOLDER_ID_WITH_ASTERIX, PLACEHOLDER_ID_WITH_ASTERIX);

  @Mock UuidGenerator uuidGenerator;
  @Mock TimeProvider timeProvider;

  @InjectMocks ReceiptBundleCreator underTest;

  public static Organization createOrga(final String name) {
    return (Organization) new Organization().setName(name).setId("1");
  }

  @Test
  @SneakyThrows
  void builder() {
    when(uuidGenerator.generateUuid()).thenReturn("U4-aaaa-bbbb-cccc");
    when(timeProvider.now()).thenReturn(toDate(LocalDate.of(2023, 12, 22)));

    final Bundle result =
        underTest
            .builder()
            .addNotificationId(new Identifier().setValue("1234567890"))
            .addResponsibleHealthOffice(createOrga("GA-1.98"))
            .addPdf("Mein - Pdf".getBytes(StandardCharsets.UTF_8))
            .addRelatesNotificationId("555999")
            .build();

    final String json = fhirResourceToJson(result);
    JSONObject jsonObject = new JSONObject(json);
    assertThat(jsonObject).usingRecursiveComparison().isEqualTo(new JSONObject(EXPECTED));
  }
}

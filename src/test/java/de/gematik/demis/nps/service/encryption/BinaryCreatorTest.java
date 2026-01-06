package de.gematik.demis.nps.service.encryption;

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

import static de.gematik.demis.nps.base.profile.DemisSystems.RESPONSIBLE_HEALTH_DEPARTMENT_CODING_SYSTEM;
import static de.gematik.demis.nps.test.TestUtil.toDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.gematik.demis.nps.base.util.TimeProvider;
import de.gematik.demis.nps.service.encryption.BinaryCreator.BinaryBuilder;
import de.gematik.demis.nps.test.TestUtil;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BinaryCreatorTest {
  private static final String EXPECTED_BINARY =
      """
      {
        "resourceType" : "Binary",
        "meta" : {
          "lastUpdated" : "2023-12-22T00:00:00.000+01:00",
          "tag" : [ {
            "system" : "https://demis.rki.de/fhir/CodeSystem/ResponsibleDepartment",
            "code" : "2.56"
          }, {
            "system" : "https://demis.rki.de/fhir/CodeSystem/RelatedNotificationBundle",
            "code" : "a5e00874-bb26-45ac-8eea-0bde76456703",
            "display" : "Relates to message with identifier: a5e00874-bb26-45ac-8eea-0bde76456703"
          } ]
        },
        "contentType" : "application/cms",
        "data" : "RU5DUllQVEVE"
      }
      """;
  @Mock TimeProvider timeProvider;
  @InjectMocks BinaryCreator underTest;

  @Test
  void builder() {
    when(timeProvider.now()).thenReturn(toDate(LocalDate.of(2023, 12, 22)));

    final BinaryBuilder builder = underTest.builder();
    assertThat(builder).isNotNull();

    final Binary result =
        builder
            .addTags(List.of(new Coding(RESPONSIBLE_HEALTH_DEPARTMENT_CODING_SYSTEM, "1.99", null)))
            .addRelatedNotificationIdentifierTag(
                new Identifier().setValue("a5e00874-bb26-45ac-8eea-0bde76456703"))
            .setContent("ENCRYPTED".getBytes(StandardCharsets.UTF_8))
            .setResponsibleHealthOfficeTag("2.56")
            .build();

    assertThat(TestUtil.fhirResourceToJson(result)).isEqualToIgnoringWhitespace(EXPECTED_BINARY);
  }
}

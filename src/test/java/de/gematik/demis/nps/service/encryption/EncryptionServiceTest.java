package de.gematik.demis.nps.service.encryption;

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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.XmlParser;
import de.gematik.demis.nps.base.profile.DemisSystems;
import de.gematik.demis.nps.base.util.TimeProvider;
import java.util.List;
import java.util.NoSuchElementException;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EncryptionServiceTest {
  @Mock private DataEncryption dataEncryption;

  @Mock private CertificateProvider certificateProvider;

  @Mock private FhirContext fhirContext;

  @Mock private XmlParser xmlParser;

  private final BinaryCreator binaryCreator = new BinaryCreator(new TimeProvider());

  @InjectMocks private EncryptionService underTest;

  @BeforeEach
  void setup() {
    underTest =
        new EncryptionService(dataEncryption, certificateProvider, fhirContext, binaryCreator);
    when(fhirContext.newXmlParser()).thenReturn(xmlParser);
    when(xmlParser.encodeResourceToString(any())).thenReturn("test");
    when(dataEncryption.encryptData(any(), any())).thenReturn(new byte[0]);
  }

  /** We are using the tag to retrieve the Binary from the FSS (at least in the core-tests). */
  @Test
  void thatRelatedIdentifierTagIsSetOnBinary() {
    final Bundle bundle = new Bundle();
    bundle
        .getMeta()
        .addTag()
        .setSystem(DemisSystems.RESPONSIBLE_HEALTH_DEPARTMENT_CODING_SYSTEM)
        .setCode("required to avoid nosuchelement exception");
    bundle.setId("test-bundle-id");
    final Identifier identifier = new Identifier().setValue("original-id");
    bundle.setIdentifier(identifier);

    final Binary result = underTest.encryptFor(bundle, "1.01.0.53");

    final List<Coding> list =
        result.getMeta().getTag().stream()
            .filter(c -> DemisSystems.RELATED_NOTIFICATION_CODING_SYSTEM.equals(c.getSystem()))
            .toList();
    assertThat(list).hasSize(1);
    final Coding tag = list.getFirst();
    assertThat(tag.getSystem()).isEqualTo(DemisSystems.RELATED_NOTIFICATION_CODING_SYSTEM);
    assertThat(tag.getCode()).isEqualTo("original-id");
    assertThat(tag.getDisplay()).isEqualTo("Relates to message with identifier: original-id");
  }

  /**
   * We don't really know if this is important to test, but we capture the current state here. Feel
   * free to change this test.
   */
  @Test
  void thatAnExceptionIsRaisedIfResponsibleTagIsMissingOnInput() {
    final Bundle bundle = new Bundle();
    bundle.setId("test-bundle-id");
    final Identifier identifier = new Identifier().setValue("original-id");
    bundle.setIdentifier(identifier);

    assertThatExceptionOfType(NoSuchElementException.class)
        .isThrownBy(() -> underTest.encryptFor(bundle, "1.01.0.53"));
  }
}

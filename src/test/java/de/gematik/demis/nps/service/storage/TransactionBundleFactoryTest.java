package de.gematik.demis.nps.service.storage;

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
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import de.gematik.demis.nps.base.profile.DemisProfiles;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@Slf4j
class TransactionBundleFactoryTest {

  private static final String EXPECTED =
      """
{
    "resourceType": "Bundle",
    "type": "transaction",
    "entry": [
        {
            "fullUrl": "urn:uuid:09210cc6-66c9-4afd-9515-2b829dfea5b8",
            "resource": {
                "resourceType": "Bundle",
                "meta": {
                    "profile": [
                        "https://demis.rki.de/fhir/StructureDefinition/NotificationBundleLaboratory"
                    ]
                }
            },
            "request": {
                "method": "POST",
                "url": "Bundle"
            }
        },
        {
            "fullUrl": "urn:uuid:a62a470d-5627-4d5b-bfc7-8f9e57e72b9e",
            "resource": {
                "resourceType": "Binary",
                "contentType": "cms",
                "data": "YWJj"
            },
            "request": {
                "method": "POST",
                "url": "Binary"
            }
        },
        {
            "fullUrl": "urn:uuid:fb868e63-dfa7-403e-8c8e-80f92e4e4176",
            "resource": {
                "resourceType": "Binary",
                "contentType": "cms",
                "data": "ZGVm"
            },
            "request": {
                "method": "POST",
                "url": "Binary"
            }
        }
    ]
}
""";

  private final TransactionBundleFactory underTest = new TransactionBundleFactory();

  @Test
  void createTransactionBundle() {
    final Binary binary =
        new Binary().setContentType("cms").setData("abc".getBytes(StandardCharsets.UTF_8));

    final Binary binarySubsidiary =
        new Binary().setContentType("cms").setData("def".getBytes(StandardCharsets.UTF_8));

    final Bundle rkiBundle = new Bundle();
    DemisProfiles.NOTIFICATION_BUNDLE_LABORATORY_PROFILE.applyTo(rkiBundle);

    try (final var staticMock = mockStatic(UUID.class)) {
      final UUID uuidMock = Mockito.mock(UUID.class);
      when(uuidMock.toString())
          .thenReturn(
              "09210cc6-66c9-4afd-9515-2b829dfea5b8",
              "a62a470d-5627-4d5b-bfc7-8f9e57e72b9e",
              "fb868e63-dfa7-403e-8c8e-80f92e4e4176");
      staticMock.when(UUID::randomUUID).thenReturn(uuidMock);

      final Bundle transactionBundle =
          underTest.createTransactionBundle(binary, binarySubsidiary, rkiBundle);
      assertThat(fhirResourceToJson(transactionBundle)).isEqualToIgnoringWhitespace(EXPECTED);
    }
  }
}

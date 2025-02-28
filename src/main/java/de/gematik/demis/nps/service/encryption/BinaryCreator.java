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
 * #L%
 */

import static de.gematik.demis.nps.base.profile.DemisSystems.RESPONSIBLE_HEALTH_DEPARTMENT_CODING_SYSTEM;

import de.gematik.demis.nps.base.profile.DemisSystems;
import de.gematik.demis.nps.base.util.TimeProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class BinaryCreator {
  private final TimeProvider timeProvider;

  public BinaryBuilder builder() {
    return new BinaryBuilder();
  }

  class BinaryBuilder {
    private final Binary binary = new Binary();

    public Binary build() {
      binary.getMeta().setLastUpdated(timeProvider.now());
      return binary;
    }

    public BinaryBuilder addTags(final List<Coding> tags) {
      binary.getMeta().getTag().addAll(tags);
      return this;
    }

    public BinaryBuilder addRelatedNotificationIdentifierTag(final Identifier identifier) {
      binary
          .getMeta()
          .addTag(
              DemisSystems.RELATED_NOTIFICATION_CODING_SYSTEM,
              identifier.getValue(),
              "Relates to message with identifier: " + identifier.getValue());
      return this;
    }

    public BinaryBuilder setContent(final byte[] encryptedData) {
      binary.setContentTypeElement(new CodeType("application/cms"));
      binary.setData(encryptedData);
      return this;
    }

    public BinaryBuilder setResponsibleHealthOfficeTag(final String targetHealthOffice) {
      binary.getMeta().getTag().stream()
          .filter(t -> RESPONSIBLE_HEALTH_DEPARTMENT_CODING_SYSTEM.equals(t.getSystem()))
          .findFirst()
          .orElseThrow()
          .setCode(targetHealthOffice);
      return this;
    }
  }
}

package de.gematik.demis.nps.service.receipt;

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

import de.gematik.demis.nps.base.profile.DemisExtensions;
import de.gematik.demis.nps.base.profile.DemisProfiles;
import de.gematik.demis.nps.base.profile.DemisSystems;
import de.gematik.demis.nps.base.util.TimeProvider;
import de.gematik.demis.nps.base.util.UuidGenerator;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Composition.CompositionRelatesToComponent;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class ReceiptBundleCreator {

  private static final String RESPONSE_COMPOSITION_TITLE = "Meldevorgangsquittung";
  private static final String RESPONSE_PDF_RECEIPT_SECTION_TITLE = "PDF Quittung";
  private static final String RESPONSIBLE_DEPARTMENT = "Zuständiges Gesundheitsamt";
  private static final Coding COMPOSITION_CODING_TYPE =
      new Coding()
          .setSystem(DemisSystems.RESPONSE_COMPOSITION_SYSTEM)
          .setCode("77999-1")
          .setDisplay("Case notification comment");

  private static final CodeType CODETYPE_PDF = new CodeType("application/pdf");

  private static final Organization ORG_DEMIS = DemisOrganization.createInstance();

  private final UuidGenerator uuidGenerator;
  private final TimeProvider timeProvider;

  public ReceiptBundleBuilder builder() {
    return new ReceiptBundleBuilder();
  }

  public class ReceiptBundleBuilder {

    private final Bundle bundle;
    private final Composition composition;

    private ReceiptBundleBuilder() {
      bundle = new Bundle();
      composition = new Composition();

      setupBundle();
      setupComposition();
      addBundleResource(composition);
      addAuthor();
    }

    public Bundle build() {
      return bundle;
    }

    private void setupBundle() {
      DemisProfiles.RECEIPT_BUNDLE_PROFILE.applyTo(bundle);
      bundle.setType(Bundle.BundleType.COLLECTION);
    }

    private void setupComposition() {
      DemisProfiles.NOTIFICATION_RECEIPT_PROFILE.applyTo(composition);
      composition.setType(new CodeableConcept().addCoding(COMPOSITION_CODING_TYPE));

      composition.setId(uuidGenerator.generateUuid());
      composition.setDate(timeProvider.now());

      composition.setStatus(Composition.CompositionStatus.FINAL);
      composition.setTitle(RESPONSE_COMPOSITION_TITLE);
    }

    public ReceiptBundleBuilder addNotificationId(final Identifier identifier) {
      composition
          .addExtension()
          .setUrl(DemisExtensions.EXTENSION_URL_RECEIVED_NOTIFICATION)
          .setValue(identifier);
      return this;
    }

    public ReceiptBundleBuilder addPdf(final byte[] pdfBytes) {
      final Binary pdfBinary = createPdfBinary(pdfBytes);

      composition
          .addSection()
          .setTitle(RESPONSE_PDF_RECEIPT_SECTION_TITLE)
          .setCode(new CodeableConcept().setText(RESPONSE_PDF_RECEIPT_SECTION_TITLE))
          .addEntry(addBundleResource(pdfBinary));

      return this;
    }

    private void addAuthor() {
      composition.addAuthor(addBundleResource(ORG_DEMIS));
    }

    public ReceiptBundleBuilder addResponsibleHealthOffice(final Organization destination) {
      composition
          .addSection()
          .setTitle(RESPONSIBLE_DEPARTMENT)
          .setCode(new CodeableConcept().setText(RESPONSIBLE_DEPARTMENT))
          .addEntry(addBundleResource(destination));
      return this;
    }

    public ReceiptBundleBuilder addRelatesNotificationId(final String notificationId) {
      final var component = new CompositionRelatesToComponent();
      component.setCode(Composition.DocumentRelationshipType.APPENDS);
      final var relatesToTarget = new Reference();
      relatesToTarget.setType("Composition");
      relatesToTarget.setIdentifier(
          new Identifier()
              .setSystem(DemisSystems.NOTIFICATION_IDENTIFIER_SYSTEM)
              .setValue(notificationId));
      component.setTarget(relatesToTarget);

      composition.addRelatesTo(component);

      return this;
    }

    @Nonnull
    public ReceiptBundleBuilder addCustodian(@Nonnull final Organization custodian) {
      composition.setCustodian(addBundleResource(custodian));
      return this;
    }

    private Reference addBundleResource(final Resource resource) {
      final String url =
          DemisProfiles.PROFILE_BASE_URL
              + resource.getClass().getSimpleName()
              + "/"
              + resource.getId();

      bundle.addEntry().setFullUrl(url).setResource(resource);

      return new Reference(resource);
    }

    private Binary createPdfBinary(byte[] data) {
      final Binary binary = new Binary();
      binary.setId(uuidGenerator.generateUuid());
      binary.setContentTypeElement(CODETYPE_PDF);
      binary.setData(data);
      return binary;
    }
  }
}

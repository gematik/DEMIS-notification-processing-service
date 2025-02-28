package de.gematik.demis.nps.test;

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

import static de.gematik.demis.nps.test.TestUtil.getJsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Provenance;

@UtilityClass
public class TestData {

  public static final String DISEASE_BUNDLE_NOTBYNAME_RESOURCE =
      "/bundles/disease_bundle_max_notbyname.json";
  public static final String LABORATORY_BUNDLE_NOTBYNAME_RESOURCE =
      "/bundles/laboratory_cvdp_bundle_notbyname.json";
  public static final String DISEASE_BUNDLE_RESOURCE = "/bundles/disease_bundle_max.json";
  public static final String LABORATORY_BUNDLE_RESOURCE = "/bundles/laboratory_cvdp_bundle.json";
  public static final String LABORATORY_BUNDLE_ESCAPED_RESOURCE =
      "/bundles/laboratory_cvdp_bundle_escaped.json";
  public static final String PROVENANCE_RESOURCE = "/entries/provenanceResource.json";
  public static final String PROVENANCE_ENTRY = "/entries/context_entry.json";

  public static Bundle diseaseBundle() {
    return getBundle(DISEASE_BUNDLE_RESOURCE);
  }

  public static Bundle laboratoryBundle() {
    return getBundle(LABORATORY_BUNDLE_RESOURCE);
  }

  public static Bundle getBundle(final String resourceName) {
    return getJsonParser().parseResource(Bundle.class, readResourceAsString(resourceName));
  }

  public static InputStream readResource(final String resourceName) {
    final InputStream is = TestUtil.class.getResourceAsStream(resourceName);
    if (is == null) {
      throw new IllegalStateException("missing resource file " + resourceName);
    }
    return is;
  }

  public static byte[] readResourceBytes(final String resourceName) {
    try (final InputStream is = readResource(resourceName)) {
      return is.readAllBytes();
    } catch (final IOException e) {
      throw new UncheckedIOException("error reading classpath resource " + resourceName, e);
    }
  }

  public static String readResourceAsString(final String resourceName) {
    return new String(readResourceBytes(resourceName), StandardCharsets.UTF_8);
  }

  @SneakyThrows
  public static BundleEntryComponent getProvenance() {
    Provenance resource =
        getJsonParser().parseResource(Provenance.class, readResourceAsString(PROVENANCE_RESOURCE));
    return new BundleEntryComponent()
        .setResource(resource)
        .setFullUrl("https://demis.rki.de/fhir/" + resource.fhirType() + "/" + resource.getId());
  }
}

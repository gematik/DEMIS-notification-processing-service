package de.gematik.demis.nps.base.fhir;

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

import de.gematik.demis.nps.base.profile.DemisProfiles.Profile;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Resource;

/** This class holds methods to query information on {@link Bundle} classes. */
// TODO move to fhir parser lib
public final class BundleQueries {

  private BundleQueries() {
    throw new IllegalStateException();
  }

  /**
   * Finds the first resource, included as a resource in the {@link Bundle#getEntry() entries} of
   * the given {@code content} bundle, that is instance of {@code clazz}. If no such resource is
   * found, an empty {@code Optional} is returned.
   *
   * @param <T> Type of the class represented by {@code clazz}.
   * @param content bundle to be queried
   * @param clazz Class the returned instance if
   * @return an {@code Optional} holding the found resource, or an empty Optional if no resource was
   *     found.
   */
  public static <T extends Resource> Optional<T> findFirstResource(
      final Bundle content, final Class<T> clazz) {
    return streamResourcesOfClass(content, clazz).findFirst();
  }

  /**
   * Finds the first resource, included as a resource in the {@link Bundle#getEntry() entries} of
   * the given {@code content} bundle, that is instance of {@code clazz} and for which {@code
   * condition} returns {@code true}. If no such resource is found, an empty {@code Optional} is
   * returned.
   *
   * @param <T> Type of the class represented by {@code clazz}.
   * @param content bundle to be queried
   * @param clazz Class the returned resource must be instance of
   * @param condition a predicate for which the returned value must hold.
   * @return an {@code Optional} holding the found resource, or an empty Optional if no resource was
   *     found.
   */
  public static <T extends Resource> Optional<T> findFirstResource(
      final Bundle content, Class<T> clazz, final Predicate<? super T> condition) {
    return streamResourcesOfClass(content, clazz).filter(condition).findFirst();
  }

  /**
   * Finds the first resource, included as a resource in the {@link Bundle#getEntry() entries} of
   * the given {@code content} bundle, that is instance of {@code clazz} and which has the profile
   * applied, which is passed as parameter {@code profile}.
   *
   * @param <T> Type of the class represented by {@code clazz}.
   * @param content the bundle to be searched for the contained resource.
   * @param clazz Type of resource to be returned
   * @param profileChecker profile applied to the resource
   * @return either the found element, wrapped in an {@code Optional}, otherwise an empty {@code
   *     Optional}
   */
  public static <T extends Resource> Optional<T> findFirstResourceWithProfile(
      final Bundle content, Class<T> clazz, final Profile<? super T> profileChecker) {
    return findFirstResource(content, clazz, profileChecker::isApplied);
  }

  /**
   * Finds all resources, included as a resource in the {@link Bundle#getEntry() entries} of the
   * given {@code content} bundle, that is instance of {@code clazz} and which have the profile
   * applied, which is passed as parameter {@code profile}.
   *
   * @param <T> Type of the class represented by {@code clazz}.
   * @param content the bundle to be searched for the contained resource.
   * @param clazz Type of resources to be returned
   * @param profileChecker profile applied to the resource
   * @return the found elements, added to a {@code List}, or an empty {@code List} if no such
   *     element found
   */
  public static <T extends Resource> List<T> findResourcesWithProfile(
      final Bundle content, final Class<T> clazz, final Profile<? super T> profileChecker) {
    return findResources(content, clazz, profileChecker::isApplied);
  }

  /**
   * Finds the all resources, included as a resource in the {@link Bundle#getEntry() entries} of the
   * given {@code content} bundle, that are instance of {@code clazz} and for which {@code
   * condition} returns {@code true}. If no such resource is found, an empty {@code List} is
   * returned.
   *
   * @param <T> Type of the class represented by {@code clazz}.
   * @param content bundle to be queried
   * @param clazz Class the returned resources must be instance of
   * @param condition a predicate for which the returned values must hold.
   * @return an {@code List} holding the found resources, an empty List if no resource was found.
   */
  public static <T extends Resource> List<T> findResources(
      final Bundle content, final Class<T> clazz, final Predicate<? super T> condition) {
    return streamResourcesOfClass(content, clazz).filter(condition).toList();
  }

  private static <T extends Resource> Stream<T> streamResourcesOfClass(
      Bundle content, Class<T> clazz) {
    return content.getEntry().stream()
        .map(BundleEntryComponent::getResource)
        .filter(clazz::isInstance)
        .map(clazz::cast);
  }
}

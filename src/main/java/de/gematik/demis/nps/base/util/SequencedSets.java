package de.gematik.demis.nps.base.util;

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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.SequencedSet;
import java.util.Set;

/**
 * Provides helper methods to build SequencedSets similar to {@link Set#of()}. The returned
 * implementations are not thread-safe, forbid null values and are unmodifiable according to {@link
 * Collections#unmodifiableSequencedSet(SequencedSet)};
 */
public class SequencedSets {

  public static <E> SequencedSet<E> of() {
    return Collections.emptyNavigableSet();
  }

  public static <E> SequencedSet<E> of(E e) {
    Objects.requireNonNull(e);
    final LinkedHashSet<E> es = new LinkedHashSet<>(1, 1);
    es.add(e);
    return Collections.unmodifiableSequencedSet(es);
  }

  public static <E> SequencedSet<E> of(E e1, E e2) {
    Objects.requireNonNull(e1);
    Objects.requireNonNull(e2);
    final LinkedHashSet<E> es = new LinkedHashSet<>(2, 1);
    es.add(e1);
    es.add(e2);
    return Collections.unmodifiableSequencedSet(es);
  }
}

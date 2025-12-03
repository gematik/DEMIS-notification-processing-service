package de.gematik.demis.nps.api;

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

import de.gematik.demis.service.base.security.jwt.Token;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * A wrapper around {@link Token}. We don't want to use the original because it's interface is
 * outdated and we really only need access to a set of roles.
 */
public record JwtToken(@Nonnull Set<String> roles) {

  /** A null-object instance to avoid null */
  public static final JwtToken EMPTY = new JwtToken(Set.of());

  @Nonnull
  public static JwtToken from(@CheckForNull final Token token) {
    if (token == null) {
      return new JwtToken(Set.of());
    }

    final List<String> roles = Objects.requireNonNullElse(token.roles(), List.of());
    return new JwtToken(Set.copyOf(roles));
  }
}

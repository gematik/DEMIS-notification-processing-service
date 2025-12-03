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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik,
 * find details in the "Readme" file.
 * #L%
 */

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Taken and striped from
 * https://github.com/eugenp/tutorials/blob/eb633a5b19658f8c2afc176c4dfc5510540ed10d/core-java-modules/core-java-uuid/src/main/java/com/baeldung/uuid/UUIDGenerator.java
 * is under MIT license
 */
public final class Uuid5Generator {

  private Uuid5Generator() {}

  @SuppressWarnings("java:S4790") // It is UUID 5 Standard to use SHA-1
  public static UUID generateType5UUID(String name) {

    try {

      final byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
      final MessageDigest md = MessageDigest.getInstance("SHA-1");

      final byte[] hash = md.digest(bytes);

      long msb = getLeastAndMostSignificantBitsVersion5(hash, 0);
      long lsb = getLeastAndMostSignificantBitsVersion5(hash, 8);
      // Set the version field
      msb &= ~(0xfL << 12);
      msb |= 5L << 12;
      // Set the variant field to 2
      lsb &= ~(0x3L << 62);
      lsb |= 2L << 62;
      return new UUID(msb, lsb);

    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  private static long getLeastAndMostSignificantBitsVersion5(final byte[] src, final int offset) {
    long ans = 0;
    for (int i = offset + 7; i >= offset; i -= 1) {
      ans <<= 8;
      ans |= src[i] & 0xffL;
    }
    return ans;
  }
}

package de.gematik.demis.nps.service.notification;

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

import static de.gematik.demis.nps.service.notification.NotificationTypeResolver.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.error.NpsServiceException;
import java.io.IOException;
import java.io.StringReader;
import org.springframework.stereotype.Component;

/**
 * Utility for extracting the first {@code Bundle.meta.profile[0]} URL from a JSON notification
 * payload
 */
@Component
class ProfileReaderJson {

  private static final String NOT_A_JSON_OBJECT = "Root is not a JSON object";
  private final JsonFactory jsonFactory;

  public ProfileReaderJson(ObjectMapper objectMapper) {
    this.jsonFactory = objectMapper.getFactory();
  }

  /**
   * Extracts the first {@code Bundle.meta.profile[0]} URL from a JSON notification payload.
   *
   * <p>The payload may contain either a FHIR {@code Bundle} as the root resource or a {@code
   * Parameters} resource wrapping the Bundle at {@code parameter[].resource}.
   *
   * <p><strong>Implementation note</strong><br>
   * This method intentionally uses Jackson's streaming API instead of building a full JSON tree.
   * The code therefore looks more verbose than a typical ObjectMapper-based solution, but it avoids
   * fully parsing untrusted input. Only the minimal parts of the document required to locate {@code
   * Bundle.meta.profile} are traversed, while all other subtrees are skipped immediately.
   *
   * <p>This approach limits memory usage, avoids unnecessary work on large payloads, and reduces
   * exposure to malicious inputs containing deeply nested or irrelevant structures.
   *
   * @param json JSON notification payload
   * @return the first {@code Bundle.meta.profile[0]} URL string, or {@code null} if not present
   * @throws NpsServiceException if the JSON is malformed
   */
  public String readProfile(String json) {
    String rootType = readRootResourceType(json);
    if (rootType == null) return null;

    return switch (rootType) {
      case BUNDLE_VALUE -> readProfileFromRootBundle(json);
      case PARAMETERS_VALUE -> readProfileFromParametersWrapper(json);
      default -> null;
    };
  }

  private String readRootResourceType(String json) {
    try (JsonParser p = jsonFactory.createParser(new StringReader(json))) {

      if (p.nextToken() != JsonToken.START_OBJECT) {
        throw parsingError(NOT_A_JSON_OBJECT);
      }

      while (p.nextToken() != JsonToken.END_OBJECT) {
        if (p.currentToken() != JsonToken.FIELD_NAME) continue;

        String field = p.currentName();
        JsonToken v = p.nextToken();

        if (RESOURCE_TYPE_FIELD.equals(field) && v == JsonToken.VALUE_STRING) {
          return p.getText();
        }

        // Hardening: never traverse root containers in this pass
        skipIfContainer(p, v);
      }

      return null;

    } catch (IOException e) {
      throw new NpsServiceException(
          ErrorCode.INVALID_REQUEST_PAYLOAD, "Unable to parse FHIR JSON.", e);
    }
  }

  private String readProfileFromRootBundle(String json) {
    try (JsonParser p = jsonFactory.createParser(new StringReader(json))) {

      if (p.nextToken() != JsonToken.START_OBJECT) {
        throw parsingError(NOT_A_JSON_OBJECT);
      }

      return readProfileIfBundleObject(p);

    } catch (IOException e) {
      throw new NpsServiceException(
          ErrorCode.INVALID_REQUEST_PAYLOAD, "Unable to parse FHIR Bundle JSON.", e);
    }
  }

  private String readProfileFromParametersWrapper(String json) {
    try (JsonParser p = jsonFactory.createParser(new StringReader(json))) {

      if (p.nextToken() != JsonToken.START_OBJECT) {
        throw parsingError(NOT_A_JSON_OBJECT);
      }

      while (p.nextToken() != JsonToken.END_OBJECT) {
        if (p.currentToken() != JsonToken.FIELD_NAME) continue;

        String field = p.currentName();
        JsonToken v = p.nextToken();

        if (!PARAMETER_FIELD.equals(field)) {
          skipIfContainer(p, v);
          continue;
        }

        if (v != JsonToken.START_ARRAY) {
          return null;
        }

        return readProfileFromFirstBundleInsideParameterArray(p);
      }

      return null;

    } catch (IOException e) {
      throw new NpsServiceException(
          ErrorCode.INVALID_REQUEST_PAYLOAD, "Unable to parse FHIR Parameters JSON.", e);
    }
  }

  private String readProfileFromFirstBundleInsideParameterArray(JsonParser p) throws IOException {
    // p currently at START_ARRAY (parameter)
    while (p.nextToken() != JsonToken.END_ARRAY) {
      if (p.currentToken() != JsonToken.START_OBJECT) {
        p.skipChildren();
        continue;
      }

      // one parameter object
      while (p.nextToken() != JsonToken.END_OBJECT) {
        if (p.currentToken() != JsonToken.FIELD_NAME) continue;

        String field = p.currentName();
        JsonToken v = p.nextToken();

        if (!RESOURCE_FIELD.equals(field)) {
          skipIfContainer(p, v);
          continue;
        }

        if (v != JsonToken.START_OBJECT) {
          return null;
        }

        String profile = readProfileIfBundleObject(p);
        if (profile != null) {
          return profile;
        }
      }
    }

    return null;
  }

  private String readProfileIfBundleObject(JsonParser p) throws IOException {
    boolean isBundle = false;
    String profileCandidate = null;

    while (p.nextToken() != JsonToken.END_OBJECT) {
      if (p.currentToken() != JsonToken.FIELD_NAME) continue;

      String field = p.currentName();
      JsonToken v = p.nextToken();

      if (RESOURCE_TYPE_FIELD.equals(field)) {
        if (v == JsonToken.VALUE_STRING) {
          isBundle = BUNDLE_VALUE.equals(p.getText());
        } else {
          skipIfContainer(p, v);
        }
        // If we already parsed meta.profile earlier, we can now return immediately
        if (isBundle && profileCandidate != null) {
          return profileCandidate;
        }
        continue;
      }

      if (META_FIELD.equals(field)) {
        if (v == JsonToken.START_OBJECT) {
          profileCandidate = readFirstProfileFromMetaObject(p);
        } else {
          // meta exists but is not an object -> ignore
        }
        // If we already know it's a Bundle, we can return immediately
        if (isBundle && profileCandidate != null) {
          return profileCandidate;
        }
        continue;
      }

      skipIfContainer(p, v);
    }

    return isBundle ? profileCandidate : null;
  }

  /** Reads inside meta object (parser currently positioned at START_OBJECT of meta). */
  private String readFirstProfileFromMetaObject(JsonParser p) throws IOException {
    String profileCandidate = null;
    while (p.nextToken() != JsonToken.END_OBJECT) {
      if (p.currentToken() != JsonToken.FIELD_NAME) continue;

      String field = p.currentName();
      p.nextToken(); // move to field value

      if (!PROFILE_FIELD.equals(field)) {
        p.skipChildren();
        continue;
      }

      if (p.currentToken() != JsonToken.START_ARRAY) {
        throw parsingError(String.format("%s.%s must be a JSON array.", META_FIELD, PROFILE_FIELD));
      }

      // return first string in the array (ignore non-strings)
      while (p.nextToken() != JsonToken.END_ARRAY) {
        if (p.currentToken() == JsonToken.VALUE_STRING && profileCandidate == null) {
          profileCandidate = p.getValueAsString();
        }
        p.skipChildren();
      }
    }

    return profileCandidate;
  }

  private static void skipIfContainer(JsonParser p, JsonToken t) throws IOException {
    if (t == JsonToken.START_OBJECT || t == JsonToken.START_ARRAY) {
      p.skipChildren();
    }
  }

  private static NpsServiceException parsingError(String message) {
    return new NpsServiceException(ErrorCode.INVALID_REQUEST_PAYLOAD, message);
  }
}

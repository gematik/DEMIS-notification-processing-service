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
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 * #L%
 */

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;

import com.google.common.base.Strings;
import de.gematik.demis.fhirparserlibrary.MessageType;
import de.gematik.demis.nps.config.TestUserConfiguration;
import de.gematik.demis.nps.error.ErrorCode;
import de.gematik.demis.nps.error.NpsServiceException;
import de.gematik.demis.nps.service.Processor;
import de.gematik.demis.nps.service.response.FhirConverter;
import jakarta.validation.constraints.NotBlank;
import java.util.Objects;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

@RestController
@Slf4j
public class NotificationController {

  public static final String HEADER_SENDER = "x-sender";
  public static final String HEADER_TEST_USER_RECIPIENT = "x-testuser-recipient";
  public static final String HEADER_IS_TEST_NOTIFICATION = "x-testuser";

  private final Processor processor;
  private final FhirConverter fhirConverter;
  private final TestUserConfiguration testUserConfiguration;
  private final boolean isNewTestRoutingEnabled;

  @Autowired
  public NotificationController(
      final Processor processor,
      final FhirConverter fhirConverter,
      final TestUserConfiguration testUserConfiguration,
      @Value("${feature.flag.test_routing_v2}") final boolean isNewTestRoutingEnabled) {
    this.processor = processor;
    this.fhirConverter = fhirConverter;
    this.testUserConfiguration = testUserConfiguration;
    this.isNewTestRoutingEnabled = isNewTestRoutingEnabled;
  }

  @PostMapping(
      path = "fhir/$process-notification",
      consumes = {
        APPLICATION_JSON_VALUE,
        "application/json+fhir",
        "application/fhir+json",
        APPLICATION_XML_VALUE,
        "application/xml+fhir",
        "application/fhir+xml",
        "text/xml"
      },
      produces = {
        APPLICATION_JSON_VALUE,
        "application/json+fhir",
        "application/fhir+json",
        APPLICATION_XML_VALUE,
        "application/xml+fhir",
        "application/fhir+xml",
        "text/xml"
      })
  public ResponseEntity<Object> processNotification(
      @RequestBody @NotBlank final String fhirNotification,
      @RequestHeader(CONTENT_TYPE) final MediaType contentType,
      @RequestHeader(value = "X-Request-ID", required = false) final String requestId,
      @CheckForNull @RequestHeader(value = HEADER_SENDER, required = false) final String sender,
      @RequestHeader(value = HEADER_IS_TEST_NOTIFICATION, required = false)
          final boolean isTestNotification,
      @CheckForNull @RequestHeader(value = HEADER_TEST_USER_RECIPIENT, required = false)
          final String testUserRecipient,
      @RequestHeader(value = AUTHORIZATION, required = false) String authorization,
      final WebRequest request) {
    if ("text".equalsIgnoreCase(contentType.getType())) {
      log.info("sender= {}, deprecated contentType= {}", sender, contentType);
    }

    final TestUserProps testUserProps;
    if (isNewTestRoutingEnabled) {
      testUserProps = getTestUserProps(isTestNotification, testUserRecipient, sender);
    } else {
      testUserProps = getLegacyTestUserProps(isTestNotification, sender, testUserConfiguration);
    }

    final Parameters response =
        processor.execute(
            fhirNotification,
            MessageType.getMessageType(contentType.getSubtype()),
            requestId,
            sender,
            testUserProps.isTestNotification(),
            testUserProps.testUserRecipient(),
            authorization);
    return fhirConverter.setResponseContent(ResponseEntity.ok(), response, request);
  }

  @Nonnull
  private TestUserProps getTestUserProps(
      final boolean isTestNotification,
      @CheckForNull String testUserRecipient,
      @CheckForNull String sender) {
    if (!isTestNotification) {
      return new TestUserProps(false, "");
    }

    testUserRecipient = Strings.nullToEmpty(testUserRecipient);
    if (testUserRecipient.isBlank()) {
      throw new NpsServiceException(
          ErrorCode.INVALID_TEST_CONFIGURATION,
          "Is test request, but no recipient provided with header");
    }

    sender = Strings.nullToEmpty(sender);
    if ("$sender".equals(testUserRecipient) && sender.isBlank()) {
      throw new NpsServiceException(
          ErrorCode.INVALID_TEST_CONFIGURATION,
          "Is test request, recipient is $sender but no sender provided with header");
    }
    if ("$sender".equals(testUserRecipient)) {
      testUserRecipient = sender;
    }

    return new TestUserProps(isTestNotification, testUserRecipient);
  }

  @Nonnull
  private TestUserProps getLegacyTestUserProps(
      final boolean isTestUser,
      @CheckForNull final String sender,
      @Nonnull final TestUserConfiguration testUserConfiguration) {
    // pre feature flag
    final boolean isTestNotification = isTestUser || testUserConfiguration.isTestUser(sender);
    String forwardTo = "";
    if (isTestNotification) {
      forwardTo = Objects.requireNonNullElse(testUserConfiguration.getReceiver(sender), "");
    }
    return new TestUserProps(isTestNotification, forwardTo);
  }

  private record TestUserProps(boolean isTestNotification, @Nonnull String testUserRecipient) {}
}

package de.gematik.demis.nps.service.processing;

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

import static org.assertj.core.api.Assertions.assertThatException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import de.gematik.demis.notification.builder.demis.fhir.notification.builder.infectious.laboratory.NotificationBundleLaboratoryAnonymousDataBuilder;
import de.gematik.demis.nps.service.notification.Notification;
import de.gematik.demis.nps.test.RoutingDataUtil;
import de.gematik.demis.service.base.error.ServiceCallException;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

class DlsServiceTest {

  private static final Composition composition = new Composition();

  static {
    composition.setId("composition");
    composition.setIdentifier(new Identifier().setValue("b96224fe-e388-4965-844e-5b43267dda2a"));
  }

  private static final Notification NOTIFICATION =
      Notification.builder()
          .bundle(
              new NotificationBundleLaboratoryAnonymousDataBuilder()
                  .setNotificationLaboratory(composition)
                  .build())
          .routingData(RoutingDataUtil.emptyFor("1.", "custodian"))
          .build();

  private static final DlsServiceClient DLS_SERVICE_CLIENT = mock(DlsServiceClient.class);
  private DlsService service;

  @BeforeEach
  void setup() {
    Mockito.reset(DLS_SERVICE_CLIENT);
    service = new DlsService(DLS_SERVICE_CLIENT, true);
  }

  @Test
  void thatClientIsCalled() {
    assertThatNoException().isThrownBy(() -> service.store(NOTIFICATION));
    verify(DLS_SERVICE_CLIENT).store(any());
  }

  @Nested
  class FeatureFlag {
    @Test
    void thatNoOpIfFeatureFlagIsDisabled() {
      service = new DlsService(DLS_SERVICE_CLIENT, false);
      assertThatNoException().isThrownBy(() -> service.store(NOTIFICATION));
      verifyNoInteractions(DLS_SERVICE_CLIENT);
    }
  }

  @Nested
  class ExceptionHandling {
    @MethodSource("serviceCallExceptions")
    @ParameterizedTest
    void thatExceptionsRaisedForWritingAreDismissed(@Nonnull final Exception raiseException) {
      doThrow(raiseException).when(DLS_SERVICE_CLIENT).store(any());
      assertThatNoException().isThrownBy(() -> service.store(NOTIFICATION));
    }

    @MethodSource("runtimeExceptions")
    @ParameterizedTest
    void thatOtherExceptionsAreRethrownWhenWriting(@Nonnull final Exception raiseException) {
      doThrow(raiseException).when(DLS_SERVICE_CLIENT).store(any());
      assertThatException().isThrownBy(() -> service.store(NOTIFICATION)).isEqualTo(raiseException);
    }

    private static Stream<Arguments> serviceCallExceptions() {
      return Stream.of(
          Arguments.of(
              new ServiceCallException(
                  "Internal", "any", HttpStatus.INTERNAL_SERVER_ERROR.value(), null)),
          Arguments.of(
              new ServiceCallException("Bad request", "any", HttpStatus.BAD_REQUEST.value(), null)),
          Arguments.of(
              new ServiceCallException("Gateway", "any", HttpStatus.GATEWAY_TIMEOUT.value(), null)),
          Arguments.of(
              new ServiceCallException("Forbidden", "any", HttpStatus.FORBIDDEN.value(), null)));
    }

    private static Stream<Arguments> runtimeExceptions() {
      return Stream.of(
          Arguments.of(new NullPointerException()), Arguments.of(new IllegalArgumentException()));
    }
  }
}

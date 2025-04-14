package de.gematik.demis.nps.config;

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

import java.util.List;

/**
 * Wrap configuration properties for handling test notifications in production(sic!) and other
 * environments. As part of our monitoring we verify that notifications are send correctly. This
 * record offers the ability to redirect notifications to test users.
 *
 * @param ids Known test users
 * @param healthOffice A fallback in case we want to forward to a test user, but no test user is
 *     selected
 * @param senderIsDestination Whether to forward to test users or to the fallback setting
 */
public record TestUserConfiguration(
    List<String> ids, String healthOffice, boolean senderIsDestination) {
  public boolean isTestUser(final String sender) {
    return (ids().contains(sender));
  }

  /**
   * Look up a test user id for the given sender.
   *
   * @return If the sender already is a test user and we are allowed to forward to test users,
   *     simply return sender. Otherwise, return the configured {@link
   *     TestUserConfiguration#healthOffice}.
   */
  public String getReceiver(final String sender) {
    if (isTestUser(sender) && isForwardToTestUserEnabled()) {
      return sender;
    }

    return healthOffice();
  }

  /**
   * @return true if a test user can be the receiver, or if the configured @{@link
   *     TestUserConfiguration#healthOffice} should be the receiver.
   */
  private boolean isForwardToTestUserEnabled() {
    return senderIsDestination();
  }
}

package de.gematik.demis.nps.base.util;

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

import de.gematik.demis.nps.service.notification.NotificationType;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Getter
@Setter
@Component
@RequestScope
public class RequestProcessorState {
  // Basic properties
  private String bundleId;
  private NotificationType type;
  private String diseaseCode;
  private String sender;
  private String receiver;
  private boolean testUser;

  // Processing step results
  private Boolean validationSuccessful = null;
  private Boolean lifecycleValidationSuccessful = null;
  private Boolean routingSuccessful = null;
  private Boolean notificationStorageSuccessful = null;
  private Boolean contextEnrichmentSuccessful = null;
  private Boolean dlsSuccessful = null;
  private Boolean pdfGenerationSuccessful = null;
  private Boolean bundleActionsSuccessful = null;
  private Boolean pseudonymizationSuccessful = null;

  public void setPseudonymizationSuccessful(Boolean pseudonymizationSuccessful) {
    if (this.pseudonymizationSuccessful != null && !this.pseudonymizationSuccessful) {
      return;
    }
    this.pseudonymizationSuccessful = pseudonymizationSuccessful;
  }

  public Boolean isSuccessfullyProcessed() {
    return checkBooleanField(validationSuccessful)
        && checkBooleanField(lifecycleValidationSuccessful)
        && checkBooleanField(routingSuccessful)
        && checkBooleanField(notificationStorageSuccessful)
        && checkBooleanField(bundleActionsSuccessful);
  }

  private boolean checkBooleanField(Boolean field) {
    return field != null && field;
  }

  public String formatRequestProcessorState() {
    StringBuilder builder = new StringBuilder();
    appendField(builder, "bundleId", this.getBundleId());
    appendField(builder, "type", this.getType());
    appendField(builder, "diseaseCode", this.getDiseaseCode());
    appendField(builder, "sender", this.getSender());
    appendField(builder, "receiver", this.getReceiver());
    appendField(builder, "testUser", this.isTestUser());
    appendField(builder, "result", transformBoolean(this.isSuccessfullyProcessed()));
    appendField(builder, "vs", transformBoolean(this.validationSuccessful));
    appendField(builder, "nrs", transformBoolean(this.routingSuccessful));
    appendField(builder, "lvs", transformBoolean(this.lifecycleValidationSuccessful));
    appendField(builder, "ces", transformBoolean(this.contextEnrichmentSuccessful));
    appendField(builder, "bundleActions", transformBoolean(this.bundleActionsSuccessful));
    appendField(builder, "ps", transformBoolean(this.pseudonymizationSuccessful));
    appendField(builder, "fsw", transformBoolean(this.notificationStorageSuccessful));
    appendField(builder, "dls", transformBoolean(this.dlsSuccessful));
    appendField(builder, "pdfgen", transformBoolean(this.pdfGenerationSuccessful));
    return builder.toString();
  }

  private void appendField(final StringBuilder builder, final String name, final Object value) {
    if (!builder.isEmpty()) {
      builder.append(", ");
    } else {
      builder.append("Notification: ");
    }

    builder.append(name).append('=').append(value == null ? "" : value);
  }

  private String transformBoolean(@Nullable Boolean value) {
    if (value == null) {
      return "unset";
    }
    return value ? "success" : "failed";
  }
}

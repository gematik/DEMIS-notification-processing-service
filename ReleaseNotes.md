<div style="text-align:right"><img src="media/Gematik_Logo_Flag_small.png" alt="gematik GmbH Logo"/> <br/> </div>

# Release Notification Processing Service

## 2.7.2
- remove feature flag FEATURE_FLAG_LV_DISEASE
- add http://fhir.de/StructureDefinition/gender-amtlich-de to whitelist for notification cleaning
- add new excerpt creation through notification builder library.
- bump spring parent to 2.14.7
- add feature flag FEATURE_FLAG_NBL_FOR_NOTBYNAME_ENABLED

## 2.7.1
- bump spring parent to 2.14.2
- add additional info to error message for a validation error in lvs processing
- error id in operation-outcome moved from location to diagnostics (FEATURE_FLAG_MOVE_ERROR_ID_TO_DIAGNOSTICS)
- add validation of notificationId to be UUID, before sending request to store notification to DLS, skip storing if invalid

## 2.7.0

- bump spring parent to 2.13.4
- remove FEATURE_FLAG_TEST_ROUTING_V2
- introduce FEATURE_FLAG_FOLLOW_UP_NOTIFICATIONS and respective structures to
  update DLS

## 2.6.1

- Adding extra header for requests to FUTS new APIs
- forward x-sender header to validation service
- add option to verify principal has required roles to send notification
- add option to write a custodian to a receipt bundle for test notifications
- Bump spring-parent to 2.12.13

## 2.6.0

- add API_ENTRYPOINT configuration for new api endpoints

## 2.5.0

- add feature flag FEATURE_FLAG_NEW_API_ENDPOINTS for header propagation of x-fhir-api-version and x-fhir-profile to
  validation-service

## 2.4.3

- fix issue with §7.4 notification and pdf creation

## 2.4.2

- update Spring-Parent with new Notification-Builder-Library
- add default feature flags FEATURE_FLAG_NOTIFICATION_PRE_CHECK, FEATURE_FLAG_LV_DISEASE to values.yaml
- handle 422 from NRS

## 2.4.1

- add support for new NRS Action

## 2.4.0

- Remove Feature Flag for 7.4
- Removal of deprecated NRS client
- Removal of deprecated Pseudo-Storage Service (PSS) client
- additional §7.3 handling
- update spring parent
- remove all NCAPI references

## 2.3.2

- Remove Feature Flag for 7.4
- Removal of deprecated NRS client

## 2.3.1

- fixed relaxed validation feature flag naming

## 2.3.0

- Renamed feature flag for relaxed validation to `feature.flag.relaxed_validation` and disabled by default
- Updated dependencies

## 2.2.0

- Introduced Feature Flag to disable the communication with the Pseudonymization Storage Service (PSS)
- uses lenient parser when relaxed validation was used to send data to NRS

## 2.1.1

- change base chart to istio hostnames
- updating dependencies

## 2.1.0

- lift test user configuration up to controller
- prepare new test user configuration mechanism based on HTTP headers
- setting new resources in helm chart
- setting new timeouts and retries in helm chart

## 2.0.1

- disable bundle validation for test notifications and rki recipient
- allow multiple notifications for one recipient
- Updated ospo-resources for adding additional notes and disclaimer

## 2.0.0

- Add service API documentation
- Update §7.4 bundles

## 1.5.0

- Enabling of §7.3 notifications
- updated further health department contact data

## 1.4.0

### changed

- First official GitHub-Release
- Connecting NPS and Certificate-Update-Service (CUS)
- Activation of Context-Enrichment-Service
- Enabling of §7.4 notifications
- Dependency-Updates (CVEs et al.)
- Update Base-Image to OSADL

## 1.1.0

### changed

- Removed feature flag for communicating with FUTS to retrieve ConceptMaps
- The Retrieval of ConceptMaps is now always done by contacting FUTS

## 1.0.5

### fixed

- CVEs

## 1.0.4

### added

- Feature flag for communicating with FUTS to retrieve ConceptMaps
- Feature flag for communicating with Lifecycle Validation Service

### fixed

- CVEs

## 1.0.3

### fixed

- CVEs

## 1.0.2

### fixed

- CVEs

## 1.0.1

### fixed

- CVEs

## 1.0.0

Initial Release

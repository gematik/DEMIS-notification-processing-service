<img align="right" width="250" height="47" src="media/Gematik_Logo_Flag.png" alt="gematik GmbH Logo"/> <br/> 

# Release Notification Processing Service

## Release 2.6.0
- add API_ENTRYPOINT configuration for new api endpoints

## Release 2.5.0
- add feature flag FEATURE_FLAG_NEW_API_ENDPOINTS for header propagation of x-fhir-api-version and x-fhir-profile to validation-service
- fix issue with §7.4 notification and pdf creation

## Release 2.4.2
- update Spring-Parent with new Notification-Builder-Library
- add default feature flags FEATURE_FLAG_NOTIFICATION_PRE_CHECK, FEATURE_FLAG_LV_DISEASE to values.yaml
- handle 422 from NRS

## Release 2.4.1
- add support for new NRS Action

## Release 2.4.0
- Remove Feature Flag for 7.4
- Removal of deprecated NRS client
- Removal of deprecated Pseudo-Storage Service (PSS) client
- additional §7.3 handling
- update spring parent
- remove all NCAPI references

## Release 2.3.2
- Remove Feature Flag for 7.4
- Removal of deprecated NRS client

## Release 2.3.1
- fixed relaxed validation feature flag naming

## Release 2.3.0
- Renamed feature flag for relaxed validation to `feature.flag.relaxed_validation` and disabled by default
- Updated dependencies

## Release 2.2.0
- Introduced Feature Flag to disable the communication with the Pseudonymization Storage Service (PSS)
- uses lenient parser when relaxed validation was used to send data to NRS

## Release 2.1.1
- change base chart to istio hostnames
- updating dependencies

## Release 2.1.0
- lift test user configuration up to controller
- prepare new test user configuration mechanism based on HTTP headers
- setting new ressources in helm chart
- setting new timeouts and retries in helm chart

## Release 2.0.1
- disable bundle validation for test notifications and rki recipient
- allow multiple notifications for one recipient
- Updated ospo-resources for adding additional notes and disclaimer

## Release 2.0.0
- Add service API documentation
- Update §7.4 bundles 

## Release 1.5.0
- Enabling of §7.3 notifications
- updated further health department contact data

## Release 1.4.0
### changed
- First official GitHub-Release
- Connecting NPS and Certificate-Update-Service (CUS)
- Activation of Context-Enrichtment-Service
- Enabling of §7.4 notifications 
- Dependency-Updates (CVEs et al.)
- Update Base-Image to OSADL

## Release 1.1.0
### changed
- Removed feature flag for communicating with FUTS to retrieve ConceptMaps
- The Retrieval of ConceptMaps is now always done by contacting FUTS

## Release 1.0.5

### fixed
- CVEs

## Release 1.0.4

### added
- Feature flag for communicating with FUTS to retrieve ConceptMaps
- Feature flag for communicating with Lifecycle Validation Service

### fixed
- CVEs

## Release 1.0.3

### fixed
- CVEs

## Release 1.0.2

### fixed
- CVEs

## Release 1.0.1

### fixed
- CVEs


## Release 1.0.0

Initial Release

<img align="right" width="250" height="47" src="media/Gematik_Logo_Flag.png"/> <br/>

# Notification Processing Service

<details>
  <summary>Table of Contents</summary>
  <ol>
    <li>
      <a href="#about-the-project">About The Project</a>
       <ul>
        <li><a href="#status">Status</a></li>
        <li><a href="#release-notes">Release Notes</a></li>
      </ul>
	</li>
    <li>
      <a href="#getting-started">Getting Started</a>
      <ul>
        <li><a href="#docker-build">Docker build</a></li>
        <li><a href="#docker-run">Docker run</a></li>
        <li><a href="#intellij-cmd">Intellij/CMD</a></li>
        <li><a href="#kubernetes">Kubernetes</a></li>
        <li><a href="#endpoints">Endpoints</a></li>
      </ul>
    </li>
    <li><a href="#security-policy">Security Policy</a></li>
    <li><a href="#contributing">Contributing</a></li>
    <li><a href="#license">License</a></li>
    <li><a href="#contact">Contact</a></li>
  </ol>
</details>

## About The Project

This service serves as a validation service for all notifications send to DEMIS. It uses a snapshot of all profiles and
the DEMIS-Schemas Project to validate any notification.

### Quality Gate

[![Quality Gate Status](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Anotification-processing-service&metric=alert_status&token=sqb_3c53ebb2ac4a23ddd181b73267d22d5bddb52ffb)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Anotification-processing-service)[![Vulnerabilities](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Anotification-processing-service&metric=vulnerabilities&token=sqb_3c53ebb2ac4a23ddd181b73267d22d5bddb52ffb)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Anotification-processing-service)[![Bugs](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Anotification-processing-service&metric=bugs&token=sqb_3c53ebb2ac4a23ddd181b73267d22d5bddb52ffb)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Anotification-processing-service)[![Code Smells](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Anotification-processing-service&metric=code_smells&token=sqb_3c53ebb2ac4a23ddd181b73267d22d5bddb52ffb)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Anotification-processing-service)[![Lines of Code](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Anotification-processing-service&metric=ncloc&token=sqb_3c53ebb2ac4a23ddd181b73267d22d5bddb52ffb)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Anotification-processing-service)[![Coverage](https://sonar.prod.ccs.gematik.solutions/api/project_badges/measure?project=de.gematik.demis%3Anotification-processing-service&metric=coverage&token=sqb_3c53ebb2ac4a23ddd181b73267d22d5bddb52ffb)](https://sonar.prod.ccs.gematik.solutions/dashboard?id=de.gematik.demis%3Anotification-processing-service)


### Release Notes

See [ReleaseNotes](ReleaseNotes.md) for all information regarding the (newest) releases.

## Getting Started

The application requires the DEMIS FHIR Profiles. This image is maintained in DockerHub: 
[demis-profile-snapshots](https://hub.docker.com/repository/docker/gematik1/demis-fhir-profile-snapshots/general).

The profiles are require to execute the unit and integration tests included in this repository. At runtime execution the
profile files must be available in a folder and this folder must be specified through the environment
variable `FHIR_PROFILES_PATH`.

### Individual Changes
In order to be able to run the tests in full, a directory “certificates” with 2 certificates in DER format including 
p12 file must currently be stored under src/test/resources:
* src/test/resources/certificates/1.01.0.53.der
* src/test/resources/certificates/1.01.0.53.p12
* src/test/resources/certificates/test-int.der
* src/test/resources/certificates/test-int.p12


### Installation

```sh
mvn clean verify
```

The Project can be built with the following command:

```sh
mvn -e clean install -DskipTests=true
```
build with docker image:

```sh
docker build -t notification-processing-service:latest .
```
The Docker Image associated to the service can be built alternatively with the extra profile `docker`:

```sh
mvn -e clean install -Pdownload-profile -Pdocker
```

Without Profiles
```sh
mvn -e clean install -DskipTests=true -Pdocker
```

The application can be started as Docker container with the following commands:

```shell
docker run --rm --name notification-processing-service -p 8080:8080 notification-processing-service:latest
```
## Kubernetes

## Intellij/CMD
Start the spring boot server with: `mvn clean spring-boot:run`
Check the server with: `curl -v localhost:8080/actuator/health`

## Usage
Start the spring boot server with: `mvn clean spring-boot:run`
Check the server with: `curl -v localhost:8080/actuator/health`

## Security Policy
If you want to see the security policy, please check our [SECURITY.md](.github/SECURITY.md).

## Contributing
If you want to contribute, please check our [CONTRIBUTING](.github/CONTRIBUTING.md).

## License

Copyright 2022-2025 gematik GmbH

EUROPEAN UNION PUBLIC LICENCE v. 1.2

EUPL © the European Union 2007, 2016

See the [LICENSE](./LICENSE) for the specific language governing permissions and limitations under the License

## Additional Notes and Disclaimer from gematik GmbH

1. Copyright notice: Each published work result is accompanied by an explicit statement of the license conditions for use. These are regularly typical conditions in connection with open source or free software. Programs described/provided/linked here are free software, unless otherwise stated.
2. Permission notice: Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
   1. The copyright notice (Item 1) and the permission notice (Item 2) shall be included in all copies or substantial portions of the Software.
   2. The software is provided "as is" without warranty of any kind, either express or implied, including, but not limited to, the warranties of fitness for a particular purpose, merchantability, and/or non-infringement. The authors or copyright holders shall not be liable in any manner whatsoever for any damages or other claims arising from, out of or in connection with the software or the use or other dealings with the software, whether in an action of contract, tort, or otherwise.
   3. We take open source license compliance very seriously. We are always striving to achieve compliance at all times and to improve our processes. If you find any issues or have any suggestions or comments, or if you see any other ways in which we can improve, please reach out to: ospo@gematik.de
3. Please note: Parts of this code may have been generated using AI-supported technology. Please take this into account, especially when troubleshooting, for security analyses and possible adjustments.

## Contact
E-Mail to [DEMIS Entwicklung](mailto:demis-entwicklung@gematik.de?subject=[GitHub]%20Notification-Processing-Service)

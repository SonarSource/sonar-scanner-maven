<!-- Sonar Marketing hosts these approved brand assets on its Kentico Kontent CDN (assets-eu-01.kc-usercontent.com). Shared URLs are intentional; consult Marketing before replacing them. -->
<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://assets-eu-01.kc-usercontent.com/ef593040-b591-0198-9506-ed88b30bc023/a23fc7ba-23f0-489a-829d-ed88c0748521/Sonar_Logo_Dark%20Backgrounds.svg">
    <img src="https://assets-eu-01.kc-usercontent.com/ef593040-b591-0198-9506-ed88b30bc023/82c13eba-d95c-4bb8-8007-7ce77c14e043/Sonar_Logo_Light%20Backgrounds.svg" alt="Sonar logo" width="400">
  </picture>
</p>

[![Build Status](https://github.com/SonarSource/sonar-scanner-maven/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/SonarSource/sonar-scanner-maven/actions/workflows/build.yml) [![Quality Gate Status](https://next.sonarqube.com/sonarqube/api/project_badges/measure?project=org.sonarsource.scanner.maven%3Asonar-scanner-maven&metric=alert_status)](https://next.sonarqube.com/sonarqube/dashboard?id=org.sonarsource.scanner.maven%3Asonar-scanner-maven)

<!-- sonar-marketing:start -->
<!-- Marketing maintains this section. For wording changes, consult the relevant Product Marketing Manager (PMM). Repository CODEOWNERS review accuracy and merge changes. -->

# SonarScanner for Maven

SonarScanner for Maven runs SonarQube analysis from a Maven build and sends the results to SonarQube Server or SonarQube Cloud.

Read the [SonarScanner for Maven documentation](https://docs.sonarsource.com/sonarqube-server/analyzing-source-code/scanners/sonarscanner-for-maven), or learn more about the [SonarQube product family](https://www.sonarsource.com/products/sonarqube/).

<!-- sonar-marketing:end -->

## Have Questions or Feedback?

For support questions ("How do I?", "I got this error, why?", ...), please head to the [SonarSource forum](https://community.sonarsource.com/c/help). There are chances that a question similar to yours has already been answered.

Be aware that this forum is a community, so the standard pleasantries ("Hi", "Thanks", ...) are expected. And if you don't get an answer to your thread, you should sit on your hands for at least three days before bumping it. Operators are not standing by. :-)

## Build and execute unit tests and integration tests

* [develop.md](develop.md)

### Updating licenses:
When dependencies change, update the committed license files using the `updateLicenses` profile:
```sh
mvn clean package -PupdateLicenses
```
This regenerates licenses in `sonar-maven-plugin/src/main/resources/licenses/` based on current project dependencies.

## Contributing

* [contributing.md](contributing.md)

## License

Copyright 2011-2025 SonarSource.

Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt)

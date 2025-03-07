# SonarScanner for Maven
The SonarScanner for Maven is recommended as the default scanner for Maven projects.

The ability to execute the SonarQube analysis via a regular Maven goal makes it available anywhere Maven is available (developer build, CI server, etc.), without the need to manually download, set up, and maintain a SonarQube scanner installation. The Maven build already has much of the information needed for SonarQube to successfully analyze a project. By preconfiguring the analysis based on that information, the need for manual configuration is reduced significantly.

## [Prerequisites](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-maven/#prerequisites "Prerequisites")


*   Maven 3.2.5+
*   At least the minimal version of Java supported by your SonarQube server is in use

## [Analyzing](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-maven/#analyzing "Analyzing")


Analyzing a Maven project consists of running a Maven goal: `sonar:sonar` from the directory that holds the main project `pom.xml`. You need to pass an [authentication token](https://docs.sonarsource.com/sonarqube/latest/user-guide/managing-tokens/ "authentication token") using one of the following options: 

*   Use the `sonar.token` property. For example, to set it through the command line, Execute `maven sonar:sonar -Dsonar.token=yourAuthenticationToken` and wait until the build has completed, then open the web page indicated at the bottom of the console output. You should now be able to browse the analysis results.
*   Create the `SONAR_TOKEN` environment variable and set the token as its value.

```
mvn clean verify sonar:sonar -Dsonar.token=myAuthenticationToken
```


In some situations you may want to run the `sonar:sonar` goal as a dedicated step. Be sure to use `install` as first step for multi-module projects

```
mvn clean install
mvn sonar:sonar -Dsonar.token=myAuthenticationToken
```


To specify the version of sonar-maven-plugin instead of using the latest:

```
mvn org.sonarsource.scanner.maven:sonar-maven-plugin:<version>:sonar
```

<div role="alert" class="css-1gsvwg e16d48ue1">
    <div class="css-n3zwgj e16d48ue0">
        <p>If sonar-maven-plugin is not defined by the project’s <code>pom.xml</code> file, we recommend specifying the version instead of using the latest to avoid breaking changes at an unwanted time.</p>
    </div>
</div>

To get coverage information, you'll need to generate the coverage report before the analysis and specify the location of the resulting report in an analysis parameter. See [test coverage](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/test-coverage/overview/ "test coverage") for details.

## [Configuring analysis](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-maven/#configuring-analysis "Configuring analysis")

Most analysis properties will be read from your project. If you would like to override the default values of specific additional parameters, configure the parameter names found on the [analysis parameters](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/analysis-parameters/ "analysis parameters") page in the `<properties>` section of your `pom.xml` like this:

```
<properties>
  <sonar.buildString> [...] </sonar.buildString>
</properties>
```


## [Sample project](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-maven/#sample-project "Sample project")

To help you get started, a simple project sample is available here: [https://github.com/SonarSource/sonar-scanning-examples/tree/master/sonar-scanner-maven/maven-basic](https://github.com/SonarSource/sonar-scanning-examples/tree/master/sonar-scanner-maven/maven-basic "https://github.com/SonarSource/sonar-scanning-examples/tree/master/sonar-scanner-maven/maven-basic")

## [Adjusting the analysis scope](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-maven/#analysis-scope "Adjusting the analysis scope")

The analysis scope of a project determines the source and test files to be analyzed. 

An initial analysis scope is set by default. With the SonarScanner for Maven, the initial analysis scope is:

*   For source files: all the files stored under `src/main/java` (in the root or module directories).
*   For test files: all the files stored under `src/test/java` (in the root or module directories). 

To adjust the analysis scope, you can:

*   Adjust the initial scope: see below.
*   Exclude specific files from the initial scope: see [Analysis scope](https://docs.sonarsource.com/sonarqube/latest/project-administration/analysis-scope/ "Analysis scope").
*   Exclude specific modules from the analysis: see below.

### [Adjusting the initial scope](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-maven/#adjusting-the-initial-scope "Adjusting the initial scope")

The initial scope is set through the `sonar.sources` property (for source files) and the `sonar.tests` property (for test files). See Analysis parameters for more information.

To adjust the initial scope, you can:

*   Either override these properties by setting them explicitly in your build like any other relevant maven property: see [Analysis scope](https://docs.sonarsource.com/sonarqube/latest/project-administration/analysis-scope/ "Analysis scope").
*   Or use the scanAll option to extend the initial scope to non-JVM-related files. See below.

### [Using the scanAll option to include non-JVM-related files](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-maven/#using-scanall "Using the scanAll option to include non-JVM-related files")

You may want to analyze not only the JVM main files but also files related to configuration, infrastructure, etc. An easy way to do that is to enable the scanAll option (By default, this option is disabled.).

If the scanAll option is enabled then the initial analysis scope of _source files_ will be:

*   The files stored in `src/main/java.`
*   The non-JVM-related files stored in the root directory of your project.

<div role="alert" class="css-1gsvwg e16d48ue1">
    <div class="css-n3zwgj e16d48ue0">
        <p>&nbsp;The scanAll option is disabled if the <code>sonar.sources</code> property is overridden.</p>
</div>
</div>

To enable the scanAll option:

*   Set the `sonar.maven.scanAll` property to `true`. 

### [Excluding a module from the analysis](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-maven/#excluding-a-module-from-the-analysis "Excluding a module from the analysis")

To exclude a module from the analysis, you may:

*   In the `pom.xml` of the module you want to exclude, define the  `<sonar.skip>true</sonar.skip>` property.
*   Use build profiles to exclude some modules (like for integration tests).
*   Use Advanced Reactor Options (such as `-pl`). For example `mvn sonar:sonar -pl !module2`

## [Other settings](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-maven/#other-settings "Other settings")


### [Locking down the version of the Maven plugin](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-maven/#locking-down-the-version-of-the-maven-plugin "Locking down the version of the Maven plugin")

It is recommended to lock down versions of Maven plugins:

```
<build>
  <pluginManagement>
    <plugins>
      <plugin>
        <groupId>org.sonarsource.scanner.maven</groupId>
        <artifactId>sonar-maven-plugin</artifactId>
        <version>yourPluginVersion</version>
      </plugin>
    </plugins>
  </pluginManagement>
</build>
```


### [If your SonarQube server is secured](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-maven/#if-your-sonarqube-server-is-secured "If your SonarQube server is secured")

If your SonarQube server is [configured with HTTPS](https://docs.sonarsource.com/sonarqube/latest/setup-and-upgrade/operating-the-server/#securing-the-server-behind-a-proxy "configured with HTTPS") and a self-signed certificate then you must add the self-signed certificate to the trusted CA certificates of the SonarScanner. In addition, if mutual TLS is used then you must define the access to the client certificate at the SonarScanner level.

See [Managing the TLS certificates on the client side](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/scanner-environment/manage-tls-certificates/ "Managing the TLS certificates on the client side").

## [Troubleshooting](https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner-for-maven/#troubleshooting "Troubleshooting")


**If you get a java.lang.OutOfMemoryError**

If you are using the SonarScanner for Maven version 5.0 or greater.

Set the `SONAR_SCANNER_JAVA_OPTS` environment variable, like this in Unix environments:

```
export SONAR_SCANNER_JAVA_OPTS="-Xmx512m"
```

In Windows environments, avoid the double quotes, since they get misinterpreted.

```
set SONAR_SCANNER_JAVA_OPTS=-Xmx512m
```

Otherwise, if you are using version 4.0 or earlier.

Set the `MAVEN_OPTS` environment variable, like this in Unix environments:
```
export MAVEN_OPTS="-Xmx512m"
```

In Windows environments, avoid the double quotes, since they get misinterpreted.

```
set MAVEN_OPTS=-Xmx512m
```

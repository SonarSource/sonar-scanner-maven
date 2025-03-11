Development notes
=================

Notes for developers of this Maven plugin.

### Build and run unit tests

* Configure Java 17
* Go to the repository root directory
* Execute: `mvn clean install`

### Working on this plugin

Once the project is built, go to the directory of the project to analyze and run a scan using your local build:
(replace `5.1.0-SNAPSHOT` with the version in your `pom.xml`)
```
mvn org.sonarsource.scanner.maven:sonar-maven-plugin:5.1.0-SNAPSHOT:sonar
```

### Testing

There are 3 type of tests:

#### Unit Tests
The Unit Tests are located in `sonar-maven-plugin/src/test/java`, and they can be run with `mvn test`

#### Invoker Tests
The Invoker tests are located in `sonar-maven-plugin/src/it`, and they can be run with `mvn verify`

The [maven-invoker-plugin](https://maven.apache.org/plugins/maven-invoker-plugin/) allows to debug single tests from the cli with `mvn invoker:run -Dinvoker.test=<test-name> -Dinvoker.mavenExecutable=mvnDebug`.

For example, in order to debug the test [java-compiler-executable](src/it/java-compiler-executable), run `mvn invoker:run -Dinvoker.test=java-compiler-executable -Dinvoker.mavenExecutable=mvnDebug` and then attach the project to the debug process: IntelliJ Toolbar -> Run -> Attach to Process.

*Note* that you have to run `mvn invoker:install` to debug the latest changes in your code!

#### Integration Tests
The Integration tests are located in `its`.

* Configure Java 17
* Execute: `./cirrus/cirrus-qa.sh`
* Or, to use a specific version of SonarQube and Maven, you can also specify:
```bash
SQ_VERSION="LATEST_RELEASE[9.9]" MAVEN_VERSION="3.8.8" ./cirrus/cirrus-qa.sh
```

### Change the maven scanner version
Use `mvn versions:set -DgenerateBackupPoms=false -DnewVersion=X.Y.Z-SNAPSHOT` to change te project version.

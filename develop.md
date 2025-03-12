Development notes
=================

Notes for developers of this Maven plugin.

Working on this plugin
----------------------

Run a scan using your local build:

    # install plugin in local Maven repo
    `mvn install`

    # run scan in some project
    `mvn org.sonarsource.scanner.maven:sonar-maven-plugin:3.5-SNAPSHOT:sonar`

### Testing

There are 3 type of tests:

#### Unit Tests
The Unit Tests are located in src/test/java, and they can be run with `mvn test`

#### Invoker Tests
The Invoker tests are located in `sonar-maven-plugin/src/it`, and they can be run with `mvn verify`

The [maven-invoker-plugin](https://maven.apache.org/plugins/maven-invoker-plugin/) allows to debug single tests from the cli with `mvn invoker:run -Dinvoker.test=<test-name> -Dinvoker.mavenExecutable=mvnDebug`.

For example, in order to debug the test [java-compiler-executable](src/it/java-compiler-executable), run `mvn invoker:run -Dinvoker.test=java-compiler-executable -Dinvoker.mavenExecutable=mvnDebug` and then attach the project to the debug process: IntelliJ Toolbar -> Run -> Attach to Process.

*Note* that you have to run `mvn invoker:install` to debug the latest changes in your code!

#### Integration Tests
The Integration tests are located in `its`.
Integration tests can be run with `mvn verify -Pits`.

### Change the maven scanner version
Use `mvn versions:set -DgenerateBackupPoms=false -DnewVersion=X.Y.Z-SNAPSHOT` to change te project version.

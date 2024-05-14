# Sonar Scanner Maven Plugin Integration Tests

This module contains integration tests implemented via the [Apache Maven Invoker Plugin](https://maven.apache.org/plugins/maven-invoker-plugin/).

The **Apache Maven Invoker Plugin** is configured in the [pom.xml](../../pom.xml) file to run the `Groovy` tests in the current directory.

## Configuration

The **Apache Maven Invoker Plugin** is further configured via the [Invoker Properties](https://maven.apache.org/plugins/maven-invoker-plugin/examples/invoker-properties.html), 
i.e. [invoker.properties](invoker.properties), to run the `Sonar Scanner Maven Plugin` with the `sonar.scanner.dumpToFile` property set to _out.properties_.
This way the output of the `Sonar Scanner Maven Plugin` is written to a file named *out.properties* and can be used in the integration tests.

### Debugging the Integration Tests

The **Apache Maven Invoker Plugin** copies every directory from the `src/it` directory to a`target/it` and runs the tests from there.
For each test, the _out.properties_ file is available in the `target/it/<test-name>` directory.

## Usage

The integration tests are automatically run when the `mvn clean install` command is executed from the root of the project.

### Run only the Maven Plugin Integration Tests

```bash
cd ../..
mvn integration-test
```

### Run only a specific Maven Plugin Integration Test

For example running the Integration Test `java-multi-module`

```bash
cd ../..
mvn invoker:run -Dinvoker.test="java-multi-module"
```

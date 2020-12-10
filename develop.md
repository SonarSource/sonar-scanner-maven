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

    
There are 3 type of tests:

* Unit tests (in src/test/java) that you can run with `mvn test`
* Invoker tests (in src/it) that you can run with `mvn verify`
* Integration tests (in its) that you have to run as a separate Maven project (`cd its && mvn verify`)


#!/bin/bash
set -euo pipefail
echo "Running with SQ=$SQ_VERSION for $CI_BUILD_NUMBER"
  # Need install because mvn org.sonarsource.scanner.maven:sonarqube-maven-plugin:<version>:sonar in ITs will take artifact from local repo
  mvn install -B -e -V -Dsource.skip=true -Denforcer.skip=true -Danimal.sniffer.skip=true -Dmaven.test.skip=true

  #deploy the version built by travis
  CURRENT_VERSION=`mvn help:evaluate -Dexpression="project.version" | grep -v '^\[\|Download\w\+\:'`
  RELEASE_VERSION=`echo $CURRENT_VERSION | sed "s/-.*//g"`
  NEW_VERSION="$RELEASE_VERSION-build$CI_BUILD_NUMBER"
  echo $NEW_VERSION
  mkdir -p target
  cd target
  curl --user $ARTIFACTORY_QA_READER_USERNAME:$ARTIFACTORY_QA_READER_PASSWORD -sSLO https://repox.sonarsource.com/sonarsource-public-qa/org/sonarsource/scanner/maven/sonar-maven-plugin/$NEW_VERSION/sonar-maven-plugin-$NEW_VERSION.jar
  cd ..
 
  mvn install:install-file -Dfile=target/sonar-maven-plugin-$NEW_VERSION.jar



  maven_home_for_its=${HOME}/maven-for-its
  mkdir -p $maven_home_for_its
  curl -sSL http://repo1.maven.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.tar.gz | tar zx --strip-components 1 -C $maven_home_for_its

  cd its 
  mvn -Dsonar.runtimeVersion="$SQ_VERSION" -Dmaven.test.redirectTestOutputToFile=false -Dmaven.home=$maven_home_for_its verify




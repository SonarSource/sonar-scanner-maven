#!/bin/bash -v

set -euo pipefail

function installTravisTools {
  mkdir -p ~/.local
  curl -sSL https://github.com/SonarSource/travis-utils/tarball/v21 | tar zx --strip-components 1 -C ~/.local
  source ~/.local/bin/install
}

installTravisTools

if [ -n "${SQ_VERSION:-}" ] && [ "${SQ_VERSION}" == "DEV" ]
then
  build_snapshot "SonarSource/sonarqube"
fi

# Need install because mvn org.codehaus.mojo:sonar-maven-plugin:<version>:sonar in ITs will take artifact from local repo
mvn install -B -e -V

 
if [ "${RUN_ITS}" == "true" ]                                                                                                                                    
then                                                                                                                                                             

  maven_home_for_its=${HOME}/maven-for-its
  mkdir -p $maven_home_for_its
  curl -sSL http://repo1.maven.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.tar.gz | tar zx --strip-components 1 -C $maven_home_for_its

  cd its  
  mvn -Dsonar.runtimeVersion=$SQ_VERSION -Dmaven.test.redirectTestOutputToFile=false -Dmaven.home=$maven_home_for_its verify
fi

#!/bin/bash

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
  cd its  
  mvn -Dsonar.runtimeVersion=$SQ_VERSION -Dmaven.test.redirectTestOutputToFile=false verify
fi

#!/bin/bash

set -euo pipefail

function installTravisTools {
  mkdir ~/.local
  curl -sSL https://github.com/SonarSource/travis-utils/tarball/v16 | tar zx --strip-components 1 -C ~/.local
  source ~/.local/bin/install
}

mvn verify -B -e -V
                                                                                                                                                                 
if [ "${RUN_ITS}" == "true" ]                                                                                                                                    
then                                                                                                                                                             
  installTravisTools                                                                                                                                             
                                                                                                                                                                 
  if [ "${SQ_VERSION}" == "DEV" ]
  then
    build_snapshot "SonarSource/sonarqube"
  fi

  cd its  
  mvn -Dsonar.runtimeVersion=$SQ_VERSION -Dmaven.test.redirectTestOutputToFile=false verify
fi

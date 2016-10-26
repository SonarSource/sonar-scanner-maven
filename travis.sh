#!/bin/bash

#set -euo pipefail

function configureTravis {
  mkdir -p ~/.local
  curl -sSL https://github.com/SonarSource/travis-utils/tarball/v33 | tar zx --strip-components 1 -C ~/.local
  source ~/.local/bin/install
}
configureTravis

function strongEcho {
  echo ""
  echo "================ $1 ================="
}

#build_snapshot "SonarSource/sonar-scanner-api"

case "$TARGET" in

CI)
  regular_mvn_build_deploy_analyze
  ;;

*)
  echo "Unexpected TEST mode: $TARGET"
  exit 1
  ;;

esac

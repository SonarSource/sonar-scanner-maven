#!/usr/bin/env bash
set -euo pipefail
(
  REPOSITORY_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && cd .. && pwd )
  cd "${REPOSITORY_DIR}"

  if [[ "${CIRRUS_CI:-false}" == "true" ]]; then
    # Set up environment variables.
    source cirrus-env QA
    source set_maven_build_version "${BUILD_NUMBER}"
  else
    export SQ_VERSION="${SQ_VERSION:-LATEST_RELEASE}"
    export MAVEN_VERSION="${MAVEN_VERSION:-3.9.4}"
    mvn --batch-mode --errors clean
    mvn --batch-mode --errors \
        --file 'sonar-maven-plugin/pom.xml' \
        -DskipTests=true -Dinvoker.skip=true \
        clean install
  fi

  # Install a specific version of Maven. We want to test against multiple versions.
  MAVEN_HOME_IT="${REPOSITORY_DIR}/target/downloaded-maven-${MAVEN_VERSION}"
  mkdir -p "${MAVEN_HOME_IT}"

  MAVEN_BINARY_URL="https://repo1.maven.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
  echo "Extracting apache-maven-${MAVEN_VERSION}-bin.tar.gz into ${MAVEN_HOME_IT}"
  curl -sSL "${MAVEN_BINARY_URL}" | tar zx --strip-components 1 -C "${MAVEN_HOME_IT}"
  cp -f "${HOME}/.m2/settings.xml" "${MAVEN_HOME_IT}/conf/"

  mvn --batch-mode --errors \
    --projects '!sonar-maven-plugin' \
    --activate-profiles its \
    -Dsonar.runtimeVersion="${SQ_VERSION}" \
    -Dmaven.home="${MAVEN_HOME_IT}" \
    -Dmaven.test.redirectTestOutputToFile=false \
    verify
)

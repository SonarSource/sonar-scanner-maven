#!/bin/bash

# Set up environment variables.
source cirrus-env QA
source set_maven_build_version $BUILD_NUMBER

# Install a specific version of Maven. We want to test against multiple versions.
MAVEN_HOME_IT=`pwd`/maven_it
mkdir -p $MAVEN_HOME_IT
curl -sSL https://repo1.maven.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.tar.gz | tar zx --strip-components 1 -C $MAVEN_HOME_IT
cp -f ~/.m2/settings.xml $MAVEN_HOME_IT/conf/

# Run ITs.
cd its
mvn -B -e -Dsonar.runtimeVersion=$SQ_VERSION -Dmaven.test.redirectTestOutputToFile=false -Dmaven.home=$MAVEN_HOME_IT verify

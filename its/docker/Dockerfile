#------------------------------------------------------------------------------
# Prepares a folder for storing Maven builds, which is needed to run the ITs
# during the QA task.
#
# Build from the basedir:
#   docker build -f its/docker/Dockerfile -t sonar-scanner-maven-qa its/docker
#
# Verify the content of the image by running a shell session in it:
#   docker run -it sonar-scanner-maven-qa bash
#
# CirrusCI builds the image when needed. No need to manually upload it to
# Google Cloud Container Registry. See section "gke_container" of .cirrus.yml
#------------------------------------------------------------------------------

FROM gcr.io/ci-cd-215716/base:latest

ENV MAVEN_HOME_IT=/opt/maven_it

USER root

RUN mkdir -p $MAVEN_HOME_IT && chmod a+w $MAVEN_HOME_IT

USER sonarsource

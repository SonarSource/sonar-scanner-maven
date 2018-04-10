
pipeline {
  agent {
    label 'linux'
  }
  environment {
    MAVEN_TOOL = 'Maven 3.5.2'
  }
  stages {
    stage('log'){
      steps{
        echo "PR: ${env.CHANGE_ID}"
        echo "BRANCH: ${env.BRANCH_NAME}"
      }
    }
    stage('Build master') {
      agent {
        label 'linux'
      }         
      when {
        branch 'master'
      }
      steps {
        withSonarQubeEnv('next') {
          withMaven(maven: MAVEN_TOOL) {
            sh "mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent package sonar:sonar"
            
          }
        }
      }
    }
    stage('Build maintenance branch') {
      when {
        branch 'branch-*'
      }
      steps {
        withSonarQubeEnv('next') {
          withMaven(maven: MAVEN_TOOL) {
            sh "mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent package sonar:sonar -Dsonar.branch.name=${env.BRANCH_NAME} -Dsonar.branch.target=master"
            
          }
        }
      }
    }
    stage('Build PR'){
      when {
        branch 'PR-*'
      }
      steps {
        withSonarQubeEnv('next') {
          withMaven(maven: MAVEN_TOOL) {
            sh "mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent package sonar:sonar \
            -Dsonar.pullrequest.branch=${env.BRANCH_NAME} \
            -Dsonar.pullrequest.base='master' \
            -Dsonar.pullrequest.key=${env.CHANGE_ID} \
            -Dsonar.pullrequest.provider=github \
            -Dsonar.pullrequest.github.repository='tomverin/sonar-scanner-maven'"            
          }
        }
      }
    }
  }
}


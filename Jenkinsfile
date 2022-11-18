pipeline {
    agent {
        node {
            label 'metersphere'
        }
    }
    environment {
        JAVA_HOME = '/opt/jdk-11'
    }
    options { quietPeriod(600) }
    stages {
        stage('Build/Test') {
            steps {
                configFileProvider([configFile(fileId: 'metersphere-maven', targetLocation: 'settings.xml')]) {
                    sh "mvn clean install -Dgpg.skip -DskipTests --settings ./settings.xml"
                }
            }
        }
    }
}

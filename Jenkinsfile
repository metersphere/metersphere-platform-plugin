pipeline {
    agent {
        node {
            label 'metersphere'
        }
    }
    environment {
        JAVA_HOME = '/opt/jdk-21'
    }
    triggers {
        pollSCM('0 * * * *')
    }
    stages {
        stage('Build/Test') {
            steps {
                configFileProvider([configFile(fileId: 'metersphere-maven', targetLocation: 'settings.xml')]) {
                    sh '''
                        export JAVA_HOME=/opt/jdk-21
                        export CLASSPATH=$JAVA_HOME/lib:$CLASSPATH
                        export PATH=$JAVA_HOME/bin:$PATH
                        java -version
                        mvn clean install -Dgpg.skip -DskipTests --settings ./settings.xml
                    '''
                }
            }
        }
    }
}

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
        stage('Preparation') {
            steps {
                script {
                    REVISION = ""
                    if (env.BRANCH_NAME.startsWith("v") ) {
                        REVISION = env.BRANCH_NAME.substring(1)
                    } else {
                        REVISION = env.BRANCH_NAME
                    }
                    env.REVISION = "${REVISION}"
                    echo "REVISION=${REVISION}"
                }
            }
        }
        stage('Build/Test') {
            steps {
                configFileProvider([configFile(fileId: 'metersphere-maven', targetLocation: 'settings.xml')]) {
                    sh '''
                        export JAVA_HOME=/opt/jdk-21
                        export CLASSPATH=$JAVA_HOME/lib:$CLASSPATH
                        export PATH=$JAVA_HOME/bin:$PATH
                        java -version
                        mvn clean install -Drevision=${REVISION} -Dgpg.skip -DskipTests --settings ./settings.xml
                    '''
                }
            }
        }
    }
}

pipeline {
    agent any
    tools {
        jdk 'JDK-21'
    }
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        stage('Build & Test') {
            steps {
                // Configures retry count and timeout flags for artifact downloads
                sh '''
                    ./mvnw -B clean verify \
                      -Dmaven.wagon.http.retryHandler.count=5 \
                      -Dmaven.wagon.http.retryHandler.requestSentEnabled=true \
                      -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 \
                      -Daether.connector.http.connectionTimeout=60000 \
                      -Daether.connector.http.requestTimeout=180000 \
                      -Dhttp.keepAlive=false \
                      -Dmaven.wagon.http.pool=false
                '''
            }
        }
        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarServer') {
                    sh './mvnw -B sonar:sonar -Dsonar.host.url=http://sonarqube:9000'
                }
            }
        }
        stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }
    }
    post {
        success {
            build job: 'swiftkart-deploy-ec2',
                wait: false,
                parameters: [
                    string(
                        name: 'BUILD_TAG_REF',
                        value: "${env.BUILD_NUMBER}"
                    )
                ]
        }

        failure {
            echo 'CI failed. Deployment will not start.'
        }
    }
}
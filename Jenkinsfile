pipeline {

    agent any

    parameters {
        string(
            name: 'BUILD_TAG_REF',
            defaultValue: 'latest',
            description: 'Docker image tag to deploy'
        )
    }

    environment {

        // Non-secret configuration
        AWS_REGION = 'ap-south-1'
        AWS_ACCOUNT_ID = '683613034915'

        ECR_REPO = 'swiftkart-ai-assistant'

        ECR_REGISTRY =
            "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

        EC2_HOST = '13.235.90.243'

        APP_NAME = 'swiftkart-app'

        SECRET_NAME = 'swiftkart/prod/env'

        IMAGE_TAG = "${params.BUILD_TAG_REF}"
    }

    stages {

        /*
         * The CD job needs the source code because it builds the Docker image.
         *
         * Configure this Jenkins job with your Git repository under:
         *
         * Pipeline → Definition → Pipeline script from SCM
         *
         * and configure GitHub credentials there using:
         * github-credentials
         */
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build Docker Image') {
            steps {

                sh '''
                    set -e

                    echo "Building Docker image..."

                    docker build \
                        -t "${ECR_REGISTRY}/${ECR_REPO}:${IMAGE_TAG}" \
                        .

                    echo "Docker image built successfully."
                '''
            }
        }

        stage('Push Image to ECR') {
            steps {

                /*
                 * AWS credentials are stored in Jenkins.
                 *
                 * Jenkins temporarily exposes them as:
                 *
                 * AWS_ACCESS_KEY_ID
                 * AWS_SECRET_ACCESS_KEY
                 *
                 * Actual values are NEVER written in this Jenkinsfile.
                 */
                withCredentials([
                    [$class: 'AmazonWebServicesCredentialsBinding',
                     credentialsId: 'aws-jenkins']
                ]) {

                    sh '''
                        set -e

                        echo "Logging into Amazon ECR..."

                        aws ecr get-login-password \
                            --region "${AWS_REGION}" \
                        | docker login \
                            --username AWS \
                            --password-stdin "${ECR_REGISTRY}"

                        echo "Pushing Docker image to ECR..."

                        docker push \
                            "${ECR_REGISTRY}/${ECR_REPO}:${IMAGE_TAG}"

                        echo "Image pushed successfully."

                        echo "Image:"
                        echo "${ECR_REGISTRY}/${ECR_REPO}:${IMAGE_TAG}"
                    '''
                }
            }
        }

        stage('Deploy to EC2') {
            steps {

                /*
                 * EC2 SSH private key is stored in Jenkins.
                 *
                 * Jenkins temporarily creates the key file represented
                 * by ${SSH_KEY}.
                 */
                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: 'ec2-ssh-key',
                        keyFileVariable: 'SSH_KEY',
                        usernameVariable: 'SSH_USER'
                    )
                ]) {

                    sh '''
                        set -e

                        echo "Deploying to EC2: ${EC2_HOST}"

                        ssh \
                            -o StrictHostKeyChecking=no \
                            -i "${SSH_KEY}" \
                            "${SSH_USER}@${EC2_HOST}" \
                            "
                                set -e

                                AWS_REGION='${AWS_REGION}'
                                ECR_REGISTRY='${ECR_REGISTRY}'
                                ECR_REPO='${ECR_REPO}'
                                IMAGE_TAG='${IMAGE_TAG}'
                                SECRET_NAME='${SECRET_NAME}'
                                APP_NAME='${APP_NAME}'

                                echo 'Logging into ECR...'

                                aws ecr get-login-password \
                                    --region \${AWS_REGION} \
                                | docker login \
                                    --username AWS \
                                    --password-stdin \${ECR_REGISTRY}

                                echo 'Pulling Docker image...'

                                docker pull \
                                    \${ECR_REGISTRY}/\${ECR_REPO}:\${IMAGE_TAG}

                                echo 'Reading application secrets from AWS Secrets Manager...'

                                SECRET_JSON=\$(aws secretsmanager get-secret-value \
                                    --secret-id \${SECRET_NAME} \
                                    --region \${AWS_REGION} \
                                    --query SecretString \
                                    --output text)

                                OPENAI_KEY=\$(echo \"\$SECRET_JSON\" | jq -r '.OPENAI_API_KEY')
                                TAVILY_KEY=\$(echo \"\$SECRET_JSON\" | jq -r '.TAVILY_API_KEY')

                                if [ -z \"\$OPENAI_KEY\" ] || [ \"\$OPENAI_KEY\" = \"null\" ]; then
                                    echo 'ERROR: OPENAI_API_KEY not found in Secrets Manager.'
                                    exit 1
                                fi

                                if [ -z \"\$TAVILY_KEY\" ] || [ \"\$TAVILY_KEY\" = \"null\" ]; then
                                    echo 'WARNING: TAVILY_API_KEY not found in Secrets Manager.'
                                fi

                                echo 'Application secrets retrieved successfully.'

                                echo 'Stopping old container...'

                                docker stop \${APP_NAME} || true

                                echo 'Removing old container...'

                                docker rm \${APP_NAME} || true

                                echo 'Starting new container...'

                                docker run -d \
                                    --name \${APP_NAME} \
                                    --restart unless-stopped \
                                    -p 8080:8080 \
                                    -e OPENAI_API_KEY=\"\${OPENAI_KEY}\" \
                                    -e TAVILY_API_KEY=\"\${TAVILY_KEY}\" \
                                    \${ECR_REGISTRY}/\${ECR_REPO}:\${IMAGE_TAG}

                                echo 'Container started.'

                                echo 'Running container status:'

                                docker ps \
                                    --filter name=\${APP_NAME}

                                echo 'Deployment completed successfully.'
                            "
                    '''
                }
            }
        }

        stage('Health Check') {
            steps {

                sh '''
                    set -e

                    echo "Waiting for application to start..."

                    sleep 15

                    echo "Checking application health..."

                    curl --fail \
                        "http://${EC2_HOST}:8080/actuator/health"

                    echo ""
                    echo "Health check passed."
                '''
            }
        }
    }

    post {

        success {
            echo 'SwiftKart deployment completed successfully.'
        }

        failure {
            echo 'SwiftKart deployment failed.'
        }
    }
}
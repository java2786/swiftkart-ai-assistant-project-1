# SwiftKart AI Assistant

# AWS EC2 Docker Deployment with Jenkins CI/CD

## 1. Objective

This guide explains how to deploy the existing monolithic SwiftKart AI Assistant application as a Docker container on an AWS EC2 instance.

The application currently contains multiple environment files such as:

```text
.env
.env.dev
.env.openai.dev
```

The objective is to:

1. Keep development secrets on the developer machine.
2. Never commit production secrets to Git.
3. Build and test the application using Jenkins.
4. Run SonarQube analysis and Quality Gate validation.
5. Trigger a separate deployment Jenkins job only when CI succeeds.
6. Build the Docker image.
7. Push the image to Amazon ECR.
8. SSH into the EC2 server.
9. Pull the image from ECR.
10. Start the application container.
11. Supply production secrets to the container without putting them inside the Docker image.
12. Perform a health check after deployment.
13. Make rollback to an earlier Docker image possible.

The existing architecture already follows the recommended CI/CD separation:

```text
swiftkart-pipeline
        |
        | SUCCESS
        v
swiftkart-deploy-ec2
```

The existing documentation describes `swiftkart-pipeline` as the CI job and `swiftkart-deploy-ec2` as the CD job.

---

# 2. Final Architecture

The recommended architecture is:

```text
                         +------------------+
                         |    Developer     |
                         +--------+---------+
                                  |
                                  | git push
                                  v
                         +------------------+
                         |     GitHub       |
                         +--------+---------+
                                  |
                                  | webhook
                                  v
                    +---------------------------+
                    |          Jenkins          |
                    |                           |
                    |  swiftkart-pipeline       |
                    |                           |
                    |  Checkout                 |
                    |  Maven Build              |
                    |  Tests                    |
                    |  JaCoCo                   |
                    |  SonarQube                |
                    |  Quality Gate              |
                    +-------------+-------------+
                                  |
                                  | SUCCESS
                                  v
                    +---------------------------+
                    | swiftkart-deploy-ec2      |
                    |                           |
                    | Docker Build              |
                    | Docker Push               |
                    | SSH Deployment             |
                    +-------------+-------------+
                                  |
                    +-------------+-------------+
                    |                           |
                    v                           v
          +------------------+          +------------------+
          |   Amazon ECR     |          |    AWS EC2      |
          |                  |          |                  |
          | Docker Image     |<---------| Docker Pull      |
          |                  |          |                  |
          +------------------+          | Container        |
                                        |                  |
                                        | Production .env  |
                                        |                  |
                                        +--------+---------+
                                                 |
                                                 v
                                      SwiftKart Application
```

---

# 3. What Goes Where?

Before doing any AWS configuration, understand where each component belongs.

| Component                 | Location                                  |
| ------------------------- | ----------------------------------------- |
| Source code               | GitHub                                    |
| `Jenkinsfile`             | GitHub                                    |
| `Dockerfile`              | GitHub                                    |
| Unit tests                | GitHub                                    |
| `.env`                    | Developer machine only                    |
| `.env.dev`                | Developer machine only                    |
| `.env.openai.dev`         | Developer machine only                    |
| Production secrets        | EC2, preferably AWS Secrets Manager/SSM   |
| Docker image              | Amazon ECR                                |
| Running container         | EC2                                       |
| Jenkins CI                | Jenkins                                   |
| Jenkins deployment job    | Jenkins                                   |
| SonarQube                 | Existing Jenkins environment              |
| OpenAI production API key | Production secret store / EC2 environment |

The most important rule is:

```text
.env files containing secrets
        |
        X
        |
      GitHub
```

Do not put production secrets inside:

```text
Dockerfile
docker-compose.yml
Jenkinsfile
GitHub repository
Docker image
```

---

# 4. Understand the Three Environment Files

You currently have:

```text
.env
.env.dev
.env.openai.dev
```

These should be treated as configuration sources for development.

For example:

```text
.env
    common configuration

.env.dev
    development-specific configuration

.env.openai.dev
    development OpenAI configuration
```

The exact variables depend on your application.

For example, you might have:

```text
OPENAI_API_KEY=...
DATABASE_URL=...
DATABASE_USERNAME=...
DATABASE_PASSWORD=...
```

The actual variable names in your project must be used.

Do not blindly copy the development `.env` files to EC2.

Production should have its own configuration:

```text
Production
    |
    +-- OPENAI_API_KEY
    +-- DATABASE_URL
    +-- DATABASE_USERNAME
    +-- DATABASE_PASSWORD
    +-- other production configuration
```

---

# 5. Recommended Production Secret Strategy

For the first deployment, the simplest approach is:

```text
EC2
 |
 +-- ~/swiftkart-deploy/
       |
       +-- .env.production
```

The file is created directly on EC2 and is never committed to Git.

Example:

```bash
mkdir -p ~/swiftkart-deploy

nano ~/swiftkart-deploy/.env.production
```

Example content:

```env
SPRING_PROFILES_ACTIVE=prod

SPRING_AI_OPENAI_API_KEY=your-production-openai-key

DATABASE_URL=your-production-database-url
DATABASE_USERNAME=your-production-database-user
DATABASE_PASSWORD=your-production-database-password
```

Use your application's actual property names.

Then protect the file:

```bash
chmod 600 ~/swiftkart-deploy/.env.production
```

This is a simple approach suitable for learning, demos, and a small deployment.

For a more production-grade architecture, move these secrets to:

```text
AWS Secrets Manager
```

or:

```text
AWS Systems Manager Parameter Store
```

and have the deployment/application retrieve them.

For your first EC2 deployment, I recommend starting with the EC2 `.env.production` approach and later upgrading to Secrets Manager.

---

# 6. Step 1 - Prepare the Git Repository

Your repository should look approximately like:

```text
swiftkart-ai-assistant/
|
+-- src/
|
+-- .mvn/
|
+-- mvnw
+-- mvnw.cmd
+-- pom.xml
|
+-- Dockerfile
+-- .dockerignore
+-- Jenkinsfile
|
+-- .gitignore
```

Do NOT have these files committed:

```text
.env
.env.dev
.env.openai.dev
.env.production
```

Update `.gitignore`:

```gitignore
.env
.env.*
!.env.example

.env.production

*.pem

target/
```

You may keep an example configuration:

```text
.env.example
```

but it must contain fake/example values only.

For example:

```env
SPRING_AI_OPENAI_API_KEY=
DATABASE_URL=
DATABASE_USERNAME=
DATABASE_PASSWORD=
```

---

# 7. Step 2 - Create a Dockerfile

Create:

```text
Dockerfile
```

in the project root.

Use a multi-stage build:

```dockerfile
# ==============================
# Stage 1 - Build
# ==============================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ==============================
# Stage 2 - Runtime
# ==============================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/swiftkart

# Create group, user, data folder, and assign ownership
RUN addgroup -S swiftgroup \
    && adduser -S swiftuser -G swiftgroup \
    && mkdir -p /opt/swiftkart/data \
    && chown -R swiftuser:swiftgroup /opt/swiftkart

COPY --from=builder --chown=swiftuser:swiftgroup /app/target/*.jar app.jar

USER swiftuser
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
```

Your existing deployment document already uses a multi-stage Java 21 Docker build and runs the application as a non-root user.

---

# 8. Step 3 - Create `.dockerignore`

Create:

```text
.dockerignore
```

with:

```dockerignore
.git
.gitignore

.env
.env.*
!.env.example

target/

.idea/
.vscode/

*.log

README.md
```

The important part is:

```dockerignore
.env
.env.*
```

This prevents environment files from accidentally entering the Docker build context.

---

# 9. Step 4 - Test Docker Locally

Before touching AWS, make sure the Docker image works.

Build:

```bash
docker build -t swiftkart-ai-assistant:test .
```

Check:

```bash
docker images
```

Run:

```bash
docker run -d \
  --name swiftkart-ai-assistant-test \
  -p 8080:8080 \
  --env-file .env \
  swiftkart-ai-assistant:test

```

Check:

```bash
docker ps
```

Check logs:

```bash
docker logs swiftkart-ai-assistant-test
```

Test:

```bash
curl http://localhost:8080/actuator/health
```

If your application does not expose Actuator, use your application's actual health endpoint.

Stop it:

```bash
docker stop swiftkart-ai-assistant-test
```

Remove it:

```bash
docker rm swiftkart-ai-assistant-test
```

Do not proceed to AWS until this works.

---

# 10. Step 5 - Create Amazon ECR Repository

Open the AWS Console.

Go to:

```text
AWS Console
    |
    +-- Elastic Container Registry
        |
        +-- Repositories
            |
            +-- Create repository
```

Use:

```text
Repository name:
swiftkart-ai-assistant
```

Use:

```text
Private repository
```

Choose your AWS region.

For an India-based deployment, you can use:

```text
ap-south-1
```

if Mumbai is your desired AWS region.

Your ECR URI will look like:

```text
AWS_ACCOUNT_ID.dkr.ecr.ap-south-1.amazonaws.com/swiftkart-ai-assistant
```

For example:

```text
123456789012.dkr.ecr.ap-south-1.amazonaws.com/swiftkart-ai-assistant
```

Do not copy this example account ID.

Use your actual AWS account ID.

---

# 11. Step 6 - Launch EC2

Go to:

```text
AWS Console
    |
    +-- EC2
        |
        +-- Launch instance
```

Recommended initial configuration:

```text
Name:
swiftkart-production-server
```

OS:

```text
Ubuntu Server 24.04 LTS
```

Instance type:

```text
t3.small
```

A smaller instance can be used for experimentation, but Java + Docker + monitoring/database components can make 1 GB RAM restrictive.

The existing architecture documentation similarly recommends `t3.small` as the more stable option and notes the memory limitation of micro instances.

---

# 12. Step 7 - Create EC2 Key Pair

Create:

```text
swiftkart-ec2-key
```

Use:

```text
RSA
```

and:

```text
.pem
```

Download:

```text
swiftkart-ec2-key.pem
```

Store it securely.

On your local machine:

```bash
chmod 400 swiftkart-ec2-key.pem
```

---

# 13. Step 8 - Configure EC2 Security Group

Configure inbound rules.

For initial testing:

| Type       | Port | Source                     |
| ---------- | ---: | -------------------------- |
| SSH        |   22 | Your IP                    |
| Custom TCP | 8080 | Your IP / required clients |

Avoid:

```text
SSH 22 -> 0.0.0.0/0
```

unless there is a specific reason.

The existing document allows SSH from the entire Internet for convenience, but restricting SSH to the Jenkins server's network/IP or using another secure access mechanism is preferable.

For the application:

```text
8080
```

can initially be opened for testing.

Later, a better architecture is:

```text
Internet
   |
   v
Nginx / Load Balancer
   |
   v
EC2 :8080
```

and port `8080` does not need to be publicly exposed.

---

# 14. Step 9 - Attach an IAM Role to EC2

This is an important improvement over the original deployment approach.

Do not store AWS access keys on EC2 just so Docker can pull from ECR.

Create an IAM role:

```text
swiftkart-ec2-ecr-role
```

Attach an ECR read-only policy appropriate for pulling images.

The role should allow EC2 to:

```text
Authenticate to ECR
Pull Docker images from ECR
```

Conceptually:

```text
EC2
 |
 | IAM Role
 v
Amazon ECR
 |
 | docker pull
 v
Docker
```

This means you do not need:

```text
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
```

inside the EC2 deployment script.

---

# 15. Step 10 - Connect to EC2

From your local machine:

```bash
ssh -i swiftkart-ec2-key.pem ubuntu@YOUR_EC2_PUBLIC_IP
```

Example:

```bash
ssh -i swiftkart-ec2-key.pem ubuntu@3.110.120.45
```

---

# 16. Step 11 - Install Docker on EC2

On EC2:

```bash
sudo apt-get update

sudo apt-get install -y \
    ca-certificates \
    curl \
    gnupg \
    lsb-release
```

Add Docker repository:

```bash
sudo install -m 0755 -d /etc/apt/keyrings

curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | sudo gpg --dearmor \
  -o /etc/apt/keyrings/docker.gpg

sudo chmod a+r /etc/apt/keyrings/docker.gpg
```

Add repository:

```bash
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
```

Install:

```bash
sudo apt-get update

sudo apt-get install -y \
    docker-ce \
    docker-ce-cli \
    containerd.io \
    docker-buildx-plugin \
    docker-compose-plugin
```

Add Ubuntu user to Docker group:

```bash
sudo usermod -aG docker ubuntu
```

Exit:

```bash
exit
```

Reconnect:

```bash
ssh -i swiftkart-ec2-key.pem ubuntu@YOUR_EC2_PUBLIC_IP
```

Verify:

```bash
docker --version
```

and:

```bash
docker compose version
```

The original deployment guide follows essentially this EC2 Docker installation process.

---

# 17. Step 12 - Install AWS CLI on EC2

Install AWS CLI if required for ECR authentication:

```bash
sudo apt-get update

sudo apt-get install -y unzip curl

curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" \
  -o "awscliv2.zip"

unzip awscliv2.zip

sudo ./aws/install

rm -rf aws awscliv2.zip
```

Verify:

```bash
aws --version
```

Verify that EC2 can access AWS using its IAM role:

```bash
aws sts get-caller-identity
```

You should see the IAM role identity.

Do not configure:

```bash
aws configure
```

with permanent AWS access keys on the EC2 server if you are using the EC2 IAM role.

---

# 18. Step 13 - Create Production Deployment Directory

On EC2:

```bash
mkdir -p ~/swiftkart-deploy
```

Create the production environment file:

```bash
nano ~/swiftkart-deploy/.env.production
```

Example:

```env
SPRING_PROFILES_ACTIVE=prod

SPRING_AI_OPENAI_API_KEY=your-production-key

DATABASE_URL=your-production-database-url
DATABASE_USERNAME=your-production-username
DATABASE_PASSWORD=your-production-password
```

Use your real application's environment variable names.

Secure it:

```bash
chmod 600 ~/swiftkart-deploy/.env.production
```

Verify:

```bash
ls -la ~/swiftkart-deploy
```

You should have:

```text
.env.production
```

but this file must never be committed to Git.

---

# 19. Step 14 - Test ECR Access from EC2

On EC2:

```bash
aws sts get-caller-identity
```

Then:

```bash
aws ecr get-login-password --region ap-south-1 \
  | docker login \
      --username AWS \
      --password-stdin YOUR_AWS_ACCOUNT_ID.dkr.ecr.ap-south-1.amazonaws.com
```

You should receive:

```text
Login Succeeded
```

This confirms:

```text
EC2
 |
 | IAM Role
 v
AWS
 |
 v
ECR
```

is working.

---

# 20. Step 15 - Jenkins Credentials

Jenkins still needs credentials to perform deployment.

You need:

```text
1. GitHub credentials
2. EC2 SSH private key
3. AWS credentials for pushing images to ECR
```

However, AWS credentials should ideally be associated with the Jenkins machine/agent through an IAM role when Jenkins itself is running on AWS.

For your current setup, if Jenkins is running locally or in Docker outside AWS, you can temporarily use Jenkins credentials for ECR push.

Create:

```text
aws-access-key-id
```

and:

```text
aws-secret-access-key
```

in Jenkins Credentials.

The original documentation already stores these in Jenkins credentials.

Also create:

```text
ec2-ssh-key
```

as:

```text
Kind:
SSH Username with private key
```

Username:

```text
ubuntu
```

Private key:

```text
contents of swiftkart-ec2-key.pem
```

The original guide uses the same Jenkins SSH credential concept.

---

# 21. Jenkins Credentials Summary

Your Jenkins credentials should look approximately like:

| Jenkins Credential ID | Type                          | Purpose         |
| --------------------- | ----------------------------- | --------------- |
| github-credentials    | Username/password or token    | GitHub checkout |
| aws-access-key-id     | Secret text                   | ECR push        |
| aws-secret-access-key | Secret text                   | ECR push        |
| ec2-ssh-key           | SSH Username with private key | EC2 SSH         |

You do NOT need:

```text
openai-api-key
```

inside Jenkins if the production OpenAI key is maintained on EC2.

This is one of the biggest improvements over the original deployment script.

---

# 22. Step 16 - Existing CI Job

Your existing Jenkins job is:

```text
swiftkart-pipeline
```

It should perform:

```text
Checkout
   |
Maven Build
   |
Unit Tests
   |
JaCoCo
   |
SonarQube
   |
Quality Gate
```

Only when all of this succeeds should deployment happen.

Conceptually:

```text
Quality Gate FAILED
       |
       X
       |
   NO DEPLOYMENT


Quality Gate PASSED
       |
       v
   DEPLOYMENT
```

---

# 23. Step 17 - Trigger the Deployment Job

Create another Jenkins Pipeline job:

```text
swiftkart-deploy-ec2
```

The desired relationship is:

```text
swiftkart-pipeline
        |
        | SUCCESS
        v
swiftkart-deploy-ec2
```

There is an important point here.

Do not configure two independent mechanisms that both trigger deployment.

For example, do not simultaneously configure:

```text
CI Jenkinsfile:
build job: 'swiftkart-deploy-ec2'
```

and:

```text
Deployment job:
Build after other projects are built
```

unless you deliberately configure the relationship to avoid duplicate builds.

Use one clear trigger mechanism.

For your current architecture, the simplest approach is:

```text
swiftkart-pipeline
       |
       | post success
       v
swiftkart-deploy-ec2
```

---

# 24. Step 18 - Modify CI Jenkinsfile

At the end of the existing CI pipeline:

```groovy
post {
    success {
        echo 'CI succeeded. Starting deployment.'

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
        echo 'CI failed. Deployment will not run.'
    }
}
```

This matches the two-job architecture already present in your current documentation.

---

# 25. Step 19 - Create Deployment Jenkins Job

Create:

```text
New Item
    |
    +-- swiftkart-deploy-ec2
    |
    +-- Pipeline
```

Configure:

```text
This project is parameterized
```

Add:

```text
String Parameter
```

Name:

```text
BUILD_TAG_REF
```

Default:

```text
latest
```

Description:

```text
CI build number used as Docker image tag.
```

---

# 26. Step 20 - Deployment Pipeline

The deployment pipeline should perform:

```text
Checkout
   |
Docker Build
   |
Docker Tag
   |
ECR Login
   |
Docker Push
   |
SSH -> EC2
   |
Docker Login
   |
Docker Pull
   |
Read production environment
   |
Stop old container
   |
Start new container
   |
Health Check
```

---

# 27. Recommended Deployment Jenkinsfile

Use the following as the starting point for `swiftkart-deploy-ec2`.

```groovy
pipeline {

    agent any

    environment {

        AWS_REGION = 'ap-south-1'

        AWS_ACCOUNT_ID = 'YOUR_AWS_ACCOUNT_ID'

        ECR_REPO_NAME = 'swiftkart-ai-assistant'

        ECR_REGISTRY =
            "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

        EC2_PUBLIC_IP = 'YOUR_EC2_PUBLIC_IP'

        APP_NAME = 'swiftkart-ai-assistant'

        IMAGE_TAG = "${params.BUILD_TAG_REF ?: env.BUILD_NUMBER}"
    }

    stages {

        stage('Checkout') {

            steps {

                git(
                    branch: 'main',
                    credentialsId: 'github-credentials',
                    url: 'YOUR_GITHUB_REPOSITORY_URL'
                )
            }
        }


        stage('Build Docker Image') {

            steps {

                echo "Building Docker image..."

                sh """
                    docker build \
                      -t ${ECR_REGISTRY}/${ECR_REPO_NAME}:${IMAGE_TAG} \
                      .
                """

                sh """
                    docker tag \
                      ${ECR_REGISTRY}/${ECR_REPO_NAME}:${IMAGE_TAG} \
                      ${ECR_REGISTRY}/${ECR_REPO_NAME}:latest
                """
            }
        }


        stage('Push Image to ECR') {

            steps {

                withCredentials([

                    string(
                        credentialsId: 'aws-access-key-id',
                        variable: 'AWS_ACCESS_KEY_ID'
                    ),

                    string(
                        credentialsId: 'aws-secret-access-key',
                        variable: 'AWS_SECRET_ACCESS_KEY'
                    )
                ]) {

                    sh '''
                        set -e

                        aws ecr get-login-password \
                          --region "$AWS_REGION" \
                          | docker login \
                              --username AWS \
                              --password-stdin "$ECR_REGISTRY"

                        docker push \
                          "$ECR_REGISTRY/$ECR_REPO_NAME:$IMAGE_TAG"

                        docker push \
                          "$ECR_REGISTRY/$ECR_REPO_NAME:latest"
                    '''
                }
            }
        }


        stage('Deploy to EC2') {

            steps {

                withCredentials([

                    sshUserPrivateKey(
                        credentialsId: 'ec2-ssh-key',
                        keyFileVariable: 'SSH_KEY_PATH',
                        usernameVariable: 'SSH_USER'
                    )
                ]) {

                    sh '''

                        set -e

                        ssh \
                          -o StrictHostKeyChecking=no \
                          -i "$SSH_KEY_PATH" \
                          "$SSH_USER@$EC2_PUBLIC_IP" \
                          "
                            set -e

                            aws ecr get-login-password \
                              --region $AWS_REGION \
                              | docker login \
                                  --username AWS \
                                  --password-stdin $ECR_REGISTRY

                            docker pull \
                              $ECR_REGISTRY/$ECR_REPO_NAME:$IMAGE_TAG

                            docker stop $APP_NAME || true

                            docker rm $APP_NAME || true

                            docker run -d \
                              --name $APP_NAME \
                              --restart unless-stopped \
                              -p 8080:8080 \
                              --env-file ~/swiftkart-deploy/.env.production \
                              $ECR_REGISTRY/$ECR_REPO_NAME:$IMAGE_TAG

                            docker ps \
                              --filter name=$APP_NAME
                          "
                    '''
                }
            }
        }


        stage('Health Check') {

            steps {

                sh '''
                    set -e

                    echo "Waiting for application..."

                    sleep 20

                    curl \
                      --fail \
                      --retry 5 \
                      --retry-delay 5 \
                      "http://${EC2_PUBLIC_IP}:8080/actuator/health"
                '''
            }
        }
    }


    post {

        success {

            echo """
            ==========================================
            DEPLOYMENT SUCCESSFUL
            ==========================================

            Application:
            http://${EC2_PUBLIC_IP}:8080

            Docker image:
            ${ECR_REGISTRY}/${ECR_REPO_NAME}:${IMAGE_TAG}

            ==========================================
            """
        }

        failure {

            echo "Deployment failed."
        }

        always {

            sh """
                docker rmi -f \
                  ${ECR_REGISTRY}/${ECR_REPO_NAME}:${IMAGE_TAG} \
                  ${ECR_REGISTRY}/${ECR_REPO_NAME}:latest \
                  || true
            """
        }
    }
}
```

---

# 28. Why This Deployment Script Is Better

The original deployment script sends:

```text
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
OPENAI_KEY
```

through the SSH command to EC2.

The recommended version changes this.

Jenkins sends only:

```text
SSH connection
```

to EC2.

EC2 obtains ECR access using:

```text
IAM Role
```

The application obtains its production secrets from:

```text
~/swiftkart-deploy/.env.production
```

Therefore:

```text
Jenkins
   |
   | SSH
   v
EC2
   |
   +---- IAM Role ----> ECR
   |
   +---- .env.production
               |
               v
          Docker Container
```

---

# 29. Important: The `.env` File Is NOT Inside the Image

This is an important Docker concept.

The Docker image contains:

```text
Application JAR
Java runtime
Application dependencies
```

It should NOT contain:

```text
OPENAI_API_KEY
DATABASE_PASSWORD
AWS_SECRET_ACCESS_KEY
```

The image is:

```text
swiftkart-ai-assistant:15
```

At runtime:

```text
docker run
    |
    +-- image
    |
    +-- environment
    |
    +-- production secrets
```

This is why we use:

```bash
--env-file ~/swiftkart-deploy/.env.production
```

---

# 30. Step 21 - First Manual Deployment Test

Before trusting Jenkins, perform one manual deployment.

On EC2:

```bash
cd ~/swiftkart-deploy
```

Login:

```bash
aws ecr get-login-password \
  --region ap-south-1 \
  | docker login \
      --username AWS \
      --password-stdin \
      YOUR_AWS_ACCOUNT_ID.dkr.ecr.ap-south-1.amazonaws.com
```

Pull:

```bash
docker pull \
  YOUR_AWS_ACCOUNT_ID.dkr.ecr.ap-south-1.amazonaws.com/swiftkart-ai-assistant:latest
```

Run:

```bash
docker run -d \
  --name swiftkart-ai-assistant \
  --restart unless-stopped \
  -p 8080:8080 \
  --env-file ~/swiftkart-deploy/.env.production \
  YOUR_AWS_ACCOUNT_ID.dkr.ecr.ap-south-1.amazonaws.com/swiftkart-ai-assistant:latest
```

Check:

```bash
docker ps
```

Check logs:

```bash
docker logs swiftkart-ai-assistant
```

Test:

```bash
curl http://localhost:8080/actuator/health
```

If successful:

```text
EC2
 |
 v
Docker
 |
 v
SwiftKart
 |
 v
Spring Boot
```

is working.

---

# 31. Step 22 - Test from Your Browser

From your local machine:

```text
http://YOUR_EC2_PUBLIC_IP:8080
```

For example:

```text
http://3.110.120.45:8080
```

Health:

```text
http://YOUR_EC2_PUBLIC_IP:8080/actuator/health
```

If the application is accessible, the first deployment is complete.

---

# 32. Step 23 - Run the Complete Jenkins Flow

Now perform the real CI/CD test.

Make a small change:

```bash
git add .
git commit -m "test: deploy application to EC2"
git push origin main
```

Then observe Jenkins.

## Job 1

```text
swiftkart-pipeline
```

Expected:

```text
Checkout
   |
Build
   |
Tests
   |
JaCoCo
   |
SonarQube
   |
Quality Gate
   |
SUCCESS
```

Then Jenkins automatically starts:

```text
swiftkart-deploy-ec2
```

---

# 33. Deployment Job Expected Result

The deployment job should show:

```text
Checkout
   |
Build Docker Image
   |
Push Image to ECR
   |
Deploy to EC2
   |
Health Check
   |
SUCCESS
```

The final result should be:

```text
swiftkart-pipeline       SUCCESS
        |
        v
swiftkart-deploy-ec2     SUCCESS
        |
        v
AWS EC2
        |
        v
Docker Container
        |
        v
SwiftKart Application
```

---

# 34. Step 24 - Verify the Container on EC2

SSH into EC2:

```bash
ssh -i swiftkart-ec2-key.pem ubuntu@YOUR_EC2_PUBLIC_IP
```

Run:

```bash
docker ps
```

Expected:

```text
swiftkart-ai-assistant
```

Check logs:

```bash
docker logs swiftkart-ai-assistant
```

For live logs:

```bash
docker logs -f swiftkart-ai-assistant
```

---

# 35. Step 25 - Verify Environment Variables

You should NOT print secrets to Jenkins logs.

You can verify that the container received the expected configuration without displaying the actual values.

For example:

```bash
docker inspect swiftkart-ai-assistant
```

However, be careful: Docker metadata can expose environment variables.

Do not paste the output into Jenkins logs or public issue trackers.

For production secrets, prefer AWS Secrets Manager rather than relying on environment variables long term.

---

# 36. Step 26 - Rollback

Because every Docker image gets a build-specific tag:

```text
:15
:16
:17
:18
```

you can roll back.

For example:

```text
Current:
swiftkart-ai-assistant:18

Previous:
swiftkart-ai-assistant:17
```

SSH into EC2:

```bash
ssh -i swiftkart-ec2-key.pem ubuntu@YOUR_EC2_PUBLIC_IP
```

Pull previous image:

```bash
docker pull \
  YOUR_AWS_ACCOUNT_ID.dkr.ecr.ap-south-1.amazonaws.com/swiftkart-ai-assistant:17
```

Stop current container:

```bash
docker stop swiftkart-ai-assistant
```

Remove:

```bash
docker rm swiftkart-ai-assistant
```

Run previous image:

```bash
docker run -d \
  --name swiftkart-ai-assistant \
  --restart unless-stopped \
  -p 8080:8080 \
  --env-file ~/swiftkart-deploy/.env.production \
  YOUR_AWS_ACCOUNT_ID.dkr.ecr.ap-south-1.amazonaws.com/swiftkart-ai-assistant:17
```

Check:

```bash
docker ps
```

Check:

```bash
docker logs swiftkart-ai-assistant
```

This is much better than relying only on `latest`.

Your existing documentation also uses build-number tags specifically to make previous releases available for rollback.

---

# 37. Important Improvement: Do Not Treat `latest` as the Release Identifier

Use:

```text
swiftkart-ai-assistant:15
swiftkart-ai-assistant:16
swiftkart-ai-assistant:17
```

as the actual releases.

You can additionally maintain:

```text
swiftkart-ai-assistant:latest
```

but deployment should preferably use:

```text
:BUILD_NUMBER
```

rather than:

```text
:latest
```

because:

```text
latest
```

does not tell you exactly which build is running.

Better:

```text
Deployment #17
      |
      v
swiftkart-ai-assistant:17
```

---

# 38. Important Improvement: Health Check

Do not use:

```bash
curl ... || echo "Health check evaluated."
```

as the final health check.

That can allow Jenkins to report success even when the application is unhealthy.

Instead use:

```bash
curl --fail --retry 5 --retry-delay 5 \
  http://${EC2_PUBLIC_IP}:8080/actuator/health
```

If `curl` fails after retries:

```text
Jenkins
   |
   v
Deployment FAILED
```

The current documentation's health-check command falls back to `echo`, which can mask a failed health check.

---

# 39. Important Improvement: "Zero Downtime"

The original documentation describes:

```text
Zero-Downtime Container Restart
```

but the actual sequence is:

```text
stop old container
remove old container
start new container
```

That creates a short period when port `8080` is unavailable.

Therefore it is more accurate to call it:

```text
Container Replacement Deployment
```

rather than true zero-downtime deployment.

True zero-downtime deployment would require something such as:

```text
Load Balancer
     |
     +---- Container A
     |
     +---- Container B
```

or:

```text
Blue-Green Deployment
```

or:

```text
ECS
Kubernetes
```

For your current monolithic EC2 deployment, simple container replacement is perfectly reasonable.

---

# 40. Recommended Version 1 Architecture

Do not over-engineer the first deployment.

Use:

```text
GitHub
   |
   v
Jenkins
   |
   +------------------------+
   |                        |
   v                        v
CI Job                  CD Job
   |                        |
   |                        |
   +----------+-------------+
              |
              v
             ECR
              |
              v
             EC2
              |
              v
       Docker Container
              |
              v
      Spring Boot App
```

Secrets:

```text
Development
    |
    +-- local .env files


Production
    |
    +-- EC2 .env.production
```

---

# 41. Recommended Version 2 Architecture

Once the basic deployment works, improve secret management:

```text
                    AWS
                     |
          +----------+----------+
          |                     |
          v                     v
         ECR             Secrets Manager
          |                     |
          |                     |
          v                     |
         EC2 <------------------+
          |
          v
       Docker
          |
          v
     Spring Boot
```

Production secrets become:

```text
AWS Secrets Manager
```

instead of:

```text
~/swiftkart-deploy/.env.production
```

This is the better long-term production architecture.

---

# 42. Recommended Version 3 Architecture

If the application eventually grows beyond a single EC2 deployment:

```text
GitHub
   |
   v
Jenkins
   |
   v
ECR
   |
   v
ECS / Kubernetes
   |
   +---- Application
   |
   +---- Secrets Manager
   |
   +---- Load Balancer
```

But do not start here unless there is a real requirement.

For your current monolithic application:

```text
Jenkins + ECR + EC2 + Docker
```

is completely reasonable.

---

# 43. Complete Deployment Checklist

## Application

```text
[ ] Application runs locally
[ ] Application runs using Docker
[ ] Dockerfile created
[ ] .dockerignore created
[ ] Production profile works
[ ] Health endpoint works
```

## Git

```text
[ ] .env is ignored
[ ] .env.dev is ignored
[ ] .env.openai.dev is ignored
[ ] .env.production is ignored
[ ] No API keys committed
[ ] No passwords committed
[ ] No PEM files committed
```

## AWS

```text
[ ] ECR repository created
[ ] EC2 created
[ ] EC2 IAM role created
[ ] EC2 security group configured
[ ] EC2 key pair created
[ ] Docker installed
[ ] AWS CLI installed
[ ] ECR login tested
```

## EC2

```text
[ ] ~/swiftkart-deploy created
[ ] .env.production created
[ ] .env.production permissions = 600
[ ] Docker tested
[ ] ECR pull tested
[ ] Application container tested manually
```

## Jenkins

```text
[ ] GitHub credentials configured
[ ] AWS credentials configured for ECR push
[ ] EC2 SSH credential configured
[ ] swiftkart-pipeline exists
[ ] swiftkart-deploy-ec2 exists
[ ] Deployment trigger configured
[ ] Jenkins agent has Docker
[ ] Jenkins agent has AWS CLI
[ ] Jenkins agent has SSH
```

## CI

```text
[ ] Checkout
[ ] Maven build
[ ] Unit tests
[ ] JaCoCo
[ ] SonarQube
[ ] Quality Gate
[ ] Deployment only after SUCCESS
```

## CD

```text
[ ] Checkout
[ ] Docker build
[ ] Docker tag
[ ] ECR login
[ ] Docker push
[ ] SSH to EC2
[ ] ECR login on EC2
[ ] Docker pull
[ ] Container replacement
[ ] Production env loaded
[ ] Health check
```

---

# 44. Final CI/CD Flow

Once everything is configured, the normal developer workflow becomes extremely simple.

Developer does:

```bash
git add .
git commit -m "feature: update swiftkart assistant"
git push origin main
```

Everything else happens automatically:

```text
Git Push
   |
   v
GitHub
   |
   v
Jenkins
   |
   v
swiftkart-pipeline
   |
   +-- Checkout
   +-- Maven
   +-- Tests
   +-- JaCoCo
   +-- SonarQube
   +-- Quality Gate
   |
   | SUCCESS
   v
swiftkart-deploy-ec2
   |
   +-- Docker Build
   |
   +-- Docker Tag
   |
   +-- ECR Push
   |
   +-- SSH to EC2
   |
   +-- Docker Login
   |
   +-- Docker Pull
   |
   +-- Stop old container
   |
   +-- Start new container
   |
   +-- Health Check
   |
   | SUCCESS
   v
Production
```

The developer does not manually:

```text
docker build
docker push
ssh
docker pull
docker run
```

Jenkins handles these steps.

---

# 45. The Most Important Design Decisions

For your current application, I recommend these exact decisions:

| Area                     | Recommendation                    |
| ------------------------ | --------------------------------- |
| Application              | Existing monolith                 |
| Container                | Docker                            |
| Registry                 | Amazon ECR                        |
| Server                   | EC2                               |
| OS                       | Ubuntu 24.04 LTS                  |
| CI                       | `swiftkart-pipeline`              |
| CD                       | `swiftkart-deploy-ec2`            |
| Image tag                | Jenkins build number              |
| `latest`                 | Optional convenience tag          |
| Production secrets       | EC2 `.env.production` initially   |
| Long-term secrets        | AWS Secrets Manager               |
| EC2 → ECR authentication | IAM Role                          |
| Jenkins → EC2            | SSH key                           |
| Jenkins → ECR            | Jenkins AWS credentials initially |
| Tests                    | Maven                             |
| Code coverage            | JaCoCo                            |
| Code quality             | SonarQube                         |
| Deployment trigger       | CI SUCCESS                        |
| Health check             | `/actuator/health`                |
| Rollback                 | Previous ECR image tag            |
| Reverse proxy            | Nginx later                       |
| HTTPS                    | Later with domain + certificate   |
| Orchestration            | Not required initially            |

---

# 46. One-Line Mental Model

If you want to remember the entire architecture, remember this:

```text
GitHub
   ↓
Jenkins CI
   ↓
Quality Gate
   ↓
Jenkins CD
   ↓
Docker Image
   ↓
Amazon ECR
   ↓
EC2
   ↓
Docker Container
   ↓
Spring Boot
```

And remember the secret rule:

```text
Secrets are configuration, NOT part of the Docker image.
```

For the first implementation, keep:

```text
Development secrets
    → local .env files

Production secrets
    → EC2 .env.production

Docker image
    → application only

AWS authentication on EC2
    → IAM Role

Jenkins authentication to EC2
    → SSH credential
```

This gives you a clean, understandable CI/CD pipeline without introducing ECS, Kubernetes, Terraform, Secrets Manager, load balancers, or other infrastructure before you actually need them.

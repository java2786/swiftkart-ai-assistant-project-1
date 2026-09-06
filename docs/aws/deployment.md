# SwiftKart AI Assistant

# Docker Deployment to AWS EC2 using Jenkins CI/CD

This guide describes how to deploy the SwiftKart Spring Boot application as a Docker container on an AWS EC2 instance.

The deployment uses:

* GitHub
* Jenkins
* Docker
* Amazon ECR
* Amazon EC2
* AWS IAM Role
* AWS Secrets Manager
* AWS CLI

The application is deployed using two Jenkins jobs:

```text
swiftkart-pipeline
        |
        | CI successful
        v
swiftkart-deploy-ec2
        |
        | Docker build
        v
Amazon ECR
        |
        | SSH
        v
AWS EC2
        |
        | docker pull
        v
SwiftKart Docker Container
```

---

# 1. Final Architecture

The recommended architecture is:

```text
                         GitHub
                           |
                           |
                           v
                 +--------------------+
                 | Jenkins CI Job     |
                 | swiftkart-pipeline |
                 +--------------------+
                           |
                           |
                    Build + Test
                    SonarQube
                    Quality Gate
                           |
                           | SUCCESS
                           v
                 +----------------------+
                 | Jenkins CD Job       |
                 | swiftkart-deploy-ec2 |
                 +----------------------+
                           |
                           |
                     Docker build
                           |
                           v
                 +----------------------+
                 | Amazon ECR           |
                 | swiftkart-ai-        |
                 | assistant            |
                 +----------------------+
                           |
                           | docker pull
                           |
                           v
                 +----------------------+
                 | AWS EC2              |
                 | Amazon Linux 2023    |
                 +----------------------+
                     |            |
                     |            |
                     v            v
                 Docker       Secrets Manager
                 Container       |
                     |            |
                     +-----<------+
                           |
                    OpenAI API Key
```

The important security principle is:

```text
Jenkins
   |
   | AWS credentials
   v
ECR

EC2
   |
   | IAM Role
   v
ECR + Secrets Manager
```

Jenkins does not send AWS credentials or the OpenAI API key to EC2.

---

# 2. Decide Where Each Component Runs

| Component                   | Location                |
| --------------------------- | ----------------------- |
| Source code                 | GitHub                  |
| CI                          | Jenkins                 |
| Docker image build          | Jenkins                 |
| Docker image registry       | Amazon ECR              |
| Production container        | Amazon EC2              |
| Production secrets          | AWS Secrets Manager     |
| ECR authentication from EC2 | EC2 IAM Role            |
| EC2 SSH authentication      | Jenkins SSH private key |

This is different from putting `.env` files inside the Docker image.

Your `.env`, `.env.dev`, `.env.openai.dev` files should remain development configuration files.

Do not copy them into the Docker image.

---

# 3. Prerequisites

You need:

```text
AWS Account
GitHub repository
Jenkins
Docker
Java 21
Maven
Spring Boot application
AWS CLI
```

You also need:

```text
AWS Region:
ap-south-1

ECR Repository:
swiftkart-ai-assistant

EC2:
Amazon Linux 2023

Jenkins jobs:
swiftkart-pipeline
swiftkart-deploy-ec2
```

---

# 4. Install AWS CLI on Your Local Machine

https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html

The local AWS CLI will be used to create and configure AWS resources such as:

* Amazon ECR
* Amazon EC2
* IAM Roles
* Security Groups
* AWS Secrets Manager
* EC2 Key Pairs

Install **AWS CLI version 2** on your local machine.

---

## 4.1 Linux

### Step 1: Download AWS CLI

Run:

```bash
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" \
    -o "awscliv2.zip"
```

### Step 2: Extract the installer

```bash
unzip awscliv2.zip
```

If `unzip` is not installed:

```bash
sudo apt update
sudo apt install unzip -y
```

Then run:

```bash
unzip awscliv2.zip
```

### Step 3: Install AWS CLI

```bash
sudo ./aws/install
```

### Step 4: Verify installation

```bash
aws --version
```

You should see something similar to:

```text
aws-cli/2.x.x Python/3.x.x Linux/...
```

---

## 4.2 Windows

There are two common ways to install AWS CLI on Windows.

### Option A: MSI Installer — Recommended

Download the official AWS CLI v2 Windows installer:

[AWS CLI v2 for Windows](https://awscli.amazonaws.com/AWSCLIV2.msi?utm_source=chatgpt.com)

Run the downloaded `.msi` file.

Follow the installation wizard:

```text
AWS Command Line Interface
        |
        v
Installation
        |
        v
Finish
```

The installer normally adds AWS CLI to the Windows `PATH`.

### Step 1: Open Command Prompt

Open:

```text
Command Prompt
```

or:

```text
PowerShell
```

### Step 2: Verify installation

Run:

```powershell
aws --version
```

You should see something similar to:

```text
aws-cli/2.x.x Python/3.x.x Windows/...
```

### Option B: Install using Windows Package Manager

If `winget` is available:

```powershell
winget install Amazon.AWSCLI
```

After installation, close and reopen the terminal.

Verify:

```powershell
aws --version
```

---

## 4.3 macOS

AWS CLI v2 can be installed using the official macOS package installer.

### Option A: Official PKG Installer — Recommended

Download the AWS CLI v2 installer for macOS:

[AWS CLI v2 for macOS](https://awscli.amazonaws.com/AWSCLIV2.pkg?utm_source=chatgpt.com)

Open the downloaded `.pkg` file.

Follow the installation wizard:

```text
AWS CLI Installer
        |
        v
Introduction
        |
        v
Installation
        |
        v
Finish
```

### Step 1: Open Terminal

After installation, open a new Terminal window.

### Step 2: Verify installation

Run:

```bash
aws --version
```

You should see something similar to:

```text
aws-cli/2.x.x Python/3.x.x Darwin/...
```

### Option B: Install using Homebrew

If Homebrew is already installed:

```bash
brew install awscli
```

Verify:

```bash
aws --version
```

---

## 4.4 Verify AWS CLI Installation

Regardless of your operating system, the following command should work:

```bash
aws --version
```

Expected output will look similar to:

```text
aws-cli/2.x.x ...
```

The exact version number, Python version, and operating-system information will vary.

At this point:

```text
Linux   -> AWS CLI installed
Windows -> AWS CLI installed
macOS   -> AWS CLI installed
```

The next step is to configure AWS CLI with your AWS credentials.


---

# 5. Configure AWS CLI Locally

Run:

```bash
aws configure
```

AWS CLI asks:

```text
AWS Access Key ID:
AWS Secret Access Key:
Default region name:
Default output format:
```

Enter:

```text
AWS Access Key ID: <your-access-key>
AWS Secret Access Key: <your-secret-key>
Default region name: ap-south-1
Default output format: json
```

Verify:

```bash
aws sts get-caller-identity
```

You should get:

```json
{
    "UserId": "...",
    "Account": "123456789012",
    "Arn": "arn:aws:iam::123456789012:user/..."
}
```

From this point onward, the terminal can communicate with AWS.

---

# 6. Define AWS Variables

Run:

```bash
export AWS_REGION="ap-south-1"
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity \
    --query Account \
    --output text)
export ECR_REPO_NAME="swiftkart-ai-assistant"
export SECRET_NAME="swiftkart/prod/env"
export KEY_PAIR_NAME="swiftkart-key"
export SEC_GROUP_NAME="swiftkart-app-sg"
```

Verify:

```bash
echo $AWS_REGION
echo $AWS_ACCOUNT_ID
echo $ECR_REPO_NAME
echo $SECRET_NAME
```

Expected:

```text
ap-south-1
123456789012
swiftkart-ai-assistant
swiftkart/prod/env
```

---

# 7. Create AWS Secrets Manager Secret

Do not put production secrets in:

```text
.env
.env.dev
.env.openai.dev
GitHub
Dockerfile
Docker image
Jenkinsfile
```

Create the secret in AWS Secrets Manager.

Example:

```bash
aws secretsmanager create-secret \
    --name ${SECRET_NAME} \
    --description "Production environment secrets for SwiftKart AI Assistant" \
    --secret-string '{
      "SPRING_AI_OPENAI_API_KEY":"sk-proj-YOUR_ACTUAL_KEY_HERE",
      "SPRING_PROFILES_ACTIVE":"prod"
    }' \
    --region ${AWS_REGION}
```

Important:

The variable name must match your Spring application's configuration.

Your existing deployment documentation uses:

```text
SPRING_AI_OPENAI_API_KEY
```

when starting the container.

Therefore, if your application expects:

```text
SPRING_AI_OPENAI_API_KEY
```

use that exact name in Secrets Manager.

Verify:

```bash
aws secretsmanager describe-secret \
    --secret-id ${SECRET_NAME} \
    --region ${AWS_REGION}
```

Do not print the actual secret value unnecessarily.

---

# 8. Create Amazon ECR Repository

Create the private ECR repository:

```bash
aws ecr create-repository \
    --repository-name ${ECR_REPO_NAME} \
    --region ${AWS_REGION} \
    --image-scanning-configuration scanOnPush=true \
    --image-tag-mutability MUTABLE
```

Verify:

```bash
aws ecr describe-repositories \
    --repository-names ${ECR_REPO_NAME} \
    --region ${AWS_REGION}
```

Your ECR URL will be:

```text
<AWS_ACCOUNT_ID>.dkr.ecr.ap-south-1.amazonaws.com/swiftkart-ai-assistant
```

For example:

```text
123456789012.dkr.ecr.ap-south-1.amazonaws.com/swiftkart-ai-assistant
```

Your existing architecture also uses a private ECR repository named `swiftkart-ai-assistant`.

---

# 9. Create IAM Role for EC2

This is an important part of the architecture.

EC2 needs permission to:

```text
Pull Docker images from ECR
        +
Read production secrets from Secrets Manager
```

Do not create AWS access keys on the EC2 machine.

Instead:

```text
EC2
 |
 | IAM Instance Role
 |
 +----> ECR
 |
 +----> Secrets Manager
```

---

# 10. Create EC2 Trust Policy

Create:

```bash
cat <<EOF > ec2-trust-policy.json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "ec2.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF
```

Create the role:

```bash
aws iam create-role \
    --role-name SwiftkartEC2AppRole \
    --assume-role-policy-document file://ec2-trust-policy.json
```

---

# 11. Give EC2 ECR Read Permission

Run:

```bash
aws iam attach-role-policy \
    --role-name SwiftkartEC2AppRole \
    --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly
```

This allows EC2 to pull images from ECR.

---

# 12. Give EC2 Secrets Manager Permission

For a simple setup, you can create:

```bash
cat <<EOF > secrets-read-policy.json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue"
      ],
      "Resource": "arn:aws:secretsmanager:ap-south-1:${AWS_ACCOUNT_ID}:secret:swiftkart/prod/env*"
    }
  ]
}
EOF
```

Attach it:

```bash
aws iam put-role-policy \
    --role-name SwiftkartEC2AppRole \
    --policy-name ReadSwiftKartSecrets \
    --policy-document file://secrets-read-policy.json
```

This is preferable to:

```text
Resource: "*"
```

because the EC2 role only needs access to the SwiftKart production secret.

---

# 13. Create EC2 Instance Profile

Create:

```bash
aws iam create-instance-profile \
    --instance-profile-name SwiftkartEC2Profile
```

Attach the role:

```bash
aws iam add-role-to-instance-profile \
    --instance-profile-name SwiftkartEC2Profile \
    --role-name SwiftkartEC2AppRole
```

Now the EC2 instance can obtain temporary AWS credentials automatically through its IAM role.

---

# 14. Create EC2 Key Pair

Create:

```bash
aws ec2 create-key-pair \
    --key-name ${KEY_PAIR_NAME} \
    --query 'KeyMaterial' \
    --output text > ${KEY_PAIR_NAME}.pem
```

Protect the private key:

```bash
chmod 400 ${KEY_PAIR_NAME}.pem
```

Do not commit this file to Git.

Add to `.gitignore`:

```gitignore
*.pem
```

---

# 15. Create Security Group

Find the default VPC:

```bash
VPC_ID=$(aws ec2 describe-vpcs \
    --filters "Name=is-default,Values=true" \
    --query "Vpcs[0].VpcId" \
    --output text)

echo $VPC_ID
```

Create the security group:

```bash
SG_ID=$(aws ec2 create-security-group \
    --group-name ${SEC_GROUP_NAME} \
    --description "Security group for SwiftKart application" \
    --vpc-id ${VPC_ID} \
    --query 'GroupId' \
    --output text)

echo $SG_ID
```

---

# 16. Configure Security Group

For initial testing:

```bash
aws ec2 authorize-security-group-ingress \
    --group-id ${SG_ID} \
    --protocol tcp \
    --port 22 \
    --cidr 0.0.0.0/0
```

Application:

```bash
aws ec2 authorize-security-group-ingress \
    --group-id ${SG_ID} \
    --protocol tcp \
    --port 8080 \
    --cidr 0.0.0.0/0
```

However, for a real deployment, do not leave SSH open to the entire Internet.

Prefer:

```text
SSH 22
Source = Jenkins public IP / trusted administrator IP
```

Application:

```text
8080
Source = required clients
```

Your original document currently allows SSH from `0.0.0.0/0`; that is acceptable for a temporary lab setup but should not be the final production rule.

---

# 17. Find Amazon Linux 2023 AMI

Use the AWS public SSM parameter:

```bash
AMI_ID=$(aws ssm get-parameter \
    --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
    --query "Parameter.Value" \
    --output text)

echo $AMI_ID
```

---

# 18. Create EC2 User Data

Create:

```bash
cat <<'EOF' > user-data.sh
#!/bin/bash

dnf update -y

dnf install -y docker jq

systemctl enable docker
systemctl start docker

usermod -aG docker ec2-user 
EOF
```

The EC2 instance will automatically execute this script during first boot.

Amazon Linux 2023 supports Docker/container workloads, and AWS documents AL2023 as a current Amazon Linux platform for containerized workloads.

---

# 19. Launch EC2

Run:

```bash
INSTANCE_ID=$(aws ec2 run-instances \
    --image-id ${AMI_ID} \
    --count 1 \
    --instance-type t3.small \
    --key-name ${KEY_PAIR_NAME} \
    --security-group-ids ${SG_ID} \
    --iam-instance-profile Name=SwiftkartEC2Profile \
    --user-data file://user-data.sh \
    --tag-specifications \
    'ResourceType=instance,Tags=[{Key=Name,Value=swiftkart-ai-assistant-prod}]' \
    --query "Instances[0].InstanceId" \
    --output text)

echo "EC2 Instance: ${INSTANCE_ID}"
```

Wait:

```bash
aws ec2 wait instance-running \
    --instance-ids ${INSTANCE_ID}
```

Get public IP:

```bash
PUBLIC_IP=$(aws ec2 describe-instances \
    --instance-ids ${INSTANCE_ID} \
    --query "Reservations[0].Instances[0].PublicIpAddress" \
    --output text)

echo "EC2 IP: ${PUBLIC_IP}"
```

---

# 20. Connect to EC2

Because this is Amazon Linux:

```bash
ssh -i ${KEY_PAIR_NAME}.pem ec2-user@${PUBLIC_IP}
```

The username is:

```text
ec2-user
```

not:

```text
ubuntu
```

This is one reason it is important not to mix the Ubuntu and Amazon Linux instructions in the same deployment guide.

---

# 21. Verify Docker on EC2

Run:

```bash
docker --version
```

Then:

```bash
docker ps
```

If Docker permission is denied, log out:

```bash
exit
```

Reconnect:

```bash
ssh -i ${KEY_PAIR_NAME}.pem ec2-user@${PUBLIC_IP}
```

Then:

```bash
docker ps
```

Verify AWS CLI:

```bash
aws --version
```

Verify the EC2 IAM Role:

```bash
aws sts get-caller-identity
```

You should see an ARN similar to:

```text
arn:aws:sts::123456789012:assumed-role/SwiftkartEC2AppRole/...
```

This is an important test.

You should **not** have to run:

```bash
aws configure
```

on EC2.

The EC2 IAM Role supplies the credentials automatically.

---

# 22. Verify EC2 Can Read Secrets Manager

From EC2:

```bash
aws secretsmanager get-secret-value \
    --secret-id swiftkart/prod/env \
    --region ap-south-1 \
    --query SecretString \
    --output text
```

This confirms:

```text
EC2
 |
 | IAM Role
 v
Secrets Manager
```

Do not paste the output into logs or Jenkins.

---

# 23. Verify EC2 Can Authenticate to ECR

Run:

```bash
AWS_ACCOUNT_ID=$(aws sts get-caller-identity \
    --query Account \
    --output text)
  
AWS_REGION="ap-south-1"

ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
```

Then:

```bash
aws ecr get-login-password \
    --region ${AWS_REGION} \
| docker login \
    --username AWS \
    --password-stdin ${ECR_REGISTRY}
```

AWS documents this as the standard private ECR Docker authentication mechanism. The ECR authentication token is valid for 12 hours.

You should see:

```text
Login Succeeded
```

---

# 24. Configure Your Dockerfile

Your application already uses a multi-stage Java 21 Dockerfile.

A suitable version is:

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

---

# 25. Create `.dockerignore`

This is very important because you have multiple `.env` files.

Create:

```text
.git
.gitignore

.env
.env.*
!.env.example

target/

.idea/
.vscode/

*.log

*.pem
```

The following files must not be copied into the Docker build context:

```text
.env
.env.dev
.env.openai.dev
.env.production
```

Secrets should be supplied when the container starts.

---

# 26. Test Docker Image Locally

Before involving Jenkins:

```bash
docker build \
    -t swiftkart-ai-assistant:test \
    .
```

Check:

```bash
docker images
```

You should see:

```text
swiftkart-ai-assistant    test
```

---

# 27. Jenkins Must Be Able to Use Docker

Your Jenkins agent needs: 

```bash
docker exec -u root -it swiftkart-jenkins bash
apt-get install -y docker-cli
docker --version
```

```text
Docker
AWS CLI
Git
```

Verify on the Jenkins agent:

```bash
docker --version
```

and:

```bash
aws --version
```

If Jenkins itself is running inside Docker, make sure the Jenkins agent has access to Docker.

For example, the Jenkins execution environment must be able to execute:

```bash
docker build
docker push
```

Do not assume that Docker installed on the EC2 server is automatically available to Jenkins.

---

# 28. Configure Jenkins AWS Credentials

Jenkins needs AWS permission to push Docker images into ECR.

Create a Jenkins credential for AWS.

For example:

```text
Credential ID:
aws-ecr-credentials
```

The IAM identity used by Jenkins should have permission to:

```text
ECR authentication
ECR image push
```

At minimum, the Jenkins identity needs appropriate ECR push permissions.

The EC2 IAM Role is different.

```text
Jenkins IAM permissions
        |
        +----> Push to ECR


EC2 IAM Role
        |
        +----> Pull from ECR
        |
        +----> Read Secrets Manager
```

Your existing document already separates Jenkins AWS credentials from the EC2 SSH credential, although its original deployment script unnecessarily forwards the AWS credentials to EC2.

---

# 29. Configure Jenkins EC2 SSH Credential

In Jenkins:

```text
Manage Jenkins
    |
    v
Credentials
    |
    v
Global credentials
    |
    v
Add Credentials
```

Choose:

```text
Kind:
SSH Username with private key
```

Configure:

```text
ID:
ec2-ssh-key

Username:
ec2-user

Private Key:
contents of swiftkart-key.pem
```

Jenkins now has permission to SSH into EC2.

---

# 30. CI Jenkins Job

Your existing CI job is:

```text
swiftkart-pipeline
```

It performs:

```text
Checkout
   |
Build/Test
   |
JaCoCo
   |
SonarQube
   |
Quality Gate
```

This matches the existing architecture in your document.

At the end of the CI job:

```groovy
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
```

---

# 31. Important Jenkins Trigger Configuration

Use **one** deployment trigger mechanism.

Recommended:

```text
swiftkart-pipeline
       |
       | post { success { build ... } }
       v
swiftkart-deploy-ec2
```

Do not simultaneously configure:

```text
post { success { build ... } }
```

and:

```text
Build after other projects are built
```

for the same relationship.

Otherwise you risk triggering deployment twice.

## Your current document contains both configurations, so this should be simplified.

# 32. Create Jenkins Deployment Job

Create:

```text
New Item
```

Name:

```text
swiftkart-deploy-ec2
```

Select:

```text
Pipeline
```

Create a parameter:

```text
BUILD_TAG_REF
```

Example:

```text
15
```

This means:

```text
Docker image:
swiftkart-ai-assistant:15
```

Using build-specific tags is better than deploying only:

```text
:latest
```

because you can identify and roll back to a specific release.

---

# 33. Jenkins Deployment Flow

The deployment job performs:

```text
Checkout source
      |
      v
Docker build
      |
      v
Tag image
      |
      v
Push image to ECR
      |
      v
SSH to EC2
      |
      v
EC2 authenticates with ECR
      |
      v
docker pull
      |
      v
Read secret from Secrets Manager
      |
      v
Stop old container
      |
      v
Start new container
      |
      v
Health check
```

---

# 34. Jenkins Deployment Pipeline

Use this as the basis for your downstream deployment job:

```groovy
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

        AWS_REGION = 'ap-south-1'

        AWS_ACCOUNT_ID = '123456789012'

        ECR_REPO = 'swiftkart-ai-assistant'

        ECR_REGISTRY =
            "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

        EC2_HOST = 'YOUR_EC2_PUBLIC_IP'

        APP_NAME = 'swiftkart-app'

        SECRET_NAME = 'swiftkart/prod/env'

        IMAGE_TAG = "${params.BUILD_TAG_REF}"
    }

    stages {

        stage('Checkout') {

            steps {

                checkout scm
            }
        }

        stage('Build Docker Image') {

            steps {

                sh """
                    docker build \
                        -t ${ECR_REGISTRY}/${ECR_REPO}:${IMAGE_TAG} \
                        .
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

                        export AWS_DEFAULT_REGION="${AWS_REGION}"

                        aws ecr get-login-password \
                            --region "${AWS_REGION}" \
                        | docker login \
                            --username AWS \
                            --password-stdin "${ECR_REGISTRY}"

                        docker push \
                            "${ECR_REGISTRY}/${ECR_REPO}:${IMAGE_TAG}"
                    '''
                }
            }
        }

        stage('Deploy to EC2') {

            steps {

                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: 'ec2-ssh-key',
                        keyFileVariable: 'SSH_KEY',
                        usernameVariable: 'SSH_USER'
                    )
                ]) {

                    sh '''
                        set -e

                        ssh \
                            -o StrictHostKeyChecking=no \
                            -i "${SSH_KEY}" \
                            "${SSH_USER}@${EC2_HOST}" \
                            "
                                set -e

                                AWS_REGION='${AWS_REGION}'
                                AWS_ACCOUNT_ID='${AWS_ACCOUNT_ID}'
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

                                echo 'Pulling image...'

                                docker pull \
                                    \${ECR_REGISTRY}/\${ECR_REPO}:\${IMAGE_TAG}

                                echo 'Reading application secret...'

                                SECRET_JSON=\$(aws secretsmanager get-secret-value \
                                    --secret-id \${SECRET_NAME} \
                                    --region \${AWS_REGION} \
                                    --query SecretString \
                                    --output text)

                                OPENAI_KEY=\$(echo \"\$SECRET_JSON\" | jq -r '.SPRING_AI_OPENAI_API_KEY')

                                PROFILE=\$(echo \"\$SECRET_JSON\" | jq -r '.SPRING_PROFILES_ACTIVE')

                                echo 'Stopping old container...'

                                docker stop \${APP_NAME} || true

                                docker rm \${APP_NAME} || true

                                echo 'Starting new container...'

                                docker run -d \
                                    --name \${APP_NAME} \
                                    --restart unless-stopped \
                                    -p 8080:8080 \
                                    -e SPRING_PROFILES_ACTIVE=\"\${PROFILE}\" \
                                    -e SPRING_AI_OPENAI_API_KEY=\"\${OPENAI_KEY}\" \
                                    \${ECR_REGISTRY}/\${ECR_REPO}:\${IMAGE_TAG}

                                echo 'Container started.'

                                docker ps \
                                    --filter name=\${APP_NAME}
                            "
                    '''
                }
            }
        }

        stage('Health Check') {

            steps {

                sh '''
                    sleep 15

                    curl --fail \
                        http://${EC2_HOST}:8080/actuator/health
                '''
            }
        }
    }
}
```

---

# 35. Why AWS Credentials Are Not Passed to EC2

Notice that Jenkins does this:

```text
Jenkins
   |
   | AWS credentials
   v
Amazon ECR
```

But when Jenkins SSHes to EC2:

```text
Jenkins
   |
   | SSH key only
   v
EC2
```

EC2 then does:

```text
EC2
 |
 | IAM Role
 |
 +----> ECR
 |
 +----> Secrets Manager
```

Therefore, you should remove code such as:

```bash
export AWS_ACCESS_KEY_ID="..."
export AWS_SECRET_ACCESS_KEY="..."
```

from the EC2 deployment section.

Your original deployment script currently does exactly this, so I recommend changing it.

---

# 36. Why the OpenAI Key Is Not Passed from Jenkins

Do not do this:

```text
Jenkins
   |
   | OpenAI API key
   v
EC2
```

Instead:

```text
EC2
 |
 | IAM Role
 v
Secrets Manager
 |
 | OpenAI key
 v
Docker container
```

This keeps the OpenAI API key outside:

```text
Jenkins logs
Jenkins pipeline
Git repository
Docker image
Dockerfile
```

---

# 37. What Happens During Deployment

Suppose Jenkins build number is:

```text
25
```

The deployment job creates:

```text
swiftkart-ai-assistant:25
```

and pushes:

```text
123456789012.dkr.ecr.ap-south-1.amazonaws.com/swiftkart-ai-assistant:25
```

to ECR.

Then Jenkins connects to EC2:

```bash
ssh ec2-user@EC2_IP
```

EC2 logs into ECR:

```bash
aws ecr get-login-password ...
```

EC2 pulls:

```text
swiftkart-ai-assistant:25
```

Then:

```text
old container
     |
     | stop
     v
removed
     |
     v
new container
     |
     v
swiftkart-ai-assistant:25
```

---

# 38. Manual Deployment Test

Before testing Jenkins, manually test the deployment on EC2.

SSH:

```bash
ssh -i swiftkart-key.pem ec2-user@${PUBLIC_IP}
```

Set:

```bash
AWS_REGION="ap-south-1"

AWS_ACCOUNT_ID=$(aws sts get-caller-identity \
    --query Account \
    --output text)

ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

ECR_REPO="swiftkart-ai-assistant"
```

Login:

```bash
aws ecr get-login-password \
    --region ${AWS_REGION} \
| docker login \
    --username AWS \
    --password-stdin ${ECR_REGISTRY}
```

Pull:

```bash
docker pull \
    ${ECR_REGISTRY}/${ECR_REPO}:1
```

Replace `1` with an image tag that actually exists.

Check:

```bash
docker images
```

---

# 39. Run Container Manually

Retrieve the secret:

```bash
SECRET_JSON=$(aws secretsmanager get-secret-value \
    --secret-id swiftkart/prod/env \
    --region ap-south-1 \
    --query SecretString \
    --output text)
```

Extract:

```bash
OPENAI_KEY=$(echo "$SECRET_JSON" | jq -r '.SPRING_AI_OPENAI_API_KEY')

PROFILE=$(echo "$SECRET_JSON" | jq -r '.SPRING_PROFILES_ACTIVE')
```

Run:

```bash
docker run -d \
    --name swiftkart-app \
    --restart unless-stopped \
    -p 8080:8080 \
    -e SPRING_PROFILES_ACTIVE="${PROFILE}" \
    -e SPRING_AI_OPENAI_API_KEY="${OPENAI_KEY}" \
    ${ECR_REGISTRY}/${ECR_REPO}:1
```

Check:

```bash
docker ps
```

---

# 40. Check Container Logs

Run:

```bash
docker logs swiftkart-app
```

For continuous logs:

```bash
docker logs -f swiftkart-app
```

Stop with:

```text
Ctrl + C
```

The container continues running.

---

# 41. Test Health Endpoint

From your local machine:

```bash
curl http://${PUBLIC_IP}:8080/actuator/health
```

Expected:

```json
{
  "status": "UP"
}
```

Your existing deployment guide also uses `/actuator/health` as the health-check endpoint.

---

# 42. Test from Browser

Open:

```text
http://YOUR_EC2_PUBLIC_IP:8080
```

For example:

```text
http://3.110.120.45:8080
```

This only works if:

```text
EC2 Security Group
        |
        v
TCP 8080
        |
        v
Docker
        |
        v
Spring Boot
```

is correctly configured.

---

# 43. Complete Jenkins Deployment Flow

Once manual deployment works, Jenkins takes over.

The final process becomes:

```text
Developer
    |
    | git push
    v
GitHub
    |
    v
swiftkart-pipeline
    |
    +--> Maven build
    |
    +--> Tests
    |
    +--> JaCoCo
    |
    +--> SonarQube
    |
    +--> Quality Gate
    |
    | SUCCESS
    v
swiftkart-deploy-ec2
    |
    +--> Checkout
    |
    +--> Docker build
    |
    +--> Tag image
    |
    +--> Login to ECR
    |
    +--> Push image
    |
    +--> SSH to EC2
             |
             +--> IAM Role
             |
             +--> ECR login
             |
             +--> Pull image
             |
             +--> Read Secrets Manager
             |
             +--> Stop old container
             |
             +--> Start new container
             |
             +--> Health check
```

---

# 44. Recommended Jenkins Job Names

Use:

```text
swiftkart-pipeline
```

for CI.

Use:

```text
swiftkart-deploy-ec2
```

for CD.

Your original document already follows this two-job structure.

---

# 45. Recommended Jenkins Credentials

Jenkins should contain approximately:

```text
Credential ID             Purpose
------------------------------------------------
github-credentials        GitHub checkout
aws-access-key-id         Push to ECR
aws-secret-access-key     Push to ECR
ec2-ssh-key               SSH to EC2
```

You do **not** need:

```text
openai-api-key
```

for the deployment job if the deployment retrieves the secret from Secrets Manager.

The OpenAI key belongs in:

```text
AWS Secrets Manager
```

not Jenkins.

---

# 46. AWS CLI Responsibilities

There are three different places where AWS CLI may exist.

## Local computer

Used for:

```text
Create ECR
Create IAM
Create EC2
Create Security Group
Create Secrets Manager secret
Create Key Pair
```

Authentication:

```text
aws configure
```

---

## Jenkins agent

Used for:

```text
ECR login
Docker image push
```

Authentication:

```text
Jenkins AWS credentials
```

Do not run:

```bash
aws configure
```

inside the Jenkins pipeline.

Use Jenkins Credentials instead.

---

## EC2

Used for:

```text
ECR login
Secrets Manager
```

Authentication:

```text
EC2 IAM Role
```

Do not run:

```bash
aws configure
```

on EC2.

This is the cleanest arrangement.

---

# 47. Final Security Model

The final security model should look like this:

```text
                 Jenkins
                    |
                    | AWS credentials
                    v
                  ECR
                    ^
                    |
                    | IAM Role
                    |
                   EC2
                  /   \
                 /     \
                v       v
              ECR   Secrets Manager
                        |
                        |
                  OpenAI API Key
                        |
                        v
                  Docker Container
```

There should be no:

```text
AWS access key on EC2
AWS secret key on EC2
OpenAI key in Git
OpenAI key in Dockerfile
OpenAI key in Docker image
.env.production inside Git
```

---

# 48. Troubleshooting

## Problem: `aws: command not found`

Check:

```bash
aws --version
```

If missing on Linux, install AWS CLI v2 using the official installer.

---

## Problem: `docker: command not found`

Check:

```bash
docker --version
```

On Amazon Linux:

```bash
sudo dnf install -y docker
sudo systemctl enable docker
sudo systemctl start docker
```

Then:

```bash
sudo usermod -aG docker ec2-user
```

Log out and reconnect.

---

## Problem: `Unable to locate credentials` on EC2

Check:

```bash
aws sts get-caller-identity
```

If it fails, verify that:

```text
SwiftkartEC2Profile
        |
        v
SwiftkartEC2AppRole
```

is attached to the EC2 instance.

---

## Problem: ECR login fails

Run:

```bash
aws sts get-caller-identity
```

Then:

```bash
aws ecr get-login-password \
    --region ap-south-1 \
| docker login \
    --username AWS \
    --password-stdin \
    ${AWS_ACCOUNT_ID}.dkr.ecr.ap-south-1.amazonaws.com
```

ECR authentication must use the same AWS Region as the registry.

---

## Problem: Jenkins cannot SSH to EC2

Check:

```text
EC2 Security Group
    |
    +--> TCP 22
    |
    +--> Jenkins public IP
```

Check Jenkins credential:

```text
ec2-ssh-key
```

Check username:

```text
ec2-user
```

---

## Problem: Container starts but application fails

Check:

```bash
docker ps
```

Then:

```bash
docker logs swiftkart-app
```

Check:

```bash
docker inspect swiftkart-app
```

Also verify that the environment variable expected by the application is correct:

```text
SPRING_AI_OPENAI_API_KEY
```

versus:

```text
OPENAI_API_KEY
```

These are not automatically interchangeable.

---

# 49. Important Correction to the Original Document

Your original deployment script contains:

```bash
export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY}"
```

and:

```groovy
string(credentialsId: 'openai-api-key', variable: 'OPENAI_KEY')
```

and then sends the OpenAI key to EC2.

I recommend removing all of that.

The deployment should instead be:

```text
Jenkins
   |
   | AWS credentials
   v
ECR

Jenkins
   |
   | SSH private key
   v
EC2
   |
   | IAM Role
   +----> ECR
   |
   +----> Secrets Manager
```

This is simpler and safer.

---

# 50. Important Correction: Do Not Deploy Only `latest`

Instead of:

```bash
docker pull .../swiftkart-ai-assistant:latest
```

prefer:

```bash
docker pull .../swiftkart-ai-assistant:25
```

where:

```text
25 = Jenkins build number
```

Then ECR contains:

```text
swiftkart-ai-assistant:23
swiftkart-ai-assistant:24
swiftkart-ai-assistant:25
```

If build 25 has a problem, you can deploy:

```text
swiftkart-ai-assistant:24
```

This gives you a straightforward rollback mechanism.

Your existing documentation already recognizes the usefulness of build-specific image tags for rollback.

---

# 51. Final Deployment Checklist

Before triggering Jenkins:

## AWS

```text
[ ] AWS CLI installed locally
[ ] aws sts get-caller-identity works
[ ] ECR repository created
[ ] Secrets Manager secret created
[ ] EC2 IAM Role created
[ ] ECR read permission attached
[ ] Secrets Manager permission attached
[ ] Instance Profile created
[ ] EC2 launched
[ ] Security Group configured
```

## EC2

```text
[ ] Amazon Linux 2023 running
[ ] Docker installed
[ ] Docker running
[ ] AWS CLI installed
[ ] jq installed
[ ] EC2 IAM Role working
[ ] aws sts get-caller-identity works
[ ] ECR login works
[ ] Secrets Manager access works
```

## Application

```text
[ ] Dockerfile exists
[ ] .dockerignore exists
[ ] .env files excluded
[ ] Docker image builds successfully
[ ] Application listens on port 8080
[ ] /actuator/health works
```

## Jenkins

```text
[ ] Docker available to Jenkins agent
[ ] AWS CLI available to Jenkins agent
[ ] GitHub credential configured
[ ] AWS ECR credentials configured
[ ] EC2 SSH credential configured
[ ] swiftkart-pipeline works
[ ] SonarQube Quality Gate works
[ ] swiftkart-deploy-ec2 created
[ ] BUILD_TAG_REF configured
[ ] CI success triggers deployment
```

---

# 52. The Simplified End-to-End Sequence

If you want to remember the entire process, it is simply this:

```text
STEP 1
Install AWS CLI locally
        |
        v
STEP 2
Configure AWS CLI locally
        |
        v
STEP 3
Create Secrets Manager secret
        |
        v
STEP 4
Create ECR repository
        |
        v
STEP 5
Create EC2 IAM Role
        |
        v
STEP 6
Create EC2 + Security Group
        |
        v
STEP 7
Install Docker + AWS CLI on EC2
        |
        v
STEP 8
Verify EC2 IAM Role
        |
        v
STEP 9
Verify EC2 -> ECR
        |
        v
STEP 10
Verify EC2 -> Secrets Manager
        |
        v
STEP 11
Configure Jenkins AWS credentials
        |
        v
STEP 12
Configure Jenkins EC2 SSH credential
        |
        v
STEP 13
Create swiftkart-deploy-ec2
        |
        v
STEP 14
Jenkins builds Docker image
        |
        v
STEP 15
Jenkins pushes image to ECR
        |
        v
STEP 16
Jenkins SSHs into EC2
        |
        v
STEP 17
EC2 pulls image from ECR
        |
        v
STEP 18
EC2 reads secret from Secrets Manager
        |
        v
STEP 19
EC2 starts Docker container
        |
        v
STEP 20
Jenkins performs health check
```

The key distinction is:

```text
Jenkins = BUILD + PUSH

EC2 = PULL + RUN

ECR = IMAGE STORAGE

Secrets Manager = PRODUCTION SECRETS

IAM ROLE = EC2 AWS AUTHENTICATION
```


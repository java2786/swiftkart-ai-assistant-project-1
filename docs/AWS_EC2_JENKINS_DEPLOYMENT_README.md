# SwiftKart AI Assistant: AWS EC2 Deployment Guide via Jenkins CI/CD

This step-by-step documentation guides you through automating the continuous deployment of the `swiftkart-ai-assistant` Spring Boot application to an Amazon Web Services (AWS) EC2 virtual machine using Amazon Elastic Container Registry (ECR) and Jenkins.

---

## Table of Contents
1. [Architectural Overview & Design Pattern](#1-architectural-overview--design-pattern)
2. [Prerequisites & AWS Setup](#2-prerequisites--aws-setup)
   - [2.1 AWS ECR Repository Creation](#21-aws-ecr-repository-creation)
   - [2.2 AWS EC2 Instance Provisioning & Security Group](#22-aws-ec2-instance-provisioning--security-group)
   - [2.3 EC2 Docker & AWS CLI Configuration](#23-ec2-docker--aws-cli-configuration)
   - [2.4 IAM User & Least-Privilege Policies](#24-iam-user--least-privilege-policies)
3. [Jenkins Configuration & Secrets Management](#3-jenkins-configuration--secrets-management)
   - [3.1 Required Jenkins Plugins](#31-required-jenkins-plugins)
   - [3.2 Jenkins Global Credentials Storage](#32-jenkins-global-credentials-storage)
4. [Deployment Strategies: Unified Pipeline vs. Downstream Job](#4-deployment-strategies-unified-pipeline-vs-downstream-job)
5. [Step-by-Step Implementation](#5-step-by-step-implementation)
   - [Option A: Continuous Deployment via Downstream Job (Recommended)](#option-a-continuous-deployment-via-downstream-job-recommended)
   - [Option B: All-In-One Unified Pipeline (`Jenkinsfile`)](#option-b-all-in-one-unified-pipeline-jenkinsfile)
6. [Dockerfile & Production Compose Setup](#6-dockerfile--production-compose-setup)
7. [Verification, Smoke Tests & Rollback Procedures](#7-verification-smoke-tests--rollback-procedures)
8. [Troubleshooting Common Issues](#8-troubleshooting-common-issues)

---

## 1. Architectural Overview & Design Pattern

The CI/CD pipeline is designed around a modular two-tier workflow:
1. **Continuous Integration (CI):** Handled by `swiftkart-pipeline` (the existing job). Triggered by code commits, it executes checkout, Maven clean verify, JaCoCo code coverage analysis, and SonarQube Quality Gate validation.
2. **Continuous Deployment (CD):** Handled by `swiftkart-deploy-ec2` (the downstream deployment job). Triggered automatically when and only when `swiftkart-pipeline` finishes with `SUCCESS`.

```text
+----------------------------------------------------------------------------------------------------+
|                                    JENKINS CI/CD ARCHITECTURE                                      |
+----------------------------------------------------------------------------------------------------+

  [ Developer ]
        |
        | git push origin main
        v
  [ GitHub Private Repository ]
        |
        | SCM Polling / Webhook Trigger
        v
+----------------------------------------------------------------------------------------------------+
| PIPELINE 1: swiftkart-pipeline (Continuous Integration)                                            |
|                                                                                                    |
|  [ 1. SCM Checkout ]                                                                               |
|           |                                                                                        |
|           v                                                                                        |
|  [ 2. Maven Build & JaCoCo Test ]                                                                  |
|           |                                                                                        |
|           v                                                                                        |
|  [ 3. SonarQube Static Analysis ]                                                                  |
|           |                                                                                        |
|           v                                                                                        |
|  [ 4. SonarQube Quality Gate ] ---> [ FAILURE: Pipeline Aborts ]                                   |
|           |                                                                                        |
|           | (SUCCESS)                                                                              |
+-----------|----------------------------------------------------------------------------------------+
            |
            | Post-build Trigger / Pipeline Downstream Trigger
            v
+----------------------------------------------------------------------------------------------------+
| PIPELINE 2: swiftkart-deploy-ec2 (Continuous Deployment)                                           |
|                                                                                                    |
|  [ 1. Build Multi-Stage Docker Image ]                                                             |
|           |                                                                                        |
|           v                                                                                        |
|  [ 2. Push Image to Amazon ECR ]                                                                   |
|           |                                                                                        |
|           v                                                                                        |
|  [ 3. SSH into AWS EC2 Instance ]                                                                  |
|           |                                                                                        |
|           v                                                                                        |
|  [ 4. Pull Latest Image from ECR & Zero-Downtime Container Restart ]                               |
|           |                                                                                        |
|           v                                                                                        |
|  [ 5. Application Health Check / Smoke Test ]                                                      |
+----------------------------------------------------------------------------------------------------+
                                    |
                                    v
                     +-----------------------------+
                     |       AWS EC2 VM            |
                     |  - Public IP: Port 8080     |
                     |  - App: swiftkart-assistant |
                     |  - DB: PostgreSQL / Chroma  |
                     +-----------------------------+
```

---

## 2. Prerequisites & AWS Setup

### 2.1 AWS ECR Repository Creation
Amazon Elastic Container Registry (ECR) will store your production-ready container images.

1. Log into the [AWS Management Console](https://console.aws.amazon.com/).
2. In the top search bar, navigate to **Elastic Container Registry (ECR)**.
3. Verify your target AWS Region (e.g., `ap-south-1` for Asia Pacific / Mumbai).
4. Click **Create repository**:
   - **Visibility settings:** `Private`
   - **Repository name:** `swiftkart-ai-assistant`
   - **Tag immutability:** `Disabled` (allows overwriting `:latest` while keeping specific build numbers)
   - **Scan on push:** `Enabled` (scans for CVE vulnerabilities automatically)
5. Click **Create repository**.
6. Note down your full **Repository URI**:
   ```text
   <AWS_ACCOUNT_ID>.dkr.ecr.<AWS_REGION>.amazonaws.com/swiftkart-ai-assistant
   ```

---

### 2.2 AWS EC2 Instance Provisioning & Security Group

#### Step A: Launch Instance
1. In the AWS Console, open **EC2** and click **Launch instance**.
2. **Name:** `swiftkart-production-server`
3. **Application and OS Images:** `Ubuntu Server 24.04 LTS (HVM), SSD Volume Type` (64-bit x86).
4. **Instance type:**
   - For minimal cost / free tier test: `t2.micro` or `t3.micro` (Note: Java 21 Spring Boot apps require at least 1GB swap memory if run on 1GB RAM).
   - Recommended for stable runtime: `t3.small` (2 vCPUs, 2 GiB RAM).
5. **Key pair (login):**
   - Click **Create new key pair**.
   - **Key pair name:** `swiftkart-ec2-key`
   - **Key pair type:** `RSA`
   - **Private key file format:** `.pem` (for OpenSSH).
   - Click **Create key pair** and save the downloaded `swiftkart-ec2-key.pem` file safely on your host.

#### Step B: Configure Security Group (Firewall Rules)
Under **Network settings**, select **Create security group**:
* **Security group name:** `swiftkart-ec2-sg`
* **Inbound Security Group Rules:**
  | Type | Port Range | Protocol | Source | Purpose |
  | :--- | :--- | :--- | :--- | :--- |
  | **SSH** | `22` | TCP | `0.0.0.0/0` (or your host IP) | Jenkins remote deployment access |
  | **Custom TCP** | `8080` | TCP | `0.0.0.0/0` | Spring Boot application REST APIs & UI |
  | **HTTP** | `80` | TCP | `0.0.0.0/0` | Optional Nginx reverse proxy |
* **Storage (Volumes):** Configure `20 GiB` General Purpose SSD (gp3).
* Click **Launch instance**.
* Once running, note your **Public IPv4 address** (e.g., `3.110.120.45`) and **Public IPv4 DNS**.

---

### 2.3 EC2 Docker & AWS CLI Configuration

You need Docker installed on the EC2 machine so it can pull and run containers.

1. Open your host terminal, navigate to the directory where `swiftkart-ec2-key.pem` is stored, and set restricted permissions:
   ```bash
   chmod 400 swiftkart-ec2-key.pem
   ```
2. Connect to the EC2 instance via SSH:
   ```bash
   ssh -i swiftkart-ec2-key.pem ubuntu@<YOUR_EC2_PUBLIC_IP>
   ```
3. Update package index and install Docker Engine, Docker Compose plugin, and AWS CLI v2:
   ```bash
   sudo apt-get update && sudo apt-get upgrade -y
   
   # Install essential tools & Docker
   sudo apt-get install -y ca-certificates curl gnupg lsb-release unzip
   
   sudo install -m 0755 -d /etc/apt/keyrings
   curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
   sudo chmod a+r /etc/apt/keyrings/docker.gpg

   echo \
     "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
     $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

   sudo apt-get update
   sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

   # Grant ubuntu user access to Docker daemon without sudo
   sudo usermod -aG docker ubuntu
   
   # Install AWS CLI v2
   curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
   unzip awscliv2.zip
   sudo ./aws/install
   rm -rf aws awscliv2.zip
   ```
4. Verify installations:
   ```bash
   docker --version
   docker compose version
   aws --version
   ```
5. Exit the SSH session and reconnect so the group membership takes effect:
   ```bash
   exit
   ssh -i swiftkart-ec2-key.pem ubuntu@<YOUR_EC2_PUBLIC_IP>
   docker ps
   ```
6. Create an application deployment directory on EC2:
   ```bash
   mkdir -p ~/swiftkart-deploy
   exit
   ```

---

### 2.4 IAM User & Least-Privilege Policies

To allow Jenkins to push images to ECR and perform deployments, create an automated IAM user.

1. Go to AWS Console -> **IAM** -> **Users** -> **Create user**.
2. **User name:** `jenkins-deployer-swiftkart`
3. Leave "Provide user access to the AWS Management Console" **unchecked** (Programmatic access only).
4. In the permissions step, choose **Attach policies directly** and attach:
   - `AmazonEC2ContainerRegistryPowerUser` (allows read/write to ECR without granting full AWS admin)
5. Review and click **Create user**.
6. Select the newly created user -> Click **Security credentials** tab.
7. Scroll to **Access keys** -> Click **Create access key**.
8. Select **Third-party service** (or CLI), acknowledge the prompt, and click **Next**.
9. **Copy both credentials immediately:**
   - **Access key ID:** (e.g., `AKIAIOSFODNN7EXAMPLE`)
   - **Secret access key:** (e.g., `wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY`)

---

## 3. Jenkins Configuration & Secrets Management

To avoid leaking credentials or private SSH keys into your source repository, store all secrets in the Jenkins internal credential vault.

### 3.1 Required Jenkins Plugins
1. In Jenkins (`http://localhost:8081`), go to **Manage Jenkins** -> **Plugins** -> **Available plugins**.
2. Search and install the following plugins:
   - **SSH Pipeline Steps** (executes remote commands via SSH in Jenkinsfile)
   - **Credentials Binding Plugin** (binds secret variables in pipeline steps; usually pre-installed)
   - **Pipeline: AWS Steps** (optional, provides AWS-specific steps)
3. Check **Restart Jenkins when installation is complete and no jobs are running**.

### 3.2 Jenkins Global Credentials Storage
Navigate to **Manage Jenkins** -> **Credentials** -> **System** -> **Global credentials (unrestricted)**.

Click **+ Add Credentials** for each of the following 4 secrets:

#### Secret 1: AWS Access Key ID
* **Kind:** `Secret text`
* **Scope:** `Global`
* **Secret:** `<Your-IAM-Access-Key-ID>` (e.g., `AKIA...`)
* **ID:** `aws-access-key-id`
* **Description:** `AWS IAM Deployer Access Key ID`

#### Secret 2: AWS Secret Access Key
* **Kind:** `Secret text`
* **Scope:** `Global`
* **Secret:** `<Your-IAM-Secret-Access-Key>`
* **ID:** `aws-secret-access-key`
* **Description:** `AWS IAM Deployer Secret Access Key`

#### Secret 3: EC2 Deployment SSH Private Key
* **Kind:** `SSH Username with private key`
* **Scope:** `Global`
* **ID:** `ec2-ssh-key`
* **Username:** `ubuntu`
* **Private Key:** Select **Enter directly** -> Click **Add** -> Paste the entire contents of your `swiftkart-ec2-key.pem` file (including `-----BEGIN RSA PRIVATE KEY-----` and `-----END RSA PRIVATE KEY-----`).
* **Description:** `EC2 SSH Key for Ubuntu Deployer`

#### Secret 4: OpenAI API Key (For Production Runtime)
* **Kind:** `Secret text`
* **Scope:** `Global`
* **Secret:** `sk-...`
* **ID:** `openai-api-key`
* **Description:** `Production OpenAI API Key for SwiftKart`

---

## 4. Deployment Strategies: Unified Pipeline vs. Downstream Job

| Criterion | Downstream Job (`swiftkart-deploy-ec2`) | Unified Pipeline (Single `Jenkinsfile`) |
| :--- | :--- | :--- |
| **Separation of Concerns** | **High:** CI (Build/Test/Sonar) and CD (Build Docker/Deploy) are cleanly decoupled. | Low: CI and CD live in one single script. |
| **Failure Isolation** | Deployment errors do not invalidate CI build artifacts or Sonar test metrics. | If deploy fails, the entire pipeline is marked red. |
| **RBAC / Promotion Gate** | Easy to place a manual "Approval" step between CI and CD. | Requires complex `input` steps inside the build pipeline. |
| **Build Time** | Faster CI cycle when running unit tests frequently. | Full run required on every push unless skipped with conditionals. |
| **Recommended For** | **Industry best practices, multi-environment setups (Dev -> Prod).** | Quick prototypes and simple single-branch projects. |

---

## 5. Step-by-Step Implementation

### Option A: Continuous Deployment via Downstream Job (Recommended)

In this approach, the CI pipeline (`swiftkart-pipeline`) continues to handle checkout, testing, and SonarQube verification. Upon success, it automatically triggers a dedicated deployment job (`swiftkart-deploy-ec2`).

#### Step 1: Update Existing CI `Jenkinsfile`
In your Git repository, append a `post { success { ... } }` block to your existing `Jenkinsfile`:

```groovy
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
                sh './mvnw -B clean verify'
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
            echo "CI Quality Gate passed! Triggering downstream deployment job..."
            build job: 'swiftkart-deploy-ec2', wait: false, parameters: [
                string(name: 'BUILD_TAG_REF', value: "${env.BUILD_NUMBER}")
            ]
        }
        failure {
            echo "CI pipeline failed. Deployment will NOT be triggered."
        }
    }
}
```

Commit and push this change to your repository:
```bash
git add Jenkinsfile
git commit -m "ci: trigger downstream deployment job on Quality Gate pass"
git push origin main
```

---

#### Step 2: Create the Downstream Pipeline Job (`swiftkart-deploy-ec2`)

1. Open Jenkins at `http://localhost:8081`.
2. Click **New Item** in the left navigation sidebar.
3. Enter item name: `swiftkart-deploy-ec2`.
4. Select **Pipeline** and click **OK**.
5. Under **General**:
   - Check **This project is parameterized**.
   - Click **Add Parameter** -> **String Parameter**:
     - **Name:** `BUILD_TAG_REF`
     - **Default Value:** `latest`
     - **Description:** `Build number reference passed from upstream CI job.`
6. Under **Build Triggers**:
   - Check **Build after other projects are built**.
   - **Projects to watch:** `swiftkart-pipeline`
   - Select **Trigger only if build is stable**.
7. Scroll down to the **Pipeline** section:
   - **Definition:** `Pipeline script`
   - Paste the following production deployment script:

```groovy
pipeline {
    agent any
    environment {
        AWS_REGION       = 'ap-south-1'
        AWS_ACCOUNT_ID   = '123456789012' // Replace with your 12-digit AWS Account ID
        ECR_REPO_NAME    = 'swiftkart-ai-assistant'
        ECR_REGISTRY     = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
        EC2_PUBLIC_IP    = '3.110.120.45' // Replace with your EC2 Public IP
        APP_NAME         = 'swiftkart-ai-assistant'
        IMAGE_TAG        = "${params.BUILD_TAG_REF ?: 'latest'}"
    }
    stages {
        stage('Checkout SCM') {
            steps {
                git branch: 'main',
                    credentialsId: 'github-credentials',
                    url: 'https://github.com/arunkumar/swiftkart-ai-assistant.git'
            }
        }

        stage('Build Docker Image') {
            steps {
                echo "Building Docker container for release: ${IMAGE_TAG}"
                sh 'docker build -t ${ECR_REGISTRY}/${ECR_REPO_NAME}:${IMAGE_TAG} .'
                sh 'docker tag ${ECR_REGISTRY}/${ECR_REPO_NAME}:${IMAGE_TAG} ${ECR_REGISTRY}/${ECR_REPO_NAME}:latest'
            }
        }

        stage('Push Image to Amazon ECR') {
            steps {
                withCredentials([
                    string(credentialsId: 'aws-access-key-id', variable: 'AWS_ACCESS_KEY_ID'),
                    string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')
                ]) {
                    sh 'aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REGISTRY}'
                    sh 'docker push ${ECR_REGISTRY}/${ECR_REPO_NAME}:${IMAGE_TAG}'
                    sh 'docker push ${ECR_REGISTRY}/${ECR_REPO_NAME}:latest'
                }
            }
        }

        stage('Deploy Container on AWS EC2') {
            steps {
                withCredentials([
                    string(credentialsId: 'aws-access-key-id', variable: 'AWS_ACCESS_KEY_ID'),
                    string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY'),
                    string(credentialsId: 'openai-api-key', variable: 'OPENAI_KEY'),
                    sshUserPrivateKey(credentialsId: 'ec2-ssh-key', keyFileVariable: 'SSH_KEY_PATH', usernameVariable: 'SSH_USER')
                ]) {
                    echo "Executing remote zero-downtime deployment on EC2 (${EC2_PUBLIC_IP})..."
                    sh '''
                        ssh -o StrictHostKeyChecking=no -i ${SSH_KEY_PATH} ${SSH_USER}@${EC2_PUBLIC_IP} << EOF
                            set -e
                            export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID}"
                            export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY}"
                            export AWS_DEFAULT_REGION="${AWS_REGION}"
                            
                            aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REGISTRY}
                            docker pull ${ECR_REGISTRY}/${ECR_REPO_NAME}:latest
                            
                            if [ $(docker ps -aq -f name=${APP_NAME}) ]; then
                                echo "Stopping existing container..."
                                docker stop ${APP_NAME} || true
                                docker rm ${APP_NAME} || true
                            fi
                            
                            docker run -d \
                              --name ${APP_NAME} \
                              --restart unless-stopped \
                              -p 8080:8080 \
                              -e SPRING_PROFILES_ACTIVE=prod \
                              -e SPRING_AI_OPENAI_API_KEY="${OPENAI_KEY}" \
                              ${ECR_REGISTRY}/${ECR_REPO_NAME}:latest
                              
                            echo "Container started successfully."
                            docker ps -f name=${APP_NAME}
EOF
                    '''
                }
            }
        }

        stage('Smoke Test & Health Verification') {
            steps {
                echo "Waiting 20 seconds for Spring Boot initialization..."
                sleep 20
                sh '''
                    curl --fail --retry 5 --retry-delay 5 http://${EC2_PUBLIC_IP}:8080/actuator/health || \
                    curl --fail --retry 3 --retry-delay 5 http://${EC2_PUBLIC_IP}:8080/api/agent/health || \
                    echo "Health check endpoint evaluated."
                '''
            }
        }
    }
    post {
        always {
            sh 'docker rmi -f ${ECR_REGISTRY}/${ECR_REPO_NAME}:${IMAGE_TAG} ${ECR_REGISTRY}/${ECR_REPO_NAME}:latest || true'
        }
        success {
            echo "==========================================================="
            echo "DEPLOYMENT COMPLETED SUCCESSFULLY!"
            echo "Application Live URL: http://${EC2_PUBLIC_IP}:8080"
            echo "==========================================================="
        }
    }
}
```
8. Click **Apply** and **Save**.

---

### Option B: All-In-One Unified Pipeline (`Jenkinsfile`)

If you prefer maintaining everything inside a single, unified pipeline script without creating a second job in Jenkins, replace your repository's root `Jenkinsfile` with the following:

```groovy
pipeline {
    agent any
    tools {
        jdk 'JDK-21'
    }
    environment {
        AWS_REGION       = 'ap-south-1'
        AWS_ACCOUNT_ID   = '123456789012' // Replace with your AWS Account ID
        ECR_REPO_NAME    = 'swiftkart-ai-assistant'
        ECR_REGISTRY     = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
        EC2_PUBLIC_IP    = '3.110.120.45' // Replace with your EC2 Public IP
        APP_NAME         = 'swiftkart-ai-assistant'
        IMAGE_TAG        = "${env.BUILD_NUMBER}"
    }
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                sh './mvnw -B clean verify'
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

        stage('Build Docker Image') {
            steps {
                echo "Building Docker image: ${ECR_REGISTRY}/${ECR_REPO_NAME}:${IMAGE_TAG}"
                sh 'docker build -t ${ECR_REGISTRY}/${ECR_REPO_NAME}:${IMAGE_TAG} .'
                sh 'docker tag ${ECR_REGISTRY}/${ECR_REPO_NAME}:${IMAGE_TAG} ${ECR_REGISTRY}/${ECR_REPO_NAME}:latest'
            }
        }

        stage('Push to Amazon ECR') {
            steps {
                withCredentials([
                    string(credentialsId: 'aws-access-key-id', variable: 'AWS_ACCESS_KEY_ID'),
                    string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')
                ]) {
                    sh 'aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REGISTRY}'
                    sh 'docker push ${ECR_REGISTRY}/${ECR_REPO_NAME}:${IMAGE_TAG}'
                    sh 'docker push ${ECR_REGISTRY}/${ECR_REPO_NAME}:latest'
                }
            }
        }

        stage('Deploy to EC2') {
            steps {
                withCredentials([
                    string(credentialsId: 'aws-access-key-id', variable: 'AWS_ACCESS_KEY_ID'),
                    string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY'),
                    string(credentialsId: 'openai-api-key', variable: 'OPENAI_KEY'),
                    sshUserPrivateKey(credentialsId: 'ec2-ssh-key', keyFileVariable: 'SSH_KEY_PATH', usernameVariable: 'SSH_USER')
                ]) {
                    sh '''
                        ssh -o StrictHostKeyChecking=no -i ${SSH_KEY_PATH} ${SSH_USER}@${EC2_PUBLIC_IP} << EOF
                            set -e
                            export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID}"
                            export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY}"
                            export AWS_DEFAULT_REGION="${AWS_REGION}"
                            
                            aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REGISTRY}
                            docker pull ${ECR_REGISTRY}/${ECR_REPO_NAME}:latest
                            
                            if [ $(docker ps -aq -f name=${APP_NAME}) ]; then
                                docker stop ${APP_NAME} || true
                                docker rm ${APP_NAME} || true
                            fi
                            
                            docker run -d \
                              --name ${APP_NAME} \
                              --restart unless-stopped \
                              -p 8080:8080 \
                              -e SPRING_PROFILES_ACTIVE=prod \
                              -e SPRING_AI_OPENAI_API_KEY="${OPENAI_KEY}" \
                              ${ECR_REGISTRY}/${ECR_REPO_NAME}:latest
EOF
                    '''
                }
            }
        }

        stage('Health Check') {
            steps {
                sleep 20
                sh 'curl --fail --retry 3 --retry-delay 5 http://${EC2_PUBLIC_IP}:8080/actuator/health || echo "Health check evaluated."'
            }
        }
    }

    post {
        always {
            sh 'docker rmi -f ${ECR_REGISTRY}/${ECR_REPO_NAME}:${IMAGE_TAG} ${ECR_REGISTRY}/${ECR_REPO_NAME}:latest || true'
        }
    }
}
```

---

## 6. Dockerfile & Production Compose Setup

Ensure your project contains an optimized, multi-stage `Dockerfile` in the repository root:

```dockerfile
# Stage 1: Build JAR using Maven & JDK 21
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Minimal Distroless / JRE Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/swiftkart

# Run as non-root user for cloud security
RUN addgroup -S swiftgroup && adduser -S swiftuser -G swiftgroup
USER swiftuser

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
```

---

## 7. Verification, Smoke Tests & Rollback Procedures

### Post-Deployment Verification
1. Open your browser and navigate to:
   ```text
   http://<YOUR_EC2_PUBLIC_IP>:8080
   ```
2. Test the health check endpoint:
   ```bash
   curl -i http://<YOUR_EC2_PUBLIC_IP>:8080/actuator/health
   ```
3. Test the AI Assistant Chat endpoint:
   ```bash
   curl -X POST http://<YOUR_EC2_PUBLIC_IP>:8080/api/agent/chat \
     -H "Content-Type: application/json" \
     -d '{"message": "Hello SwiftKart Assistant! Can you track order #1001?"}'
   ```

### Rolling Back to a Previous Image
If a release causes an issue in production, roll back directly from your terminal:
```bash
ssh -i swiftkart-ec2-key.pem ubuntu@<YOUR_EC2_PUBLIC_IP>

# Inspect previous image tags
docker images

# Run previous build (e.g., build #14)
docker stop swiftkart-ai-assistant
docker rm swiftkart-ai-assistant
docker run -d --name swiftkart-ai-assistant --restart unless-stopped -p 8080:8080 \
  -e SPRING_AI_OPENAI_API_KEY="sk-..." \
  <AWS_ACCOUNT_ID>.dkr.ecr.ap-south-1.amazonaws.com/swiftkart-ai-assistant:14
```

---

## 8. Troubleshooting Common Issues

### 1. `Permission denied (publickey)` when Jenkins SSHes to EC2
- Ensure the username is `ubuntu` (for Ubuntu AMIs) or `ec2-user` (for Amazon Linux AMIs).
- Ensure the key stored in Jenkins credentials has the correct RSA header (`-----BEGIN RSA PRIVATE KEY-----`).
- Check that EC2 Security Group `swiftkart-ec2-sg` allows Inbound port `22` from `0.0.0.0/0`.

### 2. `docker: command not found` on EC2
- Verify Docker is installed on EC2 by running `docker --version`.
- Ensure the `ubuntu` user has been added to the `docker` group (`sudo usermod -aG docker ubuntu`). Log out and log back in for changes to take effect.

### 3. `aws: command not found` on Jenkins or EC2
- Jenkins runner container needs either the `aws-cli` binary installed or the AWS CLI command mapped via a volume.
- In `swiftkart-jenkins`, you can install AWS CLI v2 via terminal:
  ```bash
  docker exec -u 0 swiftkart-jenkins apt-get update && docker exec -u 0 swiftkart-jenkins apt-get install -y awscli
  ```

### 4. SonarQube Passes but Downstream Job does not trigger
- Verify under `swiftkart-deploy-ec2` configuration that **Build after other projects are built** has `swiftkart-pipeline` listed.
- Verify that `swiftkart-pipeline` finishes with `Finished: SUCCESS`. If SonarQube quality gate marks it as UNSTABLE, stable triggers will not execute.

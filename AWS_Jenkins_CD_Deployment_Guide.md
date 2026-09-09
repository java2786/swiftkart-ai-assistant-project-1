# AWS Deployment Using Jenkins CD — SwiftKart AI Launchpad

## Zero-to-CD Runbook

This document is the **single sequential deployment guide** for the current `ai-launchpad` project.

It assumes:

- AWS account is available.
- GitHub repository is available.
- Jenkins CI is already working.
- Docker is installed locally.
- The existing SwiftKart source code is **not changed** for deployment.

The deployment flow is:

```text
AWS account
   ↓
Local IAM identity
   ↓
AWS CLI
   ↓
ECR + Secrets Manager + IAM + EC2
   ↓
Manual Docker/ECR/EC2 verification
   ↓
Jenkins credentials
   ↓
Jenkins CD
   ↓
ECR
   ↓
EC2 + Docker Compose
   ↓
Health checks
```

The current application routing is:

```text
Internet
   |
   v
API Gateway :9090
   |
   +--> SwiftKart AI Assistant :8080
   |
   +--> Knowledge Service :8082
```

The existing gateway configuration uses `SWIFTKART_URL` and `KNOWLEDGE_SERVICE_URL` for these internal routes. fileciteturn23file1L144-L168

---

# 1. One unavoidable AWS Console step

If you have **nothing at all** configured in AWS, the first authentication cannot be done with AWS CLI because AWS CLI needs credentials before it can create an IAM user.

Therefore:

1. Open AWS Management Console.
2. Sign in as the AWS account owner/root user.
3. Do **not** create a root access key.
4. Create the IAM user described in the next section.
5. After that, perform the rest of this guide from the terminal.

This is the only initial bootstrap step that cannot honestly be replaced with an unauthenticated AWS CLI command.

---

# 2. Create the local IAM user

Create this user in AWS IAM:

```text
swiftkart-local-admin
```

For a personal training account, attach:

```text
AdministratorAccess
```

For an enterprise account, use IAM Identity Center or the organization's approved administrative role instead.

Create an access key for:

```text
swiftkart-local-admin
```

Keep the displayed access key ID and secret access key. The secret access key is shown only when the key is created. AWS documents `create-access-key` as the operation that creates the access-key ID and secret access key. citeturn0search9

Never put this key in GitHub, Dockerfiles, Jenkinsfiles, Docker images, or EC2.

---

# 3. Install AWS CLI locally

## macOS

```bash
brew install awscli
aws --version
```

## Windows

Install AWS CLI v2, open PowerShell, then:

```powershell
aws --version
```

## Linux

Install AWS CLI v2, then:

```bash
aws --version
```

Amazon Linux 2023 includes AWS CLI v2. citeturn1search0

---

# 4. Configure AWS CLI

Run:

```bash
aws configure
```

Enter the access key created for `swiftkart-local-admin`:

```text
AWS Access Key ID: <paste the access key shown by AWS>
AWS Secret Access Key: <paste the secret key shown by AWS>
Default region name: ap-south-1
Default output format: json
```

The two credential values are intentionally entered interactively. They cannot be safely hard-coded into a public document.

Verify immediately:

```bash
aws sts get-caller-identity
```

Do not continue until this succeeds.

---

# 5. Set all deployment variables once

Run this entire block once in the terminal that will be used for AWS setup.

```bash
export AWS_REGION="ap-south-1"

export AWS_ACCOUNT_ID="$(aws sts get-caller-identity \
  --query Account \
  --output text)"

export ECR_SWIFTKART_REPO="swiftkart-ai-assistant-cd"
export ECR_KNOWLEDGE_REPO="knowledge-service-cd"
export ECR_GATEWAY_REPO="api-gateway-cd"

export SECRET_NAME="swiftkart/prod/env/cd"

export EC2_NAME="swiftkart-microservices-prod-cd"
export EC2_USER="ec2-user"

export KEY_PAIR_NAME="swiftkart-key-cd"
export KEY_FILE="${KEY_PAIR_NAME}.pem"

export SEC_GROUP_NAME="swiftkart-microservices-sg"

export ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

export IMAGE_TAG="cd-${USER}-001"

export SWIFTKART_IMAGE="${ECR_REGISTRY}/${ECR_SWIFTKART_REPO}:${IMAGE_TAG}"
export KNOWLEDGE_IMAGE="${ECR_REGISTRY}/${ECR_KNOWLEDGE_REPO}:${IMAGE_TAG}"
export GATEWAY_IMAGE="${ECR_REGISTRY}/${ECR_GATEWAY_REPO}:${IMAGE_TAG}"

export EC2_Profile="SwiftkartEC2ProfileCD"
```

Verify:

```bash
echo "AWS_REGION=${AWS_REGION}"
echo "AWS_ACCOUNT_ID=${AWS_ACCOUNT_ID}"
echo "ECR_REGISTRY=${ECR_REGISTRY}"
echo "ECR_SWIFTKART_REPO=${ECR_SWIFTKART_REPO}"
echo "ECR_KNOWLEDGE_REPO=${ECR_KNOWLEDGE_REPO}"
echo "ECR_GATEWAY_REPO=${ECR_GATEWAY_REPO}"
echo "SECRET_NAME=${SECRET_NAME}"
echo "EC2_NAME=${EC2_NAME}"
echo "KEY_PAIR_NAME=${KEY_PAIR_NAME}"
echo "KEY_FILE=${KEY_FILE}"
echo "SEC_GROUP_NAME=${SEC_GROUP_NAME}"
echo "IMAGE_TAG=${IMAGE_TAG}"

echo "EC2_Profile=${EC2_Profile}"
```

Verify the account:

```bash
aws sts get-caller-identity
```

From this point forward, use the variables. Do not repeatedly type account IDs, repository names, region names, or resource names.

---

# 6. Create ECR repositories

```bash
aws ecr create-repository \
  --repository-name "${ECR_SWIFTKART_REPO}" \
  --image-scanning-configuration scanOnPush=true \
  --region "${AWS_REGION}"

aws ecr create-repository \
  --repository-name "${ECR_KNOWLEDGE_REPO}" \
  --image-scanning-configuration scanOnPush=true \
  --region "${AWS_REGION}"

aws ecr create-repository \
  --repository-name "${ECR_GATEWAY_REPO}" \
  --image-scanning-configuration scanOnPush=true \
  --region "${AWS_REGION}"
```

Verify:

```bash
aws ecr describe-repositories \
  --repository-names \
    "${ECR_SWIFTKART_REPO}" \
    "${ECR_KNOWLEDGE_REPO}" \
    "${ECR_GATEWAY_REPO}" \
  --region "${AWS_REGION}"
```

---

# 7. Create the Secrets Manager secret

The runtime secret contains:



Verify metadata only:

```bash
aws secretsmanager create-secret \
  --name "${SECRET_NAME}" \
  --description "SwiftKart runtime API keys" \
  --secret-string '{
   "OPENAI_API_KEY":"<your OpenAI key>",
   "TAVILY_API_KEY":"<your Tavily key>"
   }' \
  --region "${AWS_REGION}"
```


If the secret already exists and needs a new value, use `put-secret-value` with the same interactive technique.

---

# 8. Create the EC2 IAM role

Create the EC2 trust policy:

```bash
cat > /tmp/swiftkart-ec2-trust-policy.json <<EOF
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
```bash
aws iam create-role \
  --role-name SwiftkartEC2MicroservicesRole \
  --assume-role-policy-document file:///tmp/swiftkart-ec2-trust-policy.json
```

Attach ECR read permission:

```bash
aws iam attach-role-policy \
  --role-name SwiftkartEC2MicroservicesRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly
```

Create a least-privilege Secrets Manager policy for the one application secret:

```bash
cat > /tmp/swiftkart-ec2-secrets-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue"
      ],
      "Resource": "arn:aws:secretsmanager:${AWS_REGION}:${AWS_ACCOUNT_ID}:secret:${SECRET_NAME}*"
    }
  ]
}
EOF
```

Attach it:

```bash
aws iam put-role-policy \
  --role-name SwiftkartEC2MicroservicesRole \
  --policy-name ReadSwiftkartRuntimeSecret \
  --policy-document file:///tmp/swiftkart-ec2-secrets-policy.json
```

Remove the temporary policy files:

```bash
rm -f /tmp/swiftkart-ec2-trust-policy.json
rm -f /tmp/swiftkart-ec2-secrets-policy.json
```

---

# 9. Create the EC2 instance profile

```bash
aws iam create-instance-profile \
  --instance-profile-name "${EC2_Profile}"
```

Attach the role:

```bash
aws iam add-role-to-instance-profile \
  --instance-profile-name "${EC2_Profile}" \
  --role-name SwiftkartEC2MicroservicesRole
```

Wait for IAM propagation:

```bash
sleep 15
```

Verify:

```bash
aws iam get-instance-profile \
  --instance-profile-name "${EC2_Profile}"
```

---

# 10. Create the EC2 key pair

```bash
aws ec2 create-key-pair \
  --key-name "${KEY_PAIR_NAME}" \
  --query KeyMaterial \
  --output text \
  --region "${AWS_REGION}" > "${KEY_FILE}"
```

Protect it:

```bash
chmod 400 "${KEY_FILE}"
```

Verify:

```bash
test -f "${KEY_FILE}" && echo "EC2 key created: ${KEY_FILE}"
```

Never commit this file.
```bash
echo "\n${KEY_FILE}\n" >> .gitignore
echo "\n${KEY_FILE}\n" >> .dockerignore
```
---

# 11. Create the Security Group

Get the default VPC automatically:

```bash
export VPC_ID="$(aws ec2 describe-vpcs \
  --filters "Name=is-default,Values=true" \
  --query "Vpcs[0].VpcId" \
  --output text \
  --region "${AWS_REGION}")"
```

Verify:

```bash
echo "VPC_ID=${VPC_ID}"
```

Create the security group:

```bash
export SG_ID="$(aws ec2 create-security-group \
  --group-name "${SEC_GROUP_NAME}" \
  --description "SwiftKart microservices EC2 security group" \
  --vpc-id "${VPC_ID}" \
  --query GroupId \
  --output text \
  --region "${AWS_REGION}")"
```

Verify:

```bash
echo "SG_ID=${SG_ID}"
```

AWS security groups are the EC2 network firewall mechanism. citeturn0search0turn0search4

---

# 12. Allow SSH from the current computer

Automatically obtain the current public IP:

```bash
export MY_PUBLIC_IP="$(curl -s https://checkip.amazonaws.com)"
```

Verify:

```bash
echo "MY_PUBLIC_IP=${MY_PUBLIC_IP}"
```

Allow SSH:

```bash
aws ec2 authorize-security-group-ingress \
  --group-id "${SG_ID}" \
  --protocol tcp \
  --port 22 \
  --cidr "${MY_PUBLIC_IP}/32" \
  --region "${AWS_REGION}"
```

This avoids opening SSH to the entire Internet.

---

# 13. Allow API Gateway

The current gateway is `9090`.

```bash
aws ec2 authorize-security-group-ingress \
  --group-id "${SG_ID}" \
  --protocol tcp \
  --port 9090 \
  --cidr 0.0.0.0/0 \
  --region "${AWS_REGION}"
```

Do not expose the internal service ports publicly.

---

# 14. Launch EC2 completely from AWS CLI

Use Amazon Linux 2023 x86_64 and `t3.large`.

AWS supports launching AL2023 through its public SSM AMI parameter, so no AMI ID needs to be copied manually. citeturn0search1turn0search2

Create the EC2 bootstrap script:

```bash
cat > /tmp/swiftkart-ec2-user-data.sh <<'USERDATA'
#!/bin/bash

dnf update -y
dnf install -y docker jq git

systemctl enable docker
systemctl start docker

usermod -aG docker ec2-user

mkdir -p /usr/local/lib/docker/cli-plugins

curl -SL \
  https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose

chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

mkdir -p /opt/swiftkart
chown -R ec2-user:ec2-user /opt/swiftkart
USERDATA
```

Launch:

```bash
export INSTANCE_ID="$(aws ec2 run-instances \
  --image-id resolve:ssm:/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
  --instance-type t3.large \
  --count 1 \
  --key-name "${KEY_PAIR_NAME}" \
  --security-group-ids "${SG_ID}" \
  --iam-instance-profile Name="${EC2_Profile}" \
  --associate-public-ip-address \
  --user-data file:///tmp/swiftkart-ec2-user-data.sh \
  --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=${EC2_NAME}}]" \
  --query "Instances[0].InstanceId" \
  --output text \
  --region "${AWS_REGION}")"
```

Verify:

```bash
echo "INSTANCE_ID=${INSTANCE_ID}"
```

Wait until running:

```bash
aws ec2 wait instance-running \
  --instance-ids "${INSTANCE_ID}" \
  --region "${AWS_REGION}"
```

Wait for status checks:

```bash
aws ec2 wait instance-status-ok \
  --instance-ids "${INSTANCE_ID}" \
  --region "${AWS_REGION}"
```

```bash
rm -f /tmp/swiftkart-ec2-user-data.sh
```
---

# 15. Obtain EC2_HOST automatically

Do not type an EC2 IP manually.

Read it directly from the instance that was just created:

```bash
export EC2_HOST="$(aws ec2 describe-instances \
  --instance-ids "${INSTANCE_ID}" \
  --query "Reservations[0].Instances[0].PublicIpAddress" \
  --output text \
  --region "${AWS_REGION}")"
```

Verify:

```bash
echo "EC2_HOST=${EC2_HOST}"
```

Because the command uses the exact `INSTANCE_ID` returned by `run-instances`, there is no `None`/missing-tag discovery step here.

---

# 16. SSH to EC2

Wait for port 22:

```bash
until nc -z "${EC2_HOST}" 22 2>/dev/null; do
  echo "Waiting for SSH on ${EC2_HOST}:22..."
  sleep 5
done
```

Connect:

```bash
ssh -i "${KEY_FILE}" \
  -o StrictHostKeyChecking=no \
  "${EC2_USER}@${EC2_HOST}"
```

---

# 17. Verify EC2

Run on EC2:

```bash
uname -m
```

Expected:

```text
x86_64
```

Verify OS:

```bash
cat /etc/os-release
```

Verify Docker:

```bash
docker --version
docker compose version
```

Verify AWS CLI:

```bash
aws --version
```

Verify the IAM role:

```bash
aws sts get-caller-identity
```

Do **not** run `aws configure` on EC2. The instance role supplies temporary AWS credentials.

Exit:

```bash
exit
```

---

# 18. Verify EC2 → ECR

SSH again:

```bash
ssh -i "${KEY_FILE}" \
  -o StrictHostKeyChecking=no \
  "${EC2_USER}@${EC2_HOST}"
```

On EC2:

```bash
export AWS_REGION="ap-south-1"
export AWS_ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
export ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
```

Login:

```bash
aws ecr get-login-password \
  --region "${AWS_REGION}" |
docker login \
  --username AWS \
  --password-stdin "${ECR_REGISTRY}"
```

Expected:

```text
Login Succeeded
```

Exit:

```bash
exit
```

---

# 19. Build the three images locally

From the `ai-launchpad` repository root:

```bash
docker build \
  --platform linux/amd64 \
  -t "${SWIFTKART_IMAGE}" \
  ./swiftkart-ai-assistant
```

```bash
docker build \
  --platform linux/amd64 \
  -t "${KNOWLEDGE_IMAGE}" \
  ./knowledge-service
```

```bash
docker build \
  --platform linux/amd64 \
  -t "${GATEWAY_IMAGE}" \
  ./api-gateway
```

Verify architecture:

```bash
docker image inspect "${SWIFTKART_IMAGE}" --format '{{.Os}}/{{.Architecture}}'
docker image inspect "${KNOWLEDGE_IMAGE}" --format '{{.Os}}/{{.Architecture}}'
docker image inspect "${GATEWAY_IMAGE}" --format '{{.Os}}/{{.Architecture}}'
```

Expected:

```text
linux/amd64
linux/amd64
linux/amd64
```

---

# 20. Manually push images to ECR

This proves ECR before Jenkins is introduced.

```bash
aws ecr get-login-password \
  --region "${AWS_REGION}" |
docker login \
  --username AWS \
  --password-stdin "${ECR_REGISTRY}"
```

Push:

```bash
docker push "${SWIFTKART_IMAGE}"
docker push "${KNOWLEDGE_IMAGE}"
docker push "${GATEWAY_IMAGE}"
```

Verify:

```bash
aws ecr describe-images \
  --repository-name "${ECR_SWIFTKART_REPO}" \
  --region "${AWS_REGION}"

aws ecr describe-images \
  --repository-name "${ECR_KNOWLEDGE_REPO}" \
  --region "${AWS_REGION}"

aws ecr describe-images \
  --repository-name "${ECR_GATEWAY_REPO}" \
  --region "${AWS_REGION}"
```

---

# 21. Verify EC2 → Secrets Manager

SSH to EC2:

```bash
ssh -i "${KEY_FILE}" \
  -o StrictHostKeyChecking=no \
  "${EC2_USER}@${EC2_HOST}"
```

Remember we are in EC2. Run:
```bash
export AWS_REGION="ap-south-1"
export SECRET_NAME="swiftkart/prod/env/cd"

umask 077

aws secretsmanager get-secret-value \
  --secret-id "${SECRET_NAME}" \
  --query SecretString \
  --output text > /tmp/swiftkart-runtime-secret.json
```

Verify only permissions:

```bash
ls -l /tmp/swiftkart-runtime-secret.json
```

Delete immediately:

```bash
rm -f /tmp/swiftkart-runtime-secret.json
```

Exit:

```bash
exit
```

---

# 22. Manual Docker Compose deployment
---

# 23. Manual health checks

---

# 24. Create Jenkins IAM user

Jenkins must use a separate AWS identity.

Create:

```text
swiftkart-jenkins
```

Run locally:

```bash
aws iam create-user \
  --user-name swiftkart-jenkins
```

The Jenkins identity needs:

- ECR authentication
- ECR image push
- EC2 `DescribeInstances` so Jenkins can automatically discover the EC2 public IP from its `Name` tag

Create the policy:

```bash
cat > /tmp/swiftkart-jenkins-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "ecr:BatchCheckLayerAvailability",
        "ecr:CompleteLayerUpload",
        "ecr:InitiateLayerUpload",
        "ecr:PutImage",
        "ecr:UploadLayerPart"
      ],
      "Resource": [
        "arn:aws:ecr:${AWS_REGION}:${AWS_ACCOUNT_ID}:repository/${ECR_SWIFTKART_REPO}",
        "arn:aws:ecr:${AWS_REGION}:${AWS_ACCOUNT_ID}:repository/${ECR_KNOWLEDGE_REPO}",
        "arn:aws:ecr:${AWS_REGION}:${AWS_ACCOUNT_ID}:repository/${ECR_GATEWAY_REPO}"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "ec2:DescribeInstances"
      ],
      "Resource": "*"
    }
  ]
}
EOF
```

Attach it:

```bash
aws iam put-user-policy \
  --user-name swiftkart-jenkins \
  --policy-name PushImagesAndDiscoverEC2 \
  --policy-document file:///tmp/swiftkart-jenkins-policy.json
```

Create the access key:

```bash
aws iam create-access-key \
  --user-name swiftkart-jenkins \
  --output json
```

Save the displayed `AccessKeyId` and `SecretAccessKey` immediately.

Delete the temporary policy file:

```bash
rm -f /tmp/swiftkart-jenkins-policy.json
```

Do not use this Jenkins access key with `aws configure` locally.

---

# 25. Jenkins credentials

Open:

```text
Manage Jenkins
→ Credentials
→ System
→ Global credentials
→ Add Credentials
```

## GitHub

Use the existing working credential:

```text
Kind: Username with Password
ID: github-credentials
Description: Git with jenkins
Username: <github user>
Password: <github access key>
```

## AWS

Get details:
```bash
cat ~/.aws/credentials 
```

Create:
```text
Kind: AWS Credentials
ID: aws-jenkins
Description: AWS credentials for Jenkins CI/CD
AccessKeyId: 
SecretAccessKey: 
```

Enter the `AccessKeyId` and `SecretAccessKey` created for `swiftkart-jenkins`.

## EC2 SSH

Create:

```text
Kind: SSH Username with private key
ID: ec2-ssh-key
Username: ec2-user
Description: EC2 ssh with jenkins
```

Paste the contents of:

```bash
cat ./swiftkart-key-cd.pem
```

The project deployment design separates Jenkins ECR credentials from the EC2 SSH credential. 

---

# 26. Jenkins CD job

Create:

```text
New Item
```

Name:

```text
swiftkart-microservices-deploy-ec2
```

Select:

```text
Pipeline
```

Use:

```text
Pipeline script
```

This job is CD only. It does not run Maven, JaCoCo or SonarQube.

---

# 27. Jenkins CD Pipeline

Paste this complete pipeline into the Jenkins job.

The pipeline does **not** require an EC2 IP parameter. It discovers the running instance using:

```text
Name=swiftkart-microservices-prod-cd
```

It calculates the AWS account ID from the Jenkins AWS credential.

```groovy
pipeline {
    agent any

    parameters {
        string(
            name: 'IMAGE_TAG',
            defaultValue: '',
            description: 'Leave empty to use the Jenkins build number.'
        )
    }

    environment {
        AWS_REGION = 'ap-south-1'
        ECR_SWIFTKART_REPO = 'swiftkart-ai-assistant-cd'
        ECR_KNOWLEDGE_REPO = 'knowledge-service-cd'
        ECR_GATEWAY_REPO = 'api-gateway-cd'
        SECRET_NAME = 'swiftkart/prod/env/cd'
        EC2_NAME = 'swiftkart-microservices-prod-cd'
        EC2_USER = 'ec2-user'
        DEPLOY_DIR = '/opt/swiftkart'
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main',
                    credentialsId: 'github-credentials',
                    url: 'https://github.com/java2786/swiftkart-ai-assistant-project-1'
            }
        }

        stage('Prepare Variables') {
            steps {
                script {
                    env.IMAGE_TAG = params.IMAGE_TAG?.trim()
                        ? params.IMAGE_TAG.trim()
                        : "${env.BUILD_NUMBER}"
                }
            }
        }

        stage('Build Docker Images') {
            steps {
                withCredentials([
                    [$class: 'AmazonWebServicesCredentialsBinding',
                     credentialsId: 'aws-jenkins']
                ]) {
                    sh '''
                        set -e

                        export AWS_DEFAULT_REGION="${AWS_REGION}"
                        export AWS_ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
                        export ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

                        docker build --platform linux/amd64 \
                          -t "${ECR_REGISTRY}/${ECR_SWIFTKART_REPO}:${IMAGE_TAG}" \
                          ./swiftkart-ai-assistant

                        docker build --platform linux/amd64 \
                          -t "${ECR_REGISTRY}/${ECR_KNOWLEDGE_REPO}:${IMAGE_TAG}" \
                          ./knowledge-service

                        docker build --platform linux/amd64 \
                          -t "${ECR_REGISTRY}/${ECR_GATEWAY_REPO}:${IMAGE_TAG}" \
                          ./api-gateway
                    '''
                }
            }
        }

        stage('Push Images to ECR') {
            steps {
                withCredentials([
                    [$class: 'AmazonWebServicesCredentialsBinding',
                     credentialsId: 'aws-jenkins']
                ]) {
                    sh '''
                        set -e

                        export AWS_DEFAULT_REGION="${AWS_REGION}"
                        export AWS_ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
                        export ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

                        aws ecr get-login-password --region "${AWS_REGION}" |
                          docker login --username AWS --password-stdin "${ECR_REGISTRY}"

                        docker push "${ECR_REGISTRY}/${ECR_SWIFTKART_REPO}:${IMAGE_TAG}"
                        docker push "${ECR_REGISTRY}/${ECR_KNOWLEDGE_REPO}:${IMAGE_TAG}"
                        docker push "${ECR_REGISTRY}/${ECR_GATEWAY_REPO}:${IMAGE_TAG}"
                    '''
                }
            }
        }

        stage('Deploy to EC2') {
            steps {
                withCredentials([
                    [$class: 'AmazonWebServicesCredentialsBinding',
                     credentialsId: 'aws-jenkins'],
                    sshUserPrivateKey(
                        credentialsId: 'ec2-ssh-key',
                        keyFileVariable: 'SSH_KEY',
                        usernameVariable: 'SSH_USER'
                    )
                ]) {
                    sh '''
                        set -e

                        export AWS_DEFAULT_REGION="${AWS_REGION}"

                        export EC2_HOST="$(aws ec2 describe-instances \
                          --filters \
                            "Name=tag:Name,Values=${EC2_NAME}" \
                            "Name=instance-state-name,Values=running" \
                          --query 'Reservations[0].Instances[0].PublicIpAddress' \
                          --output text \
                          --region "${AWS_REGION}")"

                        test -n "${EC2_HOST}"
                        test "${EC2_HOST}" != "None"

                        scp -o StrictHostKeyChecking=no \
                          -i "${SSH_KEY}" \
                          docker-compose-prod.yml \
                          "${SSH_USER}@${EC2_HOST}:${DEPLOY_DIR}/docker-compose-prod.yml"

                        ssh -o StrictHostKeyChecking=no \
                          -i "${SSH_KEY}" \
                          "${SSH_USER}@${EC2_HOST}" \
                          "AWS_REGION='${AWS_REGION}' \
                           ECR_SWIFTKART_REPO='${ECR_SWIFTKART_REPO}' \
                           ECR_KNOWLEDGE_REPO='${ECR_KNOWLEDGE_REPO}' \
                           ECR_GATEWAY_REPO='${ECR_GATEWAY_REPO}' \
                           SECRET_NAME='${SECRET_NAME}' \
                           IMAGE_TAG='${IMAGE_TAG}' \
                           DEPLOY_DIR='${DEPLOY_DIR}' \
                           bash -s" <<'REMOTE_SCRIPT'

                        set -e

                        export AWS_ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
                        export ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

                        aws ecr get-login-password --region "${AWS_REGION}" |
                          docker login --username AWS --password-stdin "${ECR_REGISTRY}"

                        docker pull "${ECR_REGISTRY}/${ECR_SWIFTKART_REPO}:${IMAGE_TAG}"
                        docker pull "${ECR_REGISTRY}/${ECR_KNOWLEDGE_REPO}:${IMAGE_TAG}"
                        docker pull "${ECR_REGISTRY}/${ECR_GATEWAY_REPO}:${IMAGE_TAG}"

                        mkdir -p "${DEPLOY_DIR}"
                        cd "${DEPLOY_DIR}"

                        export REPO_ASSISTANT="${ECR_SWIFTKART_REPO}"
                        export REPO_KNOWLEDGE="${ECR_KNOWLEDGE_REPO}"
                        export REPO_GATEWAY="${ECR_GATEWAY_REPO}"

                        umask 077
                        aws secretsmanager get-secret-value \
                          --secret-id "${SECRET_NAME}" \
                          --query SecretString \
                          --output text > /tmp/swiftkart-runtime-secret.json

                        export OPENAI_KEY="$(jq -r '.OPENAI_API_KEY' /tmp/swiftkart-runtime-secret.json)"
                        export TAVILY_KEY="$(jq -r '.TAVILY_API_KEY' /tmp/swiftkart-runtime-secret.json)"
                        rm -f /tmp/swiftkart-runtime-secret.json

                        docker compose -f docker-compose-prod.yml config
                        docker compose -f docker-compose-prod.yml up -d --remove-orphans
                        docker compose -f docker-compose-prod.yml ps

                        unset OPENAI_KEY TAVILY_KEY

REMOTE_SCRIPT
                    '''
                }
            }
        }

        stage('Health Check') {
            steps {
                withCredentials([
                    [$class: 'AmazonWebServicesCredentialsBinding',
                     credentialsId: 'aws-jenkins'],
                    sshUserPrivateKey(
                        credentialsId: 'ec2-ssh-key',
                        keyFileVariable: 'SSH_KEY',
                        usernameVariable: 'SSH_USER'
                    )
                ]) {
                    sh '''
                        set -e

                        export AWS_DEFAULT_REGION="${AWS_REGION}"

                        export EC2_HOST="$(aws ec2 describe-instances \
                          --filters \
                            "Name=tag:Name,Values=${EC2_NAME}" \
                            "Name=instance-state-name,Values=running" \
                          --query 'Reservations[0].Instances[0].PublicIpAddress' \
                          --output text \
                          --region "${AWS_REGION}")"

                        ssh -o StrictHostKeyChecking=no \
                          -i "${SSH_KEY}" \
                          "${SSH_USER}@${EC2_HOST}" \
                          'bash -s' <<'REMOTE_HEALTH'

                        set -e

                        for i in $(seq 1 12); do
                            if curl --fail --silent --show-error \
                              http://localhost:9090/actuator/health; then

                                echo

                                if curl --fail --silent --show-error \
                                  http://localhost:9090/api/knowledge/health; then

                                    echo
                                    echo "Deployment health checks passed."
                                    exit 0
                                fi
                            fi

                            echo "Application not ready. Retrying in 5 seconds..."
                            sleep 5
                        done

                        echo "Deployment health check failed."
                        docker compose -f docker-compose-prod.yml -f /opt/swiftkart/docker-compose-prod.yml ps || true
                        docker compose -f docker-compose-prod.yml -f /opt/swiftkart/docker-compose-prod.yml logs --tail=100 || true
                        exit 1

REMOTE_HEALTH
                    '''
                }
            }
        }
    }

    post {
        success {
            echo 'SwiftKart microservices CD deployment completed successfully.'
        }
        failure {
            echo 'Deployment failed. Inspect Jenkins and EC2 Docker Compose logs.'
        }
    }
}
```

---

# 28. Important requirement for docker-compose-prod.yml

The CD pipeline copies the repository's existing:

```text
docker-compose-prod.yml
```

without rewriting it.

Therefore the existing Compose file must already use the image/configuration variables required by the current application configuration.

The current project configuration shows:

```yaml
server:
  port: ${PORT:9090}
```

for API Gateway and:

```text
SWIFTKART_URL=http://swiftkart-ai-assistant:8080
KNOWLEDGE_SERVICE_URL=http://knowledge-service:8082
```

for internal routing. fileciteturn23file1L144-L190

The CD guide does not silently invent new application property names or modify SwiftKart source code.

---

# 29. CI is not repeated in CD

The existing service Jenkinsfiles are responsible for Maven build/test and SonarQube Quality Gate validation. fileciteturn23file8L937-L1099

CD performs:

```text
Checkout
   ↓
Docker Build
   ↓
ECR Push
   ↓
EC2 SSH
   ↓
ECR Pull
   ↓
Docker Compose
   ↓
Health Check
```

Do not add Maven, JaCoCo or SonarQube stages to the CD pipeline.

---

# 30. CI → CD trigger

Use one trigger mechanism only.

After CI has passed its Quality Gate, the CI job can trigger CD:

```groovy
post {
    success {
        build job: 'swiftkart-microservices-deploy-ec2',
            wait: false,
            parameters: [
                string(
                    name: 'IMAGE_TAG',
                    value: "${env.BUILD_NUMBER}"
                )
            ]
    }

    failure {
        echo 'CI failed. CD will not start.'
    }
}
```

Do not configure both this and `Build after other projects are built` for the same relationship. The existing project documentation explicitly warns about duplicate triggering. fileciteturn21file0L162-L190

For three independently validated services, a release pipeline should ultimately promote a known compatible set of all three images rather than assuming one service's build number represents all three.

---

# 31. Release tags and rollback

When `IMAGE_TAG` is empty, the CD pipeline uses the Jenkins build number.

Example:

```text
swiftkart-ai-assistant:42
knowledge-service:42
api-gateway:42
```

Rollback means deploying the previous complete release set:

```text
swiftkart-ai-assistant:41
knowledge-service:41
api-gateway:41
```

Do not depend only on `latest`.

---

# 32. Credential separation

```text
Local AWS identity
    |
    +--> Create AWS infrastructure

Jenkins AWS identity
    |
    +--> Push Docker images to ECR

Jenkins SSH credential
    |
    +--> SSH to EC2

EC2 IAM role
    |
    +--> Pull ECR images
    +--> Read Secrets Manager

Secrets Manager
    |
    +--> OPENAI_API_KEY
    +--> TAVILY_API_KEY
```

Never use:

```text
Local AWS access key
        ↓
GitHub
        ↓
Jenkinsfile
        ↓
EC2
```

---

# 33. Final verification

## Local AWS

```bash
aws sts get-caller-identity
```

## ECR

```bash
aws ecr describe-repositories --region "${AWS_REGION}"
```

## EC2

```bash
aws ec2 describe-instances \
  --instance-ids "${INSTANCE_ID}" \
  --region "${AWS_REGION}"
```

## EC2 IAM role

```bash
ssh -i "${KEY_FILE}" "${EC2_USER}@${EC2_HOST}" \
  'aws sts get-caller-identity'
```

## EC2 Docker

```bash
ssh -i "${KEY_FILE}" "${EC2_USER}@${EC2_HOST}" \
  'docker --version && docker compose version'
```

## Gateway

```bash
curl --fail "http://${EC2_HOST}:9090/actuator/health"
```

## Knowledge Service through Gateway

```bash
curl --fail "http://${EC2_HOST}:9090/api/knowledge/health"
```

For SwiftKart, use an endpoint that actually exists in the current SwiftKart application.

---

# 34. Fresher execution checklist

```text
[ ] AWS root console bootstrap completed
[ ] swiftkart-local-admin created
[ ] Local access key created
[ ] AWS CLI installed
[ ] aws configure completed
[ ] aws sts get-caller-identity works
[ ] Deployment variables exported
[ ] 3 ECR repositories created
[ ] Secrets Manager secret created
[ ] EC2 IAM role created
[ ] EC2 instance profile created
[ ] EC2 key pair created
[ ] Security Group created
[ ] SSH restricted to current IP
[ ] TCP 9090 opened
[ ] EC2 launched
[ ] EC2_HOST automatically obtained
[ ] SSH works
[ ] EC2 IAM role works
[ ] Docker works
[ ] EC2 → ECR works
[ ] EC2 → Secrets Manager works
[ ] 3 linux/amd64 images built
[ ] 3 images pushed to ECR manually
[ ] Existing docker-compose-prod.yml copied to EC2
[ ] Docker Compose starts
[ ] Gateway health passes
[ ] Knowledge Service health passes
[ ] SwiftKart route verified
[ ] swiftkart-jenkins created
[ ] Jenkins ECR policy attached
[ ] Jenkins access key created
[ ] aws-jenkins configured
[ ] ec2-ssh-key configured
[ ] github-credentials verified
[ ] CD job created
[ ] CD Jenkinsfile pasted
[ ] CD build succeeds
[ ] ECR push succeeds
[ ] EC2 deployment succeeds
[ ] Health checks succeed
[ ] CI → CD trigger configured after manual CD is proven
```

---

# 35. Industry-standard interview explanation

> The local administrator identity is used only to provision AWS infrastructure. Jenkins has a dedicated IAM identity with the permissions required to authenticate and push images to ECR. Jenkins connects to EC2 using an SSH credential, but AWS credentials are not copied to EC2. EC2 uses an IAM instance role to pull images from ECR and read runtime secrets from Secrets Manager. The CD pipeline deploys versioned images with Docker Compose and validates the deployment through health checks.

The security boundary is:

```text
Local AWS Identity
        ≠
Jenkins AWS Identity
        ≠
EC2 IAM Role
        ≠
Application Runtime Secrets
```

---

# 36. Project-specific decisions preserved in this guide

The deployment guide preserves the project decisions already established:

- SwiftKart source remains unchanged.
- API Gateway is the public entry point.
- API Gateway runs on port 9090.
- SwiftKart runs on port 8080.
- Knowledge Service runs on port 8082.
- Docker service names are used for internal communication.
- Eureka is not required for the first deployment.
- SonarQube is part of CI, not CD.
- Runtime OpenAI/Tavily credentials are stored in Secrets Manager.
- Jenkins has separate AWS and SSH credentials.
- EC2 uses an IAM role rather than permanent AWS access keys.
- ARM Mac builds targeting x86_64 EC2 use `linux/amd64`.

The gateway and service ports are directly supported by the existing project configuration. fileciteturn23file1L144-L190

---

# 37. Golden rule

Do not debug AWS, IAM, ECR, Docker, EC2, networking and Jenkins simultaneously.

Prove each boundary in this order:

```text
AWS CLI authentication
        ↓
AWS resources
        ↓
EC2 IAM role
        ↓
EC2 → ECR
        ↓
EC2 → Secrets Manager
        ↓
Docker Compose
        ↓
Application
        ↓
Jenkins CD
```

If the manual deployment works, Jenkins CD should automate the same successful sequence rather than introducing a second deployment mechanism.

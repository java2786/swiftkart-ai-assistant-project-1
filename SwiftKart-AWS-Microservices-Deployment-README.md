# SwiftKart AI Microservices — AWS / ECR / EC2 / Jenkins Deployment Runbook

This README is the command-focused deployment guide for the current SwiftKart AI microservices setup.

It intentionally avoids hardcoding AWS account IDs, EC2 public IPs, ECR registry URLs, image tags, and other environment-specific values wherever they can be derived from AWS CLI variables.

## 1. Current Repository Structure

```text
ai-launchpad/
├── api-gateway/
├── knowledge-service/
├── swiftkart-ai-assistant/
├── cicd/
│   ├── Jenkinsfile
│   └── Jenkinsfile-ec2
├── docker-compose.yml
└── docker-compose.prod.yml
```

The three Spring applications are independent Maven projects:

```text
swiftkart-ai-assistant
knowledge-service
api-gateway
```

The existing `swiftkart-ai-assistant` application should remain unchanged.

---

# 2. Runtime Architecture

```text
                         Internet
                            |
                         :9090
                            |
                    +----------------+
                    |  API Gateway   |
                    |     :9090      |
                    +-------+--------+
                            |
                 +----------+----------+
                 |                     |
                 v                     v
       +------------------+   +--------------------+
       | SwiftKart AI     |   | Knowledge Service  |
       | Assistant :8080  |   |       :8082        |
       +--------+---------+   +---------+----------+
                |                       |
                v                       v
       ChromaDB-SwiftKart       ChromaDB-Knowledge
             :8000                    :8000
```

For the two ChromaDB instances, the normal Docker pattern is:

```text
EC2 host :8000  -> chromadb-swiftkart:8000
EC2 host :8001  -> chromadb-knowledge:8000
```

The applications themselves should use Docker service names:

```text
SwiftKart:
  http://chromadb-swiftkart:8000

Knowledge Service:
  http://chromadb-knowledge:8000
```

Do not use `localhost` for container-to-container communication.

The two ChromaDB instances are intentional because SwiftKart is being kept unchanged while Knowledge Service gets its own independent vector store.

---

# 3. Important Port Model

| Component | Container Port | Optional EC2 Host Port | Publicly Exposed? |
|---|---:|---:|---|
| API Gateway | 9090 | 9090 | Yes |
| SwiftKart | 8080 | 8080 | Prefer no |
| Knowledge Service | 8082 | 8082 | Prefer no |
| SwiftKart ChromaDB | 8000 | 8000 | Prefer no |
| Knowledge ChromaDB | 8000 | 8001 | Prefer no |

Inside Docker:

```text
api-gateway
  -> swiftkart-ai-assistant:8080
  -> knowledge-service:8082

swiftkart-ai-assistant
  -> chromadb-swiftkart:8000

knowledge-service
  -> chromadb-knowledge:8000
```

---

# 4. Required Environment Variables

The deployment commands use these variables.

## 4.1 Configure AWS variables locally

Run:

```bash
export AWS_REGION="ap-south-1"

export ACCOUNT_ID=$(aws sts get-caller-identity \
    --query Account \
    --output text)

export ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

export ECR_REPO_SWIFTKART="swiftkart-ai-assistant"
export ECR_REPO_KNOWLEDGE="swiftkart-knowledge-service"
export ECR_REPO_GATEWAY="swiftkart-api-gateway"

export SECRET_NAME="swiftkart/prod/env"

export KEY_PAIR_NAME="swiftkart-key"
export SEC_GROUP_NAME="swiftkart-app-sg"
```

Verify:

```bash
echo "AWS_REGION=${AWS_REGION}"
echo "ACCOUNT_ID=${ACCOUNT_ID}"
echo "ECR_REGISTRY=${ECR_REGISTRY}"
echo "ECR_REPO_SWIFTKART=${ECR_REPO_SWIFTKART}"
echo "ECR_REPO_KNOWLEDGE=${ECR_REPO_KNOWLEDGE}"
echo "ECR_REPO_GATEWAY=${ECR_REPO_GATEWAY}"
echo "SECRET_NAME=${SECRET_NAME}"
echo "KEY_PAIR_NAME=${KEY_PAIR_NAME}"
echo "SEC_GROUP_NAME=${SEC_GROUP_NAME}"
```

Expected ECR image names are therefore:

```text
${ACCOUNT_ID}.dkr.ecr.ap-south-1.amazonaws.com/swiftkart-ai-assistant
${ACCOUNT_ID}.dkr.ecr.ap-south-1.amazonaws.com/swiftkart-knowledge-service
${ACCOUNT_ID}.dkr.ecr.ap-south-1.amazonaws.com/swiftkart-api-gateway
```

This is the preferred format for this project.

---

# 5. Verify AWS CLI

```bash
aws --version
```

Verify credentials:

```bash
aws sts get-caller-identity
```

Verify the selected region:

```bash
aws configure get region
```

If necessary:

```bash
aws configure
```

Do not put AWS access keys into Git or project files.

---

# 6. Discover EC2 Instances

Before assigning `INSTANCE_ID`, list running instances:

```bash
aws ec2 describe-instances \
    --region "${AWS_REGION}" \
    --filters "Name=instance-state-name,Values=running" \
    --query "Reservations[].Instances[].{ID:InstanceId,Name:Tags[?Key=='Name']|[0].Value,IP:PublicIpAddress,Type:InstanceType}" \
    --output table
```

Select the SwiftKart EC2 instance.

Set its ID:

```bash
export INSTANCE_ID="<selected-instance-id>"
```

This is the only environment-specific EC2 identifier that needs to be selected manually.

Verify:

```bash
echo "INSTANCE_ID=${INSTANCE_ID}"
```

---

# 7. Derive EC2 IP — Do Not Hardcode It

Instead of:

```text
13.235.90.243
```

derive the public IP:

```bash
export EC2_IP=$(aws ec2 describe-instances \
    --region "${AWS_REGION}" \
    --instance-ids "${INSTANCE_ID}" \
    --query "Reservations[0].Instances[0].PublicIpAddress" \
    --output text)
```

Verify:

```bash
echo "EC2_IP=${EC2_IP}"
```

This is the preferred deployment approach.

If the instance has no public IP, check:

```bash
aws ec2 describe-instances \
    --region "${AWS_REGION}" \
    --instance-ids "${INSTANCE_ID}" \
    --query "Reservations[0].Instances[0].{State:State.Name,PublicIP:PublicIpAddress,PrivateIP:PrivateIpAddress}" \
    --output table
```

---

# 8. Derive EC2 Availability Zone

Useful for troubleshooting:

```bash
export EC2_AZ=$(aws ec2 describe-instances \
    --region "${AWS_REGION}" \
    --instance-ids "${INSTANCE_ID}" \
    --query "Reservations[0].Instances[0].Placement.AvailabilityZone" \
    --output text)
```

Verify:

```bash
echo "EC2_AZ=${EC2_AZ}"
```

---

# 9. Verify EC2 Instance Details

```bash
aws ec2 describe-instances \
    --region "${AWS_REGION}" \
    --instance-ids "${INSTANCE_ID}" \
    --query "Reservations[0].Instances[0].{ID:InstanceId,State:State.Name,Type:InstanceType,PublicIP:PublicIpAddress,PrivateIP:PrivateIpAddress,AZ:Placement.AvailabilityZone}" \
    --output table
```

---

# 10. Verify Key Pair

List key pairs:

```bash
aws ec2 describe-key-pairs \
    --region "${AWS_REGION}" \
    --query "KeyPairs[].KeyName" \
    --output table
```

Verify the configured key:

```bash
aws ec2 describe-key-pairs \
    --region "${AWS_REGION}" \
    --key-names "${KEY_PAIR_NAME}"
```

The private key file must remain local and must never be committed.

Example local file:

```text
swiftkart-key.pem
```

Permissions:

```bash
chmod 400 swiftkart-key.pem
```

---

# 11. Verify Security Group

Find the Security Group:

```bash
export SEC_GROUP_ID=$(aws ec2 describe-security-groups \
    --region "${AWS_REGION}" \
    --filters "Name=group-name,Values=${SEC_GROUP_NAME}" \
    --query "SecurityGroups[0].GroupId" \
    --output text)
```

Verify:

```bash
echo "SEC_GROUP_ID=${SEC_GROUP_ID}"
```

Inspect rules:

```bash
aws ec2 describe-security-groups \
    --region "${AWS_REGION}" \
    --group-ids "${SEC_GROUP_ID}" \
    --query "SecurityGroups[0].IpPermissions" \
    --output json
```

Recommended application ingress:

```text
TCP 9090
```

SSH:

```text
TCP 22
```

Prefer restricting SSH to the required Jenkins/admin source rather than allowing the entire Internet.

Do not publicly expose:

```text
8080
8082
8000
8001
```

unless temporary troubleshooting requires it.

---

# 12. Secrets Manager

The existing secret is:

```text
swiftkart/prod/env
```

Verify it:

```bash
aws secretsmanager describe-secret \
    --secret-id "${SECRET_NAME}" \
    --region "${AWS_REGION}"
```

Do not blindly run `create-secret` if the secret already exists.

If a new environment needs the secret, create it only when absent:

```bash
aws secretsmanager create-secret \
    --name "${SECRET_NAME}" \
    --description "Production environment secrets for SwiftKart AI" \
    --secret-string '{"OPENAI_API_KEY":"REPLACE_ME","TAVILY_API_KEY":"REPLACE_ME"}' \
    --region "${AWS_REGION}"
```

Never commit the real values.

If the secret already exists and needs updating:

```bash
aws secretsmanager put-secret-value \
    --secret-id "${SECRET_NAME}" \
    --secret-string '{"OPENAI_API_KEY":"REPLACE_ME","TAVILY_API_KEY":"REPLACE_ME"}' \
    --region "${AWS_REGION}"
```

Do not print production secrets in normal logs.

---

# 13. Verify ECR Repositories

List repositories:

```bash
aws ecr describe-repositories \
    --region "${AWS_REGION}" \
    --query "repositories[].repositoryName" \
    --output table
```

Verify SwiftKart:

```bash
aws ecr describe-repositories \
    --repository-names "${ECR_REPO_SWIFTKART}" \
    --region "${AWS_REGION}"
```

Verify Knowledge Service:

```bash
aws ecr describe-repositories \
    --repository-names "${ECR_REPO_KNOWLEDGE}" \
    --region "${AWS_REGION}"
```

Verify API Gateway:

```bash
aws ecr describe-repositories \
    --repository-names "${ECR_REPO_GATEWAY}" \
    --region "${AWS_REGION}"
```

If a repository does not exist, create it:

```bash
aws ecr create-repository \
    --repository-name "${ECR_REPO_SWIFTKART}" \
    --region "${AWS_REGION}" \
    --image-scanning-configuration scanOnPush=true \
    --image-tag-mutability MUTABLE
```

```bash
aws ecr create-repository \
    --repository-name "${ECR_REPO_KNOWLEDGE}" \
    --region "${AWS_REGION}" \
    --image-scanning-configuration scanOnPush=true \
    --image-tag-mutability MUTABLE
```

```bash
aws ecr create-repository \
    --repository-name "${ECR_REPO_GATEWAY}" \
    --region "${AWS_REGION}" \
    --image-scanning-configuration scanOnPush=true \
    --image-tag-mutability MUTABLE
```

---

# 14. ECR Login — Local Machine / Jenkins

Authenticate:

```bash
aws ecr get-login-password \
    --region "${AWS_REGION}" \
| docker login \
    --username AWS \
    --password-stdin "${ECR_REGISTRY}"
```

Expected:

```text
Login Succeeded
```

---

# 15. Local Maven Build

Run from:

```text
ai-launchpad/
```

SwiftKart:

```bash
cd swiftkart-ai-assistant
./mvnw -B clean verify
cd ..
```

Knowledge Service:

```bash
cd knowledge-service
./mvnw -B clean verify
cd ..
```

API Gateway:

```bash
cd api-gateway
./mvnw -B clean verify
cd ..
```

These are independent Maven projects.

Do not assume a root-level Maven build exists.

---

# 16. Local Docker Build — AMD64

If the local development machine is Apple Silicon and EC2 is AMD64, build for:

```text
linux/amd64
```

Set a test tag:

```bash
export IMAGE_TAG="local-test"
```

Build SwiftKart:

```bash
docker build \
    --platform linux/amd64 \
    -t "${ECR_REGISTRY}/${ECR_REPO_SWIFTKART}:${IMAGE_TAG}" \
    ./swiftkart-ai-assistant
```

Build Knowledge Service:

```bash
docker build \
    --platform linux/amd64 \
    -t "${ECR_REGISTRY}/${ECR_REPO_KNOWLEDGE}:${IMAGE_TAG}" \
    ./knowledge-service
```

Build API Gateway:

```bash
docker build \
    --platform linux/amd64 \
    -t "${ECR_REGISTRY}/${ECR_REPO_GATEWAY}:${IMAGE_TAG}" \
    ./api-gateway
```

Verify architecture:

```bash
docker image inspect \
    "${ECR_REGISTRY}/${ECR_REPO_SWIFTKART}:${IMAGE_TAG}" \
    --format '{{.Os}}/{{.Architecture}}'
```

```bash
docker image inspect \
    "${ECR_REGISTRY}/${ECR_REPO_KNOWLEDGE}:${IMAGE_TAG}" \
    --format '{{.Os}}/{{.Architecture}}'
```

```bash
docker image inspect \
    "${ECR_REGISTRY}/${ECR_REPO_GATEWAY}:${IMAGE_TAG}" \
    --format '{{.Os}}/{{.Architecture}}'
```

Expected:

```text
linux/amd64
```

---

# 17. Push Test Images to ECR

Only do this when a test image needs to be uploaded.

```bash
docker push \
    "${ECR_REGISTRY}/${ECR_REPO_SWIFTKART}:${IMAGE_TAG}"
```

```bash
docker push \
    "${ECR_REGISTRY}/${ECR_REPO_KNOWLEDGE}:${IMAGE_TAG}"
```

```bash
docker push \
    "${ECR_REGISTRY}/${ECR_REPO_GATEWAY}:${IMAGE_TAG}"
```

List images:

```bash
aws ecr list-images \
    --repository-name "${ECR_REPO_SWIFTKART}" \
    --region "${AWS_REGION}"
```

---

# 18. SSH to EC2 Without Hardcoding the IP

After deriving `EC2_IP`:

```bash
ssh -i swiftkart-key.pem ec2-user@"${EC2_IP}"
```

Equivalent form:

```bash
ssh -i swiftkart-key.pem "ec2-user@${EC2_IP}"
```

Do not use:

```text
ssh -i swiftkart-key.pem ec2-user@13.235.90.243
```

in the deployment script.

---

# 19. EC2 IAM Role Verification

On EC2:

```bash
aws sts get-caller-identity
```

The result should identify the EC2 IAM role rather than a static access-key user.

Verify ECR access:

```bash
aws ecr describe-repositories \
    --region "${AWS_REGION}" \
    --query "repositories[].repositoryName" \
    --output table
```

Verify Secrets Manager access:

```bash
aws secretsmanager describe-secret \
    --secret-id "${SECRET_NAME}" \
    --region "${AWS_REGION}"
```

The EC2 role should have permission to:

```text
ECR pull
Secrets Manager GetSecretValue
```

Do not run `aws configure` on EC2 for production credentials.

---

# 20. EC2 ECR Login

On EC2:

```bash
export AWS_REGION="ap-south-1"

export ACCOUNT_ID=$(aws sts get-caller-identity \
    --query Account \
    --output text)

export ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

aws ecr get-login-password \
    --region "${AWS_REGION}" \
| docker login \
    --username AWS \
    --password-stdin "${ECR_REGISTRY}"
```

---

# 21. EC2 Pull the Three Images

Set the deployment tag:

```bash
export IMAGE_TAG="<jenkins-build-number>"
```

Pull SwiftKart:

```bash
docker pull \
    "${ECR_REGISTRY}/${ECR_REPO_SWIFTKART}:${IMAGE_TAG}"
```

Pull Knowledge Service:

```bash
docker pull \
    "${ECR_REGISTRY}/${ECR_REPO_KNOWLEDGE}:${IMAGE_TAG}"
```

Pull API Gateway:

```bash
docker pull \
    "${ECR_REGISTRY}/${ECR_REPO_GATEWAY}:${IMAGE_TAG}"
```

Verify:

```bash
docker images
```

---

# 22. EC2 Production Directory

Recommended:

```text
/opt/swiftkart
```

Create:

```bash
sudo mkdir -p /opt/swiftkart
sudo chown ec2-user:ec2-user /opt/swiftkart
```

Then:

```bash
cd /opt/swiftkart
```

The production Compose file should be:

```text
/opt/swiftkart/docker-compose.prod.yml
```

---

# 23. Production Compose — Two ChromaDB Instances

The production Compose architecture should represent the two independent vector stores.

Conceptually:

```yaml
services:

  chromadb-swiftkart:
    image: chromadb/chroma:latest
    # optional host mapping:
    # ports:
    #   - "8000:8000"

  chromadb-knowledge:
    image: chromadb/chroma:latest
    # optional host mapping:
    # ports:
    #   - "8001:8000"

  swiftkart-ai-assistant:
    environment:
      CHROMA_HOST: http://chromadb-swiftkart
      CHROMA_PORT: 8000

  knowledge-service:
    environment:
      CHROMA_HOST: http://chromadb-knowledge
      CHROMA_PORT: 8000

  api-gateway:
    environment:
      SWIFTKART_URL: http://swiftkart-ai-assistant:8080
      KNOWLEDGE_SERVICE_URL: http://knowledge-service:8082
```

The exact ChromaDB image/version and application properties must match the tested implementation.

Do not expose ChromaDB publicly just because host mappings exist.

---

# 24. Knowledge Service Local Configuration

Current `knowledge-service/src/main/resources/application.yml` uses:

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
      embedding:
        options:
          model: text-embedding-3-small

    vectorstore:
      chroma:
        client:
          host: ${CHROMA_HOST:http://localhost}
          port: ${CHROMA_PORT:8001}
        collection-name: ${CHROMA_COLLECTION:knowledge-store}
        initialize-schema: true
```

This gives a useful local default:

```text
localhost:8001
```

For Docker, override it:

```text
CHROMA_HOST=http://chromadb-knowledge
CHROMA_PORT=8000
```

This distinction is important:

```text
Local machine:
  localhost:8001

Docker:
  chromadb-knowledge:8000
```

Do not change the Knowledge Service from OpenAI to Ollama for this deployment.

---

# 25. SwiftKart ChromaDB

Keep the existing SwiftKart ChromaDB configuration unchanged.

For Docker, if its ChromaDB endpoint is configurable through environment variables, use:

```text
CHROMA_HOST=http://chromadb-swiftkart
CHROMA_PORT=8000
```

Do not change SwiftKart source code merely to accommodate the new microservice architecture.

---

# 26. API Gateway Runtime Configuration

Use:

```text
PORT=9090
SWIFTKART_URL=http://swiftkart-ai-assistant:8080
KNOWLEDGE_SERVICE_URL=http://knowledge-service:8082
```

Expected routes:

```text
/api/agent/**       → SwiftKart
/api/knowledge/**   → Knowledge Service
```

The Gateway should contain no business logic.

---

# 27. Production Secret Injection

The production applications require:

```text
OPENAI_API_KEY
TAVILY_API_KEY
```

Retrieve the secret on EC2:

```bash
export SECRET_JSON=$(aws secretsmanager get-secret-value \
    --secret-id "${SECRET_NAME}" \
    --region "${AWS_REGION}" \
    --query SecretString \
    --output text)
```

Extract without echoing:

```bash
export OPENAI_API_KEY=$(echo "${SECRET_JSON}" | jq -r '.OPENAI_API_KEY')
```

```bash
export TAVILY_API_KEY=$(echo "${SECRET_JSON}" | jq -r '.TAVILY_API_KEY')
```

Do not run:

```bash
echo "${OPENAI_API_KEY}"
echo "${TAVILY_API_KEY}"
```

Remove the JSON variable after use:

```bash
unset SECRET_JSON
```

If your Compose/deployment mechanism passes these variables to containers, use environment expansion rather than writing the secret values into Git-tracked files.

---

# 28. Verify `jq` on EC2

The secret extraction commands require `jq`.

Check:

```bash
jq --version
```

If required on Amazon Linux:

```bash
sudo dnf install -y jq
```

Verify again:

```bash
jq --version
```

---

# 29. Start Production Compose

From:

```text
/opt/swiftkart
```

Set:

```bash
export IMAGE_TAG="<jenkins-build-number>"
```

Then:

```bash
docker compose \
    -f docker-compose.prod.yml \
    up -d
```

Check:

```bash
docker compose \
    -f docker-compose.prod.yml \
    ps
```

Expected services:

```text
api-gateway
swiftkart-ai-assistant
knowledge-service
chromadb-swiftkart
chromadb-knowledge
```

---

# 30. Check Docker Network

```bash
docker network ls
```

Inspect the application network:

```bash
docker network inspect <network-name>
```

Verify that these containers are on the same network where appropriate:

```text
api-gateway
swiftkart-ai-assistant
knowledge-service
chromadb-swiftkart
chromadb-knowledge
```

---

# 31. Container Logs

All services:

```bash
docker compose \
    -f docker-compose.prod.yml \
    logs --tail=100
```

API Gateway:

```bash
docker compose \
    -f docker-compose.prod.yml \
    logs --tail=100 api-gateway
```

Knowledge Service:

```bash
docker compose \
    -f docker-compose.prod.yml \
    logs --tail=100 knowledge-service
```

SwiftKart:

```bash
docker compose \
    -f docker-compose.prod.yml \
    logs --tail=100 swiftkart-ai-assistant
```

SwiftKart ChromaDB:

```bash
docker compose \
    -f docker-compose.prod.yml \
    logs --tail=100 chromadb-swiftkart
```

Knowledge ChromaDB:

```bash
docker compose \
    -f docker-compose.prod.yml \
    logs --tail=100 chromadb-knowledge
```

Do not expose secret values in logs.

---

# 32. Health Checks

Gateway:

```bash
curl --fail \
    http://localhost:9090/actuator/health
```

Knowledge Service:

```bash
curl --fail \
    http://localhost:8082/api/knowledge/health
```

Use the existing SwiftKart health/API endpoint for SwiftKart.

---

# 33. Gateway → Knowledge Service

From EC2:

```bash
curl --fail \
    http://localhost:9090/api/knowledge/health
```

Expected:

```json
{
  "service": "knowledge-service",
  "status": "UP"
}
```

This verifies:

```text
EC2
 |
 v
Gateway :9090
 |
 v
Knowledge Service :8082
```

---

# 34. Gateway → SwiftKart

Use an existing SwiftKart endpoint through the Gateway.

Expected route prefix:

```text
/api/agent/**
```

For example, if the existing endpoint is:

```text
POST /api/agent/chat
```

the Gateway should forward it to:

```text
http://swiftkart-ai-assistant:8080/api/agent/chat
```

Do not modify SwiftKart merely to change the route.

---

# 35. Knowledge Service → ChromaDB

From inside the Knowledge Service container, the expected target is:

```text
http://chromadb-knowledge:8000
```

Not:

```text
http://localhost:8001
```

Check the ChromaDB container:

```bash
docker compose \
    -f docker-compose.prod.yml \
    ps chromadb-knowledge
```

Check its logs:

```bash
docker compose \
    -f docker-compose.prod.yml \
    logs --tail=100 chromadb-knowledge
```

---

# 36. SwiftKart → Its ChromaDB

The existing SwiftKart application should connect to:

```text
http://chromadb-swiftkart:8000
```

This is separate from:

```text
http://chromadb-knowledge:8000
```

The host ports, if exposed for troubleshooting, are:

```text
8000 → SwiftKart ChromaDB
8001 → Knowledge ChromaDB
```

Again, application containers should normally use the Docker service names and internal port `8000`.

---

# 37. RAG Smoke Test

Add a document through the Gateway:

```bash
curl -X POST \
    http://localhost:9090/api/knowledge/chroma/documents \
    -H "Content-Type: application/json" \
    -d '{"content":"Wireless headphones with active noise cancellation"}'
```

Search:

```bash
curl \
    "http://localhost:9090/api/knowledge/chroma/search?query=headphones"
```

Expected flow:

```text
Gateway
   |
   v
Knowledge Service
   |
   +--> OpenAI Embeddings
   |
   v
Knowledge ChromaDB
```

---

# 38. Tool Chain Smoke Test

```bash
curl \
    "http://localhost:9090/api/knowledge/tools/chain?product=iphone"
```

Expected sequence:

```text
ProductSearchTool
        |
        v
ProductDetailsTool
        |
        v
InventoryTool
```

---

# 39. Test From Your Local Machine

After EC2 internal tests pass:

```bash
curl --fail \
    "http://${EC2_IP}:9090/api/knowledge/health"
```

Gateway health:

```bash
curl --fail \
    "http://${EC2_IP}:9090/actuator/health"
```

Existing SwiftKart endpoint:

```text
http://${EC2_IP}:9090/api/agent/...
```

Use the actual existing request/method required by SwiftKart.

---

# 40. Jenkins CI

Jenkins job:

```text
swiftkart-pipeline
```

CI flow:

```text
Checkout
   |
   +--> SwiftKart Maven Build/Test
   |
   +--> Knowledge Service Maven Build/Test
   |
   +--> API Gateway Maven Build/Test
   |
   +--> JaCoCo
   |
   +--> SonarQube
   |
   +--> Quality Gate
```

The CI job must build the three independent Maven projects separately.

Example:

```groovy
stage('Build & Test') {
    steps {
        sh '''
            set -e

            cd swiftkart-ai-assistant
            ./mvnw -B clean verify
            cd ..

            cd knowledge-service
            ./mvnw -B clean verify
            cd ..

            cd api-gateway
            ./mvnw -B clean verify
            cd ..
        '''
    }
}
```

---

# 41. Jenkins CD

Jenkins job:

```text
swiftkart-deploy-ec2
```

CD flow:

```text
Checkout
   |
Docker Build × 3
   |
ECR Login
   |
ECR Push × 3
   |
SSH to EC2
   |
EC2 ECR Login
   |
EC2 pulls images
   |
Secrets Manager
   |
Docker Compose
   |
Health Check
```

Use:

```text
BUILD_TAG_REF
```

for the immutable deployment image tag.

Example:

```text
BUILD_TAG_REF=25
```

Images:

```text
${ACCOUNT_ID}.dkr.ecr.ap-south-1.amazonaws.com/swiftkart-ai-assistant:25
${ACCOUNT_ID}.dkr.ecr.ap-south-1.amazonaws.com/swiftkart-knowledge-service:25
${ACCOUNT_ID}.dkr.ecr.ap-south-1.amazonaws.com/swiftkart-api-gateway:25
```

---

# 42. Jenkins ECR Push

Jenkins should define:

```bash
export AWS_REGION="ap-south-1"

export ACCOUNT_ID=$(aws sts get-caller-identity \
    --query Account \
    --output text)

export ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
```

Login:

```bash
aws ecr get-login-password \
    --region "${AWS_REGION}" \
| docker login \
    --username AWS \
    --password-stdin "${ECR_REGISTRY}"
```

Push:

```bash
docker push \
    "${ECR_REGISTRY}/swiftkart-ai-assistant:${BUILD_TAG_REF}"
```

```bash
docker push \
    "${ECR_REGISTRY}/swiftkart-knowledge-service:${BUILD_TAG_REF}"
```

```bash
docker push \
    "${ECR_REGISTRY}/swiftkart-api-gateway:${BUILD_TAG_REF}"
```

---

# 43. Jenkins Docker Build

```bash
docker build \
    --platform linux/amd64 \
    -t "${ECR_REGISTRY}/swiftkart-ai-assistant:${BUILD_TAG_REF}" \
    ./swiftkart-ai-assistant
```

```bash
docker build \
    --platform linux/amd64 \
    -t "${ECR_REGISTRY}/swiftkart-knowledge-service:${BUILD_TAG_REF}" \
    ./knowledge-service
```

```bash
docker build \
    --platform linux/amd64 \
    -t "${ECR_REGISTRY}/swiftkart-api-gateway:${BUILD_TAG_REF}" \
    ./api-gateway
```

---

# 44. Jenkins SSH Deployment

Prefer a heredoc:

```bash
ssh \
    -o StrictHostKeyChecking=no \
    -i "${SSH_KEY}" \
    "${SSH_USER}@${EC2_IP}" \
    'bash -s' <<'REMOTE_SCRIPT'

set -e

# EC2 deployment commands go here.

REMOTE_SCRIPT
```

Do not hardcode:

```text
13.235.90.243
```

Instead:

```text
${EC2_IP}
```

---

# 45. Jenkins EC2 Variables

Before the SSH step:

```bash
export AWS_REGION="ap-south-1"

export ACCOUNT_ID=$(aws sts get-caller-identity \
    --query Account \
    --output text)

export ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
```

The EC2 IP should be derived using:

```bash
export EC2_IP=$(aws ec2 describe-instances \
    --region "${AWS_REGION}" \
    --instance-ids "${INSTANCE_ID}" \
    --query "Reservations[0].Instances[0].PublicIpAddress" \
    --output text)
```

Jenkins should normally receive `INSTANCE_ID` or another stable EC2 identifier as configuration rather than hardcoding the public IP.

---

# 46. EC2 Deployment Script

A deployment script on EC2 should conceptually perform:

```bash
set -e

AWS_REGION="ap-south-1"

ACCOUNT_ID=$(aws sts get-caller-identity \
    --query Account \
    --output text)

ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

IMAGE_TAG="${BUILD_TAG_REF}"

aws ecr get-login-password \
    --region "${AWS_REGION}" \
| docker login \
    --username AWS \
    --password-stdin "${ECR_REGISTRY}"

docker pull \
    "${ECR_REGISTRY}/swiftkart-ai-assistant:${IMAGE_TAG}"

docker pull \
    "${ECR_REGISTRY}/swiftkart-knowledge-service:${IMAGE_TAG}"

docker pull \
    "${ECR_REGISTRY}/swiftkart-api-gateway:${IMAGE_TAG}"
```

Then:

```bash
cd /opt/swiftkart

export IMAGE_TAG="${BUILD_TAG_REF}"

docker compose \
    -f docker-compose.prod.yml \
    up -d
```

---

# 47. EC2 Health Check With Retry

Gateway health:

```bash
for i in $(seq 1 12); do
    if curl --fail --silent --show-error \
        http://localhost:9090/actuator/health; then

        echo
        echo "Gateway health check passed."
        exit 0
    fi

    echo "Gateway not ready. Retrying in 5 seconds..."
    sleep 5
done

echo "Gateway health check failed."
exit 1
```

Knowledge Service:

```bash
for i in $(seq 1 12); do
    if curl --fail --silent --show-error \
        http://localhost:8082/api/knowledge/health; then

        echo
        echo "Knowledge Service health check passed."
        exit 0
    fi

    echo "Knowledge Service not ready. Retrying in 5 seconds..."
    sleep 5
done

echo "Knowledge Service health check failed."
exit 1
```

For the final CD pipeline, prefer checking the Gateway route as well:

```bash
curl --fail \
    http://localhost:9090/api/knowledge/health
```

---

# 48. CI → CD Trigger

After CI succeeds:

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

Do not configure the same CI → CD relationship in two different Jenkins mechanisms.

---

# 49. Jenkins Credentials

Expected Jenkins credentials:

```text
github-credentials
aws-jenkins
ec2-ssh-key
```

Use:

```text
github-credentials → GitHub checkout
aws-jenkins        → ECR push
ec2-ssh-key        → EC2 SSH
```

The EC2 machine uses its IAM role rather than Jenkins credentials.

---

# 50. Jenkins CD Checkout

If `swiftkart-deploy-ec2` is a Pipeline job using an inline Pipeline script, use an explicit Git checkout:

```groovy
stage('Checkout') {
    steps {
        git branch: 'main',
            credentialsId: 'github-credentials',
            url: 'https://github.com/java2786/swiftkart-ai-assistant-project-1'
    }
}
```

Do not use:

```groovy
checkout scm
```

unless the job itself is configured as Pipeline from SCM.

---

# 51. Rollback

If:

```text
BUILD_TAG_REF=25
```

is bad and `24` is known good:

```bash
export IMAGE_TAG="24"

docker compose \
    -f docker-compose.prod.yml \
    up -d
```

Check:

```bash
docker compose \
    -f docker-compose.prod.yml \
    ps
```

Then health check:

```bash
curl --fail \
    http://localhost:9090/actuator/health
```

---

# 52. Clean Up Old Images

First inspect:

```bash
docker images
```

Remove an unused local image:

```bash
docker image rm <image-id>
```

Remove dangling images:

```bash
docker image prune
```

Be careful with:

```bash
docker system prune
```

because it can remove more resources than intended.

---

# 53. Restart the Complete Stack

```bash
docker compose \
    -f docker-compose.prod.yml \
    restart
```

Recreate:

```bash
docker compose \
    -f docker-compose.prod.yml \
    up -d \
    --force-recreate
```

Remove orphan containers:

```bash
docker compose \
    -f docker-compose.prod.yml \
    up -d \
    --remove-orphans
```

Stop:

```bash
docker compose \
    -f docker-compose.prod.yml \
    down
```

Start:

```bash
docker compose \
    -f docker-compose.prod.yml \
    up -d
```

---

# 54. Useful AWS Verification Commands

## Current AWS identity

```bash
aws sts get-caller-identity
```

## Current region

```bash
aws configure get region
```

## Current EC2 IP

```bash
aws ec2 describe-instances \
    --region "${AWS_REGION}" \
    --instance-ids "${INSTANCE_ID}" \
    --query "Reservations[0].Instances[0].PublicIpAddress" \
    --output text
```

## EC2 state

```bash
aws ec2 describe-instances \
    --region "${AWS_REGION}" \
    --instance-ids "${INSTANCE_ID}" \
    --query "Reservations[0].Instances[0].State.Name" \
    --output text
```

## ECR repositories

```bash
aws ecr describe-repositories \
    --region "${AWS_REGION}" \
    --query "repositories[].repositoryName" \
    --output table
```

## ECR image tags

```bash
aws ecr describe-images \
    --repository-name "${ECR_REPO_SWIFTKART}" \
    --region "${AWS_REGION}" \
    --query "imageDetails[].imageTags" \
    --output table
```

## Secret metadata

```bash
aws secretsmanager describe-secret \
    --secret-id "${SECRET_NAME}" \
    --region "${AWS_REGION}"
```

---

# 55. Useful EC2 Verification Commands

```bash
docker ps
```

```bash
docker ps -a
```

```bash
docker images
```

```bash
docker network ls
```

```bash
docker volume ls
```

```bash
docker stats
```

```bash
df -h
```

```bash
free -h
```

```bash
uname -m
```

Expected EC2 architecture:

```text
x86_64
```

---

# 56. Troubleshooting

## Gateway cannot reach Knowledge Service

Check:

```bash
docker compose \
    -f docker-compose.prod.yml \
    ps
```

Then:

```bash
docker compose \
    -f docker-compose.prod.yml \
    logs --tail=100 knowledge-service
```

Verify:

```text
KNOWLEDGE_SERVICE_URL=http://knowledge-service:8082
```

---

## Knowledge Service cannot reach ChromaDB

Check:

```bash
docker compose \
    -f docker-compose.prod.yml \
    ps chromadb-knowledge
```

Verify:

```text
CHROMA_HOST=http://chromadb-knowledge
CHROMA_PORT=8000
```

Do not use:

```text
localhost:8001
```

inside the Knowledge Service container.

---

## SwiftKart cannot reach its ChromaDB

Verify the existing SwiftKart configuration points to:

```text
chromadb-swiftkart:8000
```

Do not change SwiftKart source code unless required by its existing implementation.

---

## ECR pull fails on EC2

Verify IAM:

```bash
aws sts get-caller-identity
```

Verify ECR login:

```bash
aws ecr get-login-password \
    --region "${AWS_REGION}" \
| docker login \
    --username AWS \
    --password-stdin "${ECR_REGISTRY}"
```

Try:

```bash
docker pull \
    "${ECR_REGISTRY}/${ECR_REPO_KNOWLEDGE}:${IMAGE_TAG}"
```

---

## OpenAI authentication fails

Verify that the container receives:

```text
OPENAI_API_KEY
```

Do not print the actual value.

Verify the Secrets Manager secret metadata:

```bash
aws secretsmanager describe-secret \
    --secret-id "${SECRET_NAME}" \
    --region "${AWS_REGION}"
```

---

## Docker image fails on EC2

Verify:

```bash
docker image inspect <image> \
    --format '{{.Os}}/{{.Architecture}}'
```

Expected:

```text
linux/amd64
```

---

## Jenkins cannot SSH to EC2

Verify:

```text
ec2-ssh-key
```

and:

```text
TCP 22
```

in the EC2 Security Group.

Also verify the current EC2 IP:

```bash
echo "${EC2_IP}"
```

---

# 57. Final Deployment Sequence

Use this as the short operational sequence.

## Local machine

```bash
cd /path/to/ai-launchpad

export AWS_REGION="ap-south-1"

export ACCOUNT_ID=$(aws sts get-caller-identity \
    --query Account \
    --output text)

export ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

export ECR_REPO_SWIFTKART="swiftkart-ai-assistant"
export ECR_REPO_KNOWLEDGE="swiftkart-knowledge-service"
export ECR_REPO_GATEWAY="swiftkart-api-gateway"

export SECRET_NAME="swiftkart/prod/env"
export KEY_PAIR_NAME="swiftkart-key"
export SEC_GROUP_NAME="swiftkart-app-sg"
```

Discover EC2:

```bash
aws ec2 describe-instances \
    --region "${AWS_REGION}" \
    --filters "Name=instance-state-name,Values=running" \
    --query "Reservations[].Instances[].{ID:InstanceId,Name:Tags[?Key=='Name']|[0].Value,IP:PublicIpAddress,Type:InstanceType}" \
    --output table
```

Select:

```bash
export INSTANCE_ID="<selected-instance-id>"
```

Derive IP:

```bash
export EC2_IP=$(aws ec2 describe-instances \
    --region "${AWS_REGION}" \
    --instance-ids "${INSTANCE_ID}" \
    --query "Reservations[0].Instances[0].PublicIpAddress" \
    --output text)
```

Build/test Maven:

```bash
cd swiftkart-ai-assistant && ./mvnw -B clean verify && cd ..
cd knowledge-service && ./mvnw -B clean verify && cd ..
cd api-gateway && ./mvnw -B clean verify && cd ..
```

ECR login:

```bash
aws ecr get-login-password \
    --region "${AWS_REGION}" \
| docker login \
    --username AWS \
    --password-stdin "${ECR_REGISTRY}"
```

Build AMD64 images:

```bash
docker build --platform linux/amd64 \
    -t "${ECR_REGISTRY}/${ECR_REPO_SWIFTKART}:${IMAGE_TAG}" \
    ./swiftkart-ai-assistant

docker build --platform linux/amd64 \
    -t "${ECR_REGISTRY}/${ECR_REPO_KNOWLEDGE}:${IMAGE_TAG}" \
    ./knowledge-service

docker build --platform linux/amd64 \
    -t "${ECR_REGISTRY}/${ECR_REPO_GATEWAY}:${IMAGE_TAG}" \
    ./api-gateway
```

Push:

```bash
docker push "${ECR_REGISTRY}/${ECR_REPO_SWIFTKART}:${IMAGE_TAG}"
docker push "${ECR_REGISTRY}/${ECR_REPO_KNOWLEDGE}:${IMAGE_TAG}"
docker push "${ECR_REGISTRY}/${ECR_REPO_GATEWAY}:${IMAGE_TAG}"
```

SSH:

```bash
ssh -i swiftkart-key.pem "ec2-user@${EC2_IP}"
```

## EC2

```bash
cd /opt/swiftkart

export AWS_REGION="ap-south-1"

export ACCOUNT_ID=$(aws sts get-caller-identity \
    --query Account \
    --output text)

export ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

export IMAGE_TAG="<deployment-tag>"
```

ECR login:

```bash
aws ecr get-login-password \
    --region "${AWS_REGION}" \
| docker login \
    --username AWS \
    --password-stdin "${ECR_REGISTRY}"
```

Pull:

```bash
docker pull "${ECR_REGISTRY}/swiftkart-ai-assistant:${IMAGE_TAG}"
docker pull "${ECR_REGISTRY}/swiftkart-knowledge-service:${IMAGE_TAG}"
docker pull "${ECR_REGISTRY}/swiftkart-api-gateway:${IMAGE_TAG}"
```

Deploy:

```bash
docker compose \
    -f docker-compose.prod.yml \
    up -d
```

Verify:

```bash
docker compose \
    -f docker-compose.prod.yml \
    ps
```

Health:

```bash
curl --fail \
    http://localhost:9090/actuator/health
```

```bash
curl --fail \
    http://localhost:9090/api/knowledge/health
```

---

# 58. Final Architecture Checklist

```text
[ ] Existing SwiftKart source unchanged
[ ] knowledge-service uses OpenAI
[ ] knowledge-service uses its own ChromaDB
[ ] SwiftKart uses its existing/own ChromaDB
[ ] Two ChromaDB containers are configured
[ ] Local Knowledge Service can use localhost:8001
[ ] Docker Knowledge Service uses chromadb-knowledge:8000
[ ] Docker SwiftKart uses chromadb-swiftkart:8000
[ ] API Gateway runs on 9090
[ ] SwiftKart runs internally on 8080
[ ] Knowledge Service runs internally on 8082
[ ] Only Gateway is normally public
[ ] Three ECR repositories exist
[ ] EC2 IAM role can pull ECR
[ ] EC2 IAM role can read Secrets Manager
[ ] Jenkins can push ECR
[ ] Jenkins can SSH to EC2
[ ] Images are linux/amd64
[ ] Build-specific image tags are used
[ ] Docker Compose runs all required containers
[ ] Gateway → Knowledge Service works
[ ] Gateway → SwiftKart works
[ ] Knowledge Service → ChromaDB works
[ ] SwiftKart → its ChromaDB works
[ ] RAG search works
[ ] Tool chaining works
[ ] Health checks pass
[ ] No secrets are committed
[ ] No production IP/account ID is hardcoded in deployment scripts
```

# 59. Interview Architecture Statement

> The existing SwiftKart AI Assistant remains an independent Spring Boot application. I added a separate Knowledge Service using Spring AI and OpenAI embeddings, with its own ChromaDB-backed RAG implementation and a deterministic tool-chaining demonstration. An independent Spring Cloud API Gateway provides the external entry point and routes requests to the existing SwiftKart application and the new Knowledge Service. Both applications retain independent vector-store instances so the existing SwiftKart implementation does not need to be changed. Jenkins handles CI with Maven, tests, JaCoCo, SonarQube and Quality Gate. CD builds Linux AMD64 Docker images, pushes them to Amazon ECR, connects to EC2 using SSH, uses the EC2 IAM role for ECR and Secrets Manager access, deploys the Docker Compose stack, and performs health checks.

Eureka and PGVector are intentionally not claimed as implemented components in this deployment.

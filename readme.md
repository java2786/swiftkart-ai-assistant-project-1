


> docker compose -f docker-compose-cicd.yml up -d
> docker compose -f docker-compose-cicd.yml down

> docker compose -f docker-compose-cicd.yml ps
> docker compose -f docker-compose-cicd.yml logs -f sonarqube



## Quick Reference Commands  

| Task | Command |
| :--- | :--- |
| Start Stack | `docker compose -f docker-compose-cicd.yml up -d` |
| Check Status | `docker compose -f docker-compose-cicd.yml ps` |
| View Live Logs | `docker compose -f docker-compose-cicd.yml logs -f sonarqube` |
| View Live Logs | `docker compose -f docker-compose-cicd.yml logs -f jenkins` |
| Stop Stack (Saves Data) | `docker compose -f docker-compose-cicd.yml stop` |
| Shut Down Stack (Saves Volumes) | `docker compose -f docker-compose-cicd.yml down` |


## Create docker image
```bash
docker build -t swiftkart-ai-assistant:test .
docker build --no-cache -t swiftkart-ai-assistant:test .
```
## run container
```bash
docker run -d \
  --name swiftkart-ai-assistant-test \
  -p 8080:8080 \
  --env-file .env \
  swiftkart-ai-assistant:test
```

```bash
docker compose --env-file .env --env-file .env.aws -f docker-compose-cicd.yml up -d
docker compose -f docker-compose-cicd.yml down

docker compose -f docker-compose-cicd.yml build jenkins

docker compose \
  --env-file .env \
  --env-file .env.aws \
  -f docker-compose-cicd.yml \
  ps

docker logs swiftkart-sonarqube --tail 100

docker logs swiftkart-postgres --tail 50
```

```bash
export AWS_REGION="ap-south-1"
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity \
    --query Account \
    --output text)
export ECR_REPO_NAME="swiftkart-ai-assistant"
export SECRET_NAME="swiftkart/prod/env"
export KEY_PAIR_NAME="swiftkart-key"
export SEC_GROUP_NAME="swiftkart-app-sg"

PUBLIC_IP=$(aws ec2 describe-instances \
    --instance-ids ${INSTANCE_ID} \
    --query "Reservations[0].Instances[0].PublicIpAddress" \
    --output text)

echo "EC2 IP: ${PUBLIC_IP}"

echo $AWS_REGION
echo $AWS_ACCOUNT_ID
echo $ECR_REPO_NAME
echo $SECRET_NAME

```

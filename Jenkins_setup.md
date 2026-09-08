# Jenkins + SonarQube CI Setup — SwiftKart Microservices

This is the final CI setup for the current SwiftKart project.

It supports these independent Maven projects:

- `swiftkart-ai-assistant`
- `knowledge-service`
- `api-gateway`

The CI infrastructure consists of:

```text
Jenkins
   |
   +---- Maven Build/Test
   |
   +---- JaCoCo
   |
   +---- SonarQube
             |
          PostgreSQL
```

ChromaDB, Eureka, API Gateway runtime, and application deployment infrastructure are **not part of this Jenkins/SonarQube prerequisite stack**.

---

# 1. Final Architecture

```text
                    Local Docker
                         |
        +----------------+----------------+
        |                |                |
        v                v                v
     Jenkins         SonarQube        PostgreSQL
       :8081             :9000
        |
        +-----------------------------+
        |                             |
        v                             v
 SwiftKart CI                  Microservice CI
        |                             |
        v                             +--> knowledge-service
 SonarQube                            |
                                      +--> api-gateway
```

Each application is analyzed as a separate SonarQube project.

| Application | SonarQube Project Key |
|---|---|
| SwiftKart AI Assistant | `swiftkart-ai-assistant` |
| Knowledge Service | `swiftkart-knowledge-service` |
| API Gateway | `swiftkart-api-gateway` |

---

# 2. Prerequisites

Install:

- Docker Desktop
- Git
- AWS CLI only if AWS deployment is also being configured
- GitHub repository access

Verify:

```bash
docker --version
docker compose version
git --version
```

---

# 3. Recommended Repository Structure

Each application should be an independent Maven project.

Example:

```text
swiftkart-ai-assistant-project-1/
│
├── swiftkart-ai-assistant/
│   ├── pom.xml
│   ├── mvnw
│   ├── mvnw.cmd
│   ├── Jenkinsfile
│   └── src/
│
├── knowledge-service/
│   ├── pom.xml
│   ├── mvnw
│   ├── mvnw.cmd
│   ├── Jenkinsfile
│   └── src/
│
└── api-gateway/
    ├── pom.xml
    ├── mvnw
    ├── mvnw.cmd
    ├── Jenkinsfile
    └── src/
```

If the three applications are in separate Git repositories, create one Jenkins pipeline job per repository using the same CI pattern.

---

# 4. Create `.env`

Create `.env` beside `docker-compose-setup.yml`:

```properties
JENKINS_HOST_PORT=8081
SONAR_HOST_PORT=9000

POSTGRES_USER=sonar
POSTGRES_PASSWORD=sonar_password
POSTGRES_DB=sonarqube
```

Do not commit `.env` to GitHub.

Add this to `.gitignore`:

```text
.env
```

---

# 5. Docker Compose — Jenkins + SonarQube + PostgreSQL

Create:

```text
docker-compose-setup.yml
```

Use:

```yaml
services:

  jenkins:

    build:
      context: ./jenkins-docker
      dockerfile: Dockerfile

    container_name: swiftkart-jenkins-microservice

    group_add:
      - "0"

    ports:
      - "${JENKINS_HOST_PORT}:8080"
      - "50000:50000"

    volumes:
      - jenkins_home:/var/jenkins_home
      - /var/run/docker.sock:/var/run/docker.sock

    networks:
      - cicd-net


  sonarqube:

    image: sonarqube:community

    container_name: swiftkart-sonarqube-microservice

    ports:
      - "${SONAR_HOST_PORT}:9000"

    environment:
      SONAR_JDBC_USERNAME: ${POSTGRES_USER}
      SONAR_JDBC_PASSWORD: ${POSTGRES_PASSWORD}
      SONAR_JDBC_URL: jdbc:postgresql://db:5432/${POSTGRES_DB}

    depends_on:
      - db

    networks:
      - cicd-net


  db:

    image: postgres:15-alpine

    container_name: swiftkart-postgres-microservice

    environment:
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_DB: ${POSTGRES_DB}

    volumes:
      - postgres_data:/var/lib/postgresql/data

    networks:
      - cicd-net


networks:

  cicd-net:
    driver: bridge


volumes:

  jenkins_home:

  postgres_data:
```

Start:

```bash
docker compose -f docker-compose-setup.yml up -d --build
```

Check:

```bash
docker compose -f docker-compose-setup.yml ps
```

Expected containers:

```text
swiftkart-jenkins-microservice
swiftkart-sonarqube-microservice
swiftkart-postgres-microservice
```

---

# 6. Jenkins Docker Image

Create:

```text
jenkins-docker/
└── Dockerfile
```

Use:

```dockerfile
FROM jenkins/jenkins:lts-jdk21

USER root

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        awscli \
        git \
        curl \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

ARG DOCKER_VERSION=27.5.1

RUN curl -fsSL \
    "https://download.docker.com/linux/static/stable/aarch64/docker-${DOCKER_VERSION}.tgz" \
    -o /tmp/docker.tgz \
    && tar -xzf /tmp/docker.tgz \
        --strip-components=1 \
        -C /usr/local/bin \
        docker/docker \
    && rm /tmp/docker.tgz \
    && docker --version

USER jenkins
```


The Docker socket is mounted so Jenkins can build Docker images later.

This setup is appropriate for a local development/training environment.

---

# 7. Start Jenkins

```bash
docker compose -f docker-compose-setup.yml up -d --build
```

Check:

```bash
docker ps
```

Open:

```text
http://localhost:8081
```

Get the initial Jenkins password:

```bash
docker exec -it swiftkart-jenkins-microservice   cat /var/jenkins_home/secrets/initialAdminPassword
```

Complete the Jenkins initial setup.

---

# 8. Install Jenkins Plugins

Open:

```text
Manage Jenkins
→ Plugins
→ Available plugins
```

Install:

- SonarQube Scanner
- Pipeline Maven Integration
- PAM Authentication
- AWS Credentials

### Install the Standard Suite via Plugin Manager
- Pipeline
- Git
- GitHub Branch Source
- SSH Build Agents
- Credentials Binding
- Timestamper
- Workspace Cleanup
- Stage View

Restart Jenkins if requested.

---

# 9. Configure JDK 21

Go to:

```text
Manage Jenkins
→ Tools
```

Under **JDK installations**, add:

```text
Name:
JDK-21
```

Disable automatic installation.

Set:

```text
JAVA_HOME:
/opt/java/openjdk
```

The Jenkinsfile will use:

```groovy
tools {
    jdk 'JDK-21'
}
```

Verify:

```bash
docker exec swiftkart-jenkins-microservice java -version
```

---

# 10. Configure SonarQube

Open:

```text
http://localhost:9000
```

Complete the initial SonarQube setup.

Generate a SonarQube token from the SonarQube account security settings.

Use a token name such as:

```text
jenkins-analysis-token
```

Copy the generated token.

---

# 11. Add SonarQube Token to Jenkins

Open:

```text
Jenkins
→ Manage Jenkins
→ Credentials
→ System
→ Global credentials
```

Create:

```text
Kind:
Secret text

ID:
sonar-token

Secret:
<your SonarQube token>
```

Save.

---

# 12. Configure SonarQube Server in Jenkins

Go to:

```text
Manage Jenkins
→ System
```

Find:

```text
SonarQube servers
```

Enable:

```text
Enable injection of SonarQube server configuration as environment variables
```

Add:

```text
Name:
SonarServer

Server URL:
http://sonarqube:9000
```

Select:

```text
sonar-token
```

as the authentication token.

Save.

The name must match:

```groovy
withSonarQubeEnv('SonarServer')
```

---

# 13. Configure SonarQube Webhook

In SonarQube:

```text
Administration
→ Configuration
→ Webhooks
```

Create:

```text
Name:
Jenkins Webhook

URL:
http://jenkins:8080/sonarqube-webhook/
```

This allows Jenkins to receive the Quality Gate result.

---

# 14. Maven Configuration

Each service should have:

```text
pom.xml
```

Prefer Maven Wrapper for all three services:

```text
mvnw
mvnw.cmd
```

Then Jenkins can run:

```bash
./mvnw -B clean verify
```

If Maven Wrapper is not present and Maven is configured globally in Jenkins, use:

```bash
mvn -B clean verify
```

Do not mix the two approaches unnecessarily.

---

# 15. JaCoCo Configuration

Each Maven project should generate:

```text
target/site/jacoco/jacoco.xml
```

Add the JaCoCo plugin to each project's `pom.xml`:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>

    <executions>

        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>

        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>

    </executions>
</plugin>
```

Verify locally:

```bash
./mvnw clean verify
```

Then:

```bash
ls -l target/site/jacoco/jacoco.xml
```

---

# 16. Sonar Maven Plugin

Each project should have:

```xml
<plugin>
    <groupId>org.sonarsource.scanner.maven</groupId>
    <artifactId>sonar-maven-plugin</artifactId>
    <version>3.10.0.2594</version>
</plugin>
```

---

# 17. Sonar Project Configuration

Create `sonar-project.properties` in each project's root.

## SwiftKart

```properties
sonar.projectKey=swiftkart-ai-assistant
sonar.projectName=SwiftKart AI Assistant
sonar.projectVersion=0.0.1-SNAPSHOT

sonar.sources=src/main/java
sonar.tests=src/test/java
sonar.java.binaries=target/classes

sonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
```

## Knowledge Service

```properties
sonar.projectKey=swiftkart-knowledge-service
sonar.projectName=SwiftKart Knowledge Service
sonar.projectVersion=0.0.1-SNAPSHOT

sonar.sources=src/main/java
sonar.tests=src/test/java
sonar.java.binaries=target/classes

sonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
```

## API Gateway

```properties
sonar.projectKey=swiftkart-api-gateway
sonar.projectName=SwiftKart API Gateway
sonar.projectVersion=0.0.1-SNAPSHOT

sonar.sources=src/main/java
sonar.tests=src/test/java
sonar.java.binaries=target/classes

sonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
```

Use a different SonarQube project key for each service.

---

# 18. Standard Jenkinsfile

Use this Jenkinsfile in each independent Maven project:

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
                    sh '''
                        ./mvnw -B sonar:sonar -Dsonar.host.url=http://sonarqube:9000
                    '''
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
}
```

If a project does not contain `mvnw`, replace `./mvnw` with `mvn`.

---

# 19. Jenkins Job — SwiftKart

Create:

```text
New Item
→ Pipeline
```

Name:

```text
swiftkart-pipeline
```

Select:

```text
Pipeline script from SCM
```

SCM:

```text
Git
```

Repository:

```text
<your SwiftKart Git repository>
```

Credentials:

```text
github-credentials
```

Branch:

```text
*/main
```

Script Path:

```text
Jenkinsfile
```

Save.

Run:

```text
Build Now
```

Expected stages:

```text
Checkout
   ↓
Build & Test
   ↓
SonarQube Analysis
   ↓
Quality Gate
```

---

# 20. Jenkins Job — Knowledge Service

Create:

```text
knowledge-service-pipeline
```

Configure it exactly like the SwiftKart pipeline, pointing SCM to the repository/path containing `knowledge-service`.

The project should contain:

```text
knowledge-service/
├── pom.xml
├── mvnw
├── Jenkinsfile
├── sonar-project.properties
└── src/
```

Run:

```text
Build Now
```

Expected SonarQube project:

```text
swiftkart-knowledge-service
```

---

# 21. Jenkins Job — API Gateway

Create:

```text
api-gateway-pipeline
```

Configure it exactly like the other pipelines.

The project should contain:

```text
api-gateway/
├── pom.xml
├── mvnw
├── Jenkinsfile
├── sonar-project.properties
└── src/
```

Expected SonarQube project:

```text
swiftkart-api-gateway
```

---

# 22. GitHub Credentials

For a private GitHub repository:

```text
Manage Jenkins
→ Credentials
→ System
→ Global credentials
→ Add Credentials
```

Use:

```text
Kind:
Username with password

Username:
<GitHub username>

Password:
<GitHub Personal Access Token>

ID:
github-credentials
```

Never put the token in the Jenkinsfile.

---

# 23. Application Secrets

Do not put these in Jenkinsfiles:

```text
OPENAI_API_KEY
TAVILY_API_KEY
```

Do not hard-code them in:

```text
application.yml
docker-compose.yml
Jenkinsfile
Dockerfile
GitHub repository
```

For AWS deployment, retrieve them from AWS Secrets Manager.

The CI pipeline only needs to:

```text
compile
test
analyze
pass Quality Gate
```

---

# 24. ChromaDB Is Not Part of This CI Stack

Do not add ChromaDB to:

```text
docker-compose-setup.yml
```

This Compose file is only for:

```text
Jenkins
SonarQube
PostgreSQL
```

ChromaDB belongs to the application runtime environment.

The later application Compose environment can contain:

```text
API Gateway
SwiftKart
Knowledge Service
ChromaDB
```

If SwiftKart remains unchanged and already has its own ChromaDB configuration, the final runtime environment may require two ChromaDB instances.

That is independent of Jenkins/SonarQube.

---

# 25. Eureka Is Not Part of This CI Stack

Do not add Eureka to:

```text
docker-compose-setup.yml
```

Eureka is application infrastructure.

If implemented later, it belongs in the application runtime environment:

```text
Eureka
API Gateway
Knowledge Service
SwiftKart
```

It does not change the Jenkins + SonarQube setup.

---

# 26. Verify Jenkins → SonarQube Connectivity

Run:

```bash
docker exec swiftkart-jenkins-microservice   curl -I http://sonarqube:9000
```

You should receive an HTTP response.

Important:

Inside Jenkins:

```text
http://localhost:9000
```

means the Jenkins container itself.

The correct Docker-network address is:

```text
http://sonarqube:9000
```

---

# 27. Verify SonarQube → PostgreSQL

Check:

```bash
docker compose -f docker-compose-setup.yml ps
```

Then:

```bash
docker compose -f docker-compose-setup.yml logs sonarqube
```

SonarQube should eventually report that it is running.

PostgreSQL is accessed using:

```text
db:5432
```

not:

```text
localhost:5432
```

because PostgreSQL is another Docker service.

---

# 28. Verify CI Builds

Run:

```text
Jenkins
→ swiftkart-pipeline
→ Build Now
```

Then:

```text
Jenkins
→ knowledge-service-pipeline
→ Build Now
```

Then:

```text
Jenkins
→ api-gateway-pipeline
→ Build Now
```

Each should complete:

```text
Checkout                 SUCCESS
Build & Test             SUCCESS
SonarQube Analysis       SUCCESS
Quality Gate             SUCCESS
```

---

# 29. Verify SonarQube

Open:

```text
http://localhost:9000
```

You should have three independent projects:

```text
SwiftKart AI Assistant
SwiftKart Knowledge Service
SwiftKart API Gateway
```

Each project should have its own:

- source analysis
- bugs
- vulnerabilities
- code smells
- coverage
- Quality Gate

---

# 30. Recommended CI/CD Flow

Use independent CI pipelines:

```text
Developer
   |
   v
GitHub
   |
   +-------------------+
   |                   |
   v                   v
SwiftKart CI       Knowledge CI
   |                   |
   v                   v
SonarQube           SonarQube
   |                   |
   +---------+---------+
             |
             v
        Quality Gates
             |
             v
        Docker Build
             |
             v
            ECR
             |
             v
            EC2
             |
             v
      Docker Compose
```

API Gateway follows the same CI pattern.

---

# 31. Why Use Independent Pipelines?

For this project, use:

```text
swiftkart-pipeline
knowledge-service-pipeline
api-gateway-pipeline
```

rather than creating a complicated Maven multi-module build.

Advantages:

- each service builds independently
- each service has its own SonarQube project
- failures are isolated
- reflects real microservice CI
- easy to demonstrate during an interview
- services can later be deployed independently

---

# 32. Troubleshooting

## Jenkins cannot connect to SonarQube

Run:

```bash
docker exec swiftkart-jenkins-microservice   curl -I http://sonarqube:9000
```

If this fails, check:

```bash
docker network ls
docker network inspect <cicd-network>
```

Both containers must be on the same Docker network.

---

## Quality Gate waits and times out

Check the SonarQube webhook:

```text
http://jenkins:8080/sonarqube-webhook/
```

In SonarQube:

```text
Administration
→ Configuration
→ Webhooks
```

Verify the webhook exists.

Also check:

```text
Manage Jenkins
→ System
→ SonarQube servers
```

The server name must be:

```text
SonarServer
```

because the Jenkinsfile contains:

```groovy
withSonarQubeEnv('SonarServer')
```

---

## Maven build fails because `mvnw` is missing

Check:

```bash
ls -la
```

If `mvnw` does not exist, either add Maven Wrapper or change:

```bash
./mvnw -B clean verify
```

to:

```bash
mvn -B clean verify
```

---

## JaCoCo report is missing

Check:

```bash
ls -l target/site/jacoco/jacoco.xml
```

If missing, verify the JaCoCo plugin exists in the project's `pom.xml`.

---

## Sonar analysis uses the wrong project

Check:

```text
sonar-project.properties
```

The project key must be unique:

```text
swiftkart-ai-assistant
swiftkart-knowledge-service
swiftkart-api-gateway
```

---

# 33. Useful Commands

Start:

```bash
docker compose -f docker-compose-setup.yml up -d --build
```

Stop:

```bash
docker compose -f docker-compose-setup.yml down
```

Restart:

```bash
docker compose -f docker-compose-setup.yml restart
```

Status:

```bash
docker compose -f docker-compose-setup.yml ps
```

Logs:

```bash
docker compose -f docker-compose-setup.yml logs -f
```

Jenkins logs:

```bash
docker logs -f swiftkart-jenkins-microservice
```

SonarQube logs:

```bash
docker logs -f swiftkart-sonarqube-microservice
```

PostgreSQL logs:

```bash
docker logs -f swiftkart-postgres-microservice
```

---

# 34. Clean Rebuild

Normally, do not delete persistent volumes.

If you intentionally want to reset the complete local CI environment:

```bash
docker compose -f docker-compose-setup.yml down -v
```

Then:

```bash
docker compose -f docker-compose-setup.yml up -d --build
```

Warning:

`down -v` deletes the Jenkins and PostgreSQL Docker volumes.

Jenkins jobs/configuration and SonarQube database data can therefore be lost.

---

# 35. Final Verification Checklist

Before moving to Docker/ECR/EC2 deployment:

```text
[ ] Jenkins starts
[ ] SonarQube starts
[ ] PostgreSQL starts
[ ] Jenkins can reach SonarQube
[ ] JDK 21 configured
[ ] SonarQube token configured
[ ] SonarServer configured
[ ] SonarQube webhook configured
[ ] GitHub credentials configured
[ ] SwiftKart CI passes
[ ] Knowledge Service CI passes
[ ] API Gateway CI passes
[ ] JaCoCo report generated
[ ] SwiftKart has separate Sonar project
[ ] Knowledge Service has separate Sonar project
[ ] API Gateway has separate Sonar project
[ ] Quality Gate passes
[ ] No OpenAI/Tavily secrets committed
[ ] ChromaDB remains outside the CI infrastructure
[ ] Eureka remains outside the CI infrastructure unless later implemented
```

---

# 36. Final Interview Architecture

The CI/CD story can be explained as:

```text
                     GitHub
                        |
              +---------+---------+
              |         |         |
              v         v         v
          SwiftKart  Knowledge  API Gateway
             CI        CI          CI
              |         |          |
              +---------+----------+
                        |
                        v
                   Maven Build
                        |
                        v
                       Tests
                        |
                        v
                      JaCoCo
                        |
                        v
                    SonarQube
                        |
                        v
                   Quality Gate
                        |
                        v
                  Docker Images
                        |
                        v
                       ECR
                        |
                        v
                       EC2
                        |
                        v
                 Docker Compose
```

The infrastructure is deliberately separated:

```text
CI Infrastructure
-----------------
Jenkins
SonarQube
PostgreSQL


Application Infrastructure
--------------------------
API Gateway
SwiftKart
Knowledge Service
ChromaDB
(optional later: Eureka)
```

This allows the existing Jenkins + SonarQube environment to remain stable while the original SwiftKart application is extended with new microservices.

# SwiftKart AI Assistant - CI/CD Pipeline Setup Guide

A complete 13-step guide for configuring a local or server-based Dockerized Jenkins and SonarQube CI/CD pipeline with JaCoCo code coverage and automated quality gates.

---

## 1. Environment Variables (`.env`)
Create a `.env` file in your project root to secure credentials and configuration values:
```env
JENKINS_HOST_PORT=8081
SONAR_HOST_PORT=9000
SONARQUBE_URL=http://sonarqube:9000
POSTGRES_USER=sonar
POSTGRES_PASSWORD=sonar_password
POSTGRES_DB=sonarqube
```

---

## 2. Docker Compose Stack (`docker-compose-cicd.yml`)
Spin up Jenkins, SonarQube, and PostgreSQL in an isolated network stack:
```yaml
services:
  jenkins:
    image: jenkins/jenkins:lts-jdk21
    container_name: swiftkart-jenkins
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
    container_name: swiftkart-sonarqube
    ports:
      - "${SONAR_HOST_PORT}:9000"
    environment:
      - SONAR_JDBC_USERNAME=${POSTGRES_USER}
      - SONAR_JDBC_PASSWORD=${POSTGRES_PASSWORD}
      - SONAR_JDBC_URL=jdbc:postgresql://db:5432/${POSTGRES_DB}
    depends_on:
      - db
    networks:
      - cicd-net

  db:
    image: postgres:15-alpine
    container_name: swiftkart-postgres
    environment:
      - POSTGRES_USER=${POSTGRES_USER}
      - POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
      - POSTGRES_DB=${POSTGRES_DB}
    networks:
      - cicd-net

networks:
  cicd-net:
    driver: bridge

volumes:
  jenkins_home:
```

---

## 3. Sonar Scanner Config (`sonar-project.properties`)
Add SonarQube project analysis metadata:
```properties
sonar.projectKey=swiftkart-ai-assistant
sonar.projectName=SwiftKart AI Assistant
sonar.projectVersion=0.0.1-SNAPSHOT
sonar.sources=src/main/java
sonar.tests=src/test/java
sonar.java.binaries=target/classes
sonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
```

---

## 4. Maven JaCoCo & Sonar Plugins (`pom.xml`)
Integrate code coverage instrumentation into your Maven build:
```xml
<build>
  <plugins>
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
  </plugins>
</build>
```

---

## 5. Declarative `Jenkinsfile`
Pipeline definition for build, test, Sonar scan, and Quality Gate check:
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
}
```

---

## 6. Container Startup Commands
Initialize containers using Docker Compose:
```bash
docker compose -f docker-compose-cicd.yml up -d

docker compose ps
docker compose -f docker-compose-cicd.yml ps
docker compose -f docker-compose-cicd.yml logs -f sonarqube

```

---

## 7. Token Generation (SonarQube)
1. Navigate to `http://localhost:9000` (Default credentials: `admin` / `admin`).
2. Go to **My Account > Security > Generate Tokens**.
3. Create a User Token named `jenkins-analysis-token` and copy its secret value.

---

## 8. Webhook Configuration
1. In SonarQube, go to **Administration > Configuration > Webhooks**.
2. Click **Create** and set Name: `Jenkins Webhook`, URL: `http://jenkins:8080/sonarqube-webhook/`.

---

## 9. Jenkins Unlock & Initial Setup
1. Retrieve admin key:
   ```bash
   docker exec -it swiftkart-jenkins cat /var/jenkins_home/secrets/initialAdminPassword
   ```
2. Open `http://localhost:8081`, paste key, and install suggested plugins.

---

## 10. Plugin Installation
In Jenkins (**Manage Jenkins > Plugins > Available Plugins**), install:
- SonarQube Scanner
- Pipeline Maven Integration
- Git Plugin

---

## 11. Credentials Configuration
1. Go to **Manage Jenkins > Credentials > System > Global credentials**.
2. Add a credential of type **Secret text**.
3. Set Secret to your SonarQube token and ID to `sonar-token`.

---

## 12. SonarQube System Settings
In Jenkins (**Manage Jenkins > System > SonarQube servers**):
- Check **Enable injection of SonarQube server configuration**.
- Server URL: `http://sonarqube:9000`
- Server authentication token: Select `sonar-token`.

---

## 13. Pipeline Execution & Verification
1. Create a new Pipeline job in Jenkins.
2. Select **Pipeline script from SCM**, choose Git, and point to repository URL.
3. Trigger **Build Now** to verify end-to-end automation.

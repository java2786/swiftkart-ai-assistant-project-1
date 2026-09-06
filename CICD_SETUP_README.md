# SwiftKart AI Assistant - CI/CD Pipeline Setup Guide

A complete 13-step guide for configuring a local or server-based Dockerized Jenkins and SonarQube CI/CD pipeline with JaCoCo code coverage and automated quality gates.

## 0. Add plugin to `pom.xml`
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.sonarsource.scanner.maven</groupId>
            <artifactId>sonar-maven-plugin</artifactId>
            <version>3.10.0.2594</version>
        </plugin>
    </plugins>
</build>
```
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
- PAM Authentication

### Install the Standard Suite via Plugin Manager
  - Pipeline (installs the entire Pipeline aggregator suite)
  - Git
  - GitHub Branch Source
  - Pipeline: Stage View
  - Pipeline: Maven Integration
  - Timestamper
  - Workspace Cleanup Plugin
  - Matrix Authorization Strategy
  - Credentials Binding
  - Plain Credentials
  - SSH Build Agents
- Click Install at the bottom of the page.
- Check the box Restart Jenkins when installation is complete and no jobs are running.

### Configure JDK 21 in Jenkins UI
- Open Jenkins in your browser (http://localhost:8081).
- In the left navigation menu, go to Manage Jenkins → Tools (under System Configuration).
- Scroll down to the JDK installations section and click Add JDK.
- Configure the fields as follows:

  - Name: `JDK-21` (This must match jdk 'JDK-21' in your Jenkinsfile)
  - Install automatically: Uncheck this checkbox.
  - JAVA_HOME: `/opt/java/openjdk`
  - apply

```bash
docker exec swiftkart-jenkins echo $JAVA_HOME
```

---

## 11. Credentials Configuration
1. Go to **Manage Jenkins > Credentials > System > Global credentials**.
2. Add a credential of type **Secret text**.
3. Set Secret to your SonarQube token and ID to `sonar-token`.

### 1. Generate Your GitHub Personal Access Token (PAT)
**Prerequisite**

1. On GitHub, go to **Settings** → **Developer Settings** → **Personal access tokens** → **Tokens (classic)**.
2. Click **Generate new token (classic)**.
3. Under scopes, select at least `repo` (for private repository access) and `admin:repo_hook` (if using webhooks).
4. Copy the generated token (`ghp_...`).

> **Verification:** The token string is copied to your clipboard.

---

### 2. Navigate to Global Credentials
**Jenkins Dashboard**

1. Open Jenkins (`http://localhost:8081`).
2. Click **Manage Jenkins** in the left sidebar.
3. Scroll to the **Security** section and click **Credentials**.
4. Under **Stores scoped to Jenkins**, click on **System**.
5. Click the link **Global credentials (unrestricted)**.
6. Click **+ Add Credentials** (in the left menu or top right).

> **Verification:** The "New credentials" configuration form loads.

---

### 3. Configure and Save the Credential
**Credentials Form**

For Git SCM checkout over HTTPS, select **Username with password** (Git uses HTTP basic authentication with your PAT as the password):

* **Kind:** `Username with password`
* **Scope:** `Global (Jenkins, nodes, items, all child items, etc.)`
* **Username:** Your GitHub username
* **Password:** Paste your GitHub Personal Access Token (`ghp_...`)
* **ID:** `github-credentials` *(or a name of your choice, referenced in your pipeline script)*
* **Description:** `GitHub PAT for SwiftKart repository access`

Click **Create**.

---

## 12. SonarQube System Settings
In Jenkins (**Manage Jenkins > System > SonarQube servers**):
- Check **Enable injection of SonarQube server configuration**.
- SonarQube installations
- Name same as pipeline: `SonarServer`
- Server URL: `http://sonarqube:9000`
- Server authentication token: Select `sonar token with jenkins`.

---

## 13. Pipeline Execution & Verification
1. Create a new Pipeline job in Jenkins.
2. Select **Pipeline script from SCM**, choose Git, and point to repository URL.
3. Trigger **Build Now** to verify end-to-end automation.

## Others
### Create the Pipeline Item
- Navigate to the Jenkins Dashboard (http://localhost:8081).
- Click New Item in the left navigation sidebar.
- Enter the item name: `swiftkart-pipeline`.
- Select Pipeline as the project type.
- Click OK at the bottom of the form.
- Verification: You are redirected to the job configuration page showing tabs like General, Build Triggers, and Pipeline.

### Configure SCM & Pipeline Script
- Pipeline Tab
- Scroll down to the Pipeline section at the bottom.
- Set Definition to Pipeline script from SCM.
- In the SCM dropdown, choose Git.
- Fill in the repository details:
- Repository URL: Provide your Git repo URL (e.g., your GitHub HTTPS/SSH URL or your local bare git repo path).
>
> If not configured yet:
> - Credentials: Select your Git credentials if it is a private repository; leave as - none - if it is a public repository or a mounted local directory.
>   - For private repo - github -> settings -> credentials -> personal access tokens (classic) -> Click Generate new token → Generate new token (classic).
>   - Note: jenkins-git-access
>     - Expiration: Select your desired window (e.g., 30, 90 days, or No expiration for local dev).
>     - Scopes: Check the repo box (grants full control of private repositories).
>     - Click Generate token at the bottom and copy the generated token immediately (it will look like ghp_...).

- Branch Specifier: Change */master to */main (or whichever branch contains your code and Jenkinsfile).
- Script Path: Verify this is set to Jenkinsfile (points to the file in your repository root).
Click Save.
- Verification: The screen displays the swiftkart-pipeline dashboard with a left sidebar including Build Now, Configure, and Pipeline Syntax.


### Trigger & Verify the Build
- Execution
- Click Build Now in the left sidebar menu.
- Under Build History on the bottom-left, click on #1 (the running build).
- Click Console Output to observe the logs in real time.
- Verification: The pipeline stages (Checkout → Build & Test → SonarQube Analysis → Quality Gate) run consecutively, ending with Finished: SUCCESS.


## Jenkins should find a SonarQube server configuration named SonarServer
#### Open Jenkins System Configuration
- Navigate Settings
  - In your browser, open Jenkins (http://localhost:8081).
  - Click Manage Jenkins in the left sidebar.
  - Click System (under the System Configuration section).

- Verification: The System configuration page loads with global settings for various tools and plugins.

#### Configure the SonarQube Server
- Add Installation
  - Scroll down until you find the SonarQube servers section.
  - Check the box labeled Enable injection of SonarQube server configuration as environment variables.
  - Click the Add SonarQube button.
  - Fill in the fields exactly as follows:

    - Name: SonarServer (Must match case-sensitively with your Jenkinsfile)
    - Server URL: http://sonarqube:9000
    - Server authentication token: Select your sonar-token credential from the dropdown.

  - Click Apply, then scroll to the bottom and click Save.

- Verification: After saving, navigate back to Manage Jenkins → System → SonarQube servers and confirm SonarServer is listed with the URL http://sonarqube:9000.
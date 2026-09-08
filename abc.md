I’ll inspect the assistant service’s tracked files and the CI-related contents/references first, then I’ll map each asset to a destination and verify the remaining application surface.

Searched for files matching `ai-launchpad` · `swiftkart-ai-assistant/**/Jenkinsfile*`, 2 matches

Searched for files matching `ai-launchpad` · `swiftkart-ai-assistant/**/*jenkins*`, 3 matches

Searched for files matching `ai-launchpad` · `swiftkart-ai-assistant/**/sonar*`, 1 match

Searched for files matching `ai-launchpad` · `swiftkart-ai-assistant/**/docker-compose*`, no matches

Searched for files matching `ai-launchpad` · `swiftkart-ai-assistant/**/.env*`, 1 match

Searched for files matching `ai-launchpad` · `swiftkart-ai-assistant/**/Dockerfile*`, 1 match

The initial scan found two Jenkins pipelines, Sonar configuration, an environment template, and the service Dockerfile; no CI compose file or Jenkins Docker build directory is present under the service. I’m checking whether the environment template and documentation are operational CI assets or merely references, and whether tracked history shows any hidden CI-specific paths.

Ran terminal command:  git status --short && git ls-files swiftkart-ai-assistant | sed -n '1,240p'

Ran terminal command:  rg -n -i --hidden --glob '!target/**' --glob '!volume_data/**' 'Jenkins|jenkins|SONAR|sonar|docker-compose|docker build|dockerfile|context:|swiftkart-ai-assistant|ai-launchpad' swiftkart-ai-assistant

Ran terminal command:  for f in swiftkart-ai-assistant/Jenkinsfile swiftkart-ai-assistant/Jenkinsfile-ec2 swiftkart-ai-assistant/sonar-project.properties swiftkart-ai-assistant/.env.example swiftkart-ai-assistant/Dockerfile; do printf '\n--- %s ---\n' "$f"; sed -n '1,260p' "$f"; done

The worktree already contains an in-progress migration: several CI assets are deleted from the service and a new `cicd` directory exists, while unrelated Dockerfiles are untracked. One requested assumption is therefore stale: the audit must distinguish current filesystem state from the original tracked paths, then identify any references that still need rewriting. `rg` is unavailable in this shell, so I’m using `grep`/`find` for the reference scan.

Ran terminal command:  find cicd -maxdepth 4 -type f -print | sort; printf '\n--- service top level ---\n'; find swiftkart-ai-assistant -maxdepth 2 -type f -print | sort

Ran terminal command:  printf '%s\n' '--- current references ---'; grep -RInE --exclude-dir=target --exclude-dir=volume_data --exclude-dir=.git -E 'Jenkins|jenkins|SONAR|sonar|docker-compose|docker build|Dockerfile|context:|swiftkart-ai-assistant|ai-launchpad' swiftkart-ai-assistant cicd 2>/dev/null || true; printf '%s\n' '--- worktree diff names ---'; git diff --name-status; printf '%s\n' '--- untracked paths ---'; git ls-files --others --exclude-standard

Read , lines 1 to 260

The migration is incomplete in the current filesystem: the CI directory has been populated, but the two Jenkinsfiles and Sonar config still remain in the service, and the service also contains ignored runtime/secrets files (`.env`, `.env.aws`, PEM, database data) that are not application source. The key relocation risk is the EC2 pipeline’s Docker build context: after moving the Jenkinsfile, its `docker build .` must run from the repository root or explicitly target `swiftkart-ai-assistant`, while the app Dockerfile itself should stay with the service.

Ran terminal command:  for f in cicd/docker-compose-cicd.yml cicd/docker-compose-prod.yml cicd/jenkins-docker/Dockerfile cicd/CICD_SETUP_README.md swiftkart-ai-assistant/.env.example swiftkart-ai-assistant/sonar-project.properties; do printf '\n--- %s ---\n' "$f"; sed -n '1,280p' "$f"; done

Ran terminal command:  printf '%s\n' '--- HEAD CI paths ---'; git ls-tree -r --name-only HEAD swiftkart-ai-assistant | grep -Ei 'jenkins|sonar|compose|cicd|aws_setup|Dockerfile|env' || true; printf '%s\n' '--- status porcelain ---'; git status --short --untracked-files=all

Read 

The reference scan confirms the moved compose stack still uses a valid local `./jenkins-docker` context, but the service README and AWS deployment documentation still tell users to run CI from the old service directory. The audit also needs to flag `docs/aws_cicd*`, `docs/aws/`, and `readme.md` as CI documentation/reference material, even though they do not match the narrow filename patterns. I’m doing one final focused check of exact env contents and the app Docker build inputs before reporting.

Ran terminal command:  printf '%s\n' '--- .env.example ---'; sed -n '1,120p' swiftkart-ai-assistant/.env.example; printf '%s\n' '--- service Dockerfile ---'; sed -n '1,160p' swiftkart-ai-assistant/Dockerfile; printf '%s\n' '--- service dockerignore ---'; sed -n '1,160p' swiftkart-ai-assistant/.dockerignore

Ran terminal command:  find swiftkart-ai-assistant -maxdepth 3 -type f -not -path 'swiftkart-ai-assistant/src/*' -not -path 'swiftkart-ai-assistant/target/*' -not -path 'swiftkart-ai-assistant/.git/*' -print | sort

Ran terminal command:  grep -RInE --exclude-dir=target --exclude-dir=volume_data --exclude-dir=.git -E 'docker-compose-cicd\.yml|docker-compose-prod\.yml|Jenkinsfile|sonar-project\.properties|jenkins-docker|aws_setup|CICD_SETUP_README' swiftkart-ai-assistant cicd 2>/dev/null | sed -n '1,240p'

## Audit Report

The worktree already contains a partial migration. The following CI/CD assets remain in `swiftkart-ai-assistant`:

| Current path | Recommended destination |
|---|---|
| `Jenkinsfile` | `cicd/Jenkinsfile` |
| `Jenkinsfile-ec2` | `cicd/Jenkinsfile-ec2` |
| `sonar-project.properties` | `cicd/sonar-project.properties` |
| `.env.example` | `cicd/.env.cicd` after removing application-only keys |
| `docs/aws_cicd.md` | `cicd/docs/aws_cicd.md` |
| `docs/aws_cicd_readme.md` | `cicd/docs/aws_cicd_readme.md` |
| `docs/AWS_EC2_JENKINS_DEPLOYMENT_README.md` | `cicd/docs/AWS_EC2_JENKINS_DEPLOYMENT_README.md` |
| `docs/aws/` | `cicd/docs/aws/` |
| `readme.md` | Split application and CI instructions; CI instructions belong under `cicd` |

These assets have already been moved in the current worktree:

- `docker-compose-cicd.yml` -> `docker-compose-cicd.yml`
- `docker-compose-prod.yml` -> `docker-compose-prod.yml`
- `jenkins-docker/Dockerfile` -> `Dockerfile`
- `aws_setup/` -> `aws_setup`
- `CICD_SETUP_README.md` -> `CICD_SETUP_README.md`

There are also untracked `.DS_Store` files under `cicd`; these should be removed and ignored.

## Safe Migration Commands

From a clean checkout, use Git-aware moves:

```bash
git mv swiftkart-ai-assistant/Jenkinsfile cicd/Jenkinsfile
git mv swiftkart-ai-assistant/Jenkinsfile-ec2 cicd/Jenkinsfile-ec2
git mv swiftkart-ai-assistant/sonar-project.properties cicd/sonar-project.properties
git mv swiftkart-ai-assistant/.env.example cicd/.env.cicd

git mv swiftkart-ai-assistant/CICD_SETUP_README.md cicd/CICD_SETUP_README.md
git mv swiftkart-ai-assistant/docker-compose-cicd.yml cicd/docker-compose-cicd.yml
git mv swiftkart-ai-assistant/docker-compose-prod.yml cicd/docker-compose-prod.yml
git mv swiftkart-ai-assistant/jenkins-docker cicd/jenkins-docker
git mv swiftkart-ai-assistant/aws_setup cicd/aws_setup

git mv swiftkart-ai-assistant/docs/aws_cicd.md cicd/docs/aws_cicd.md
git mv swiftkart-ai-assistant/docs/aws_cicd_readme.md cicd/docs/aws_cicd_readme.md
git mv swiftkart-ai-assistant/docs/AWS_EC2_JENKINS_DEPLOYMENT_README.md cicd/docs/AWS_EC2_JENKINS_DEPLOYMENT_README.md
git mv swiftkart-ai-assistant/docs/aws cicd/docs/aws
```

Because the current worktree already contains delete/add pairs, do not rerun those commands against the current state. Stage the completed migration and allow Git to detect renames:

```bash
rm -f cicd/.DS_Store cicd/aws_setup/.DS_Store
git add -A
git status --short
git diff --cached --summary
```

## References Requiring Updates

1. `cicd/Jenkinsfile`
   - `./mvnw` now needs to run inside `swiftkart-ai-assistant`.
   - `sonar-project.properties` is no longer at the service root unless Sonar is invoked with an explicit project base directory.

   Recommended pipeline shape:

   ```groovy
   dir('swiftkart-ai-assistant') {
       sh './mvnw -B clean verify'
       sh './mvnw -B sonar:sonar -Dsonar.host.url=http://sonarqube:9000'
   }
   ```

2. `cicd/Jenkinsfile-ec2`
   - Current `docker build .` assumes the application directory is the checkout directory.
   - From the repository root, use:

   ```bash
   docker build \
     --platform linux/amd64 \
     -f swiftkart-ai-assistant/Dockerfile \
     -t "${ECR_REGISTRY}/${ECR_REPO}:${IMAGE_TAG}" \
     swiftkart-ai-assistant
   ```

3. CI documentation and `readme.md`
   - Update commands referencing `docker-compose-cicd.yml` to:

   ```bash
   docker compose -f cicd/docker-compose-cicd.yml up -d
   docker compose -f cicd/docker-compose-cicd.yml down
   ```

   - Update `docker-compose-prod.yml`, `CICD_SETUP_README.md`, Jenkinsfile, Sonar, and `aws_setup` paths to their `cicd` locations.

4. `docker-compose-cicd.yml`
   - Its `./jenkins-docker` build context is correct when executed from `cicd`.
   - From the repository root, invoke it with `-f cicd/docker-compose-cicd.yml`; Compose still resolves relative build paths from the compose file directory.

5. `.env.example`
   - It contains `JENKINS_*`, `SONAR_*`, and PostgreSQL CI settings, so it belongs with CI.
   - It also contains `TAVILY_API_KEY` and `OPENAI_API_KEY`; do not place real application secrets in `.env.cicd`.

## Remaining Service Verification

The following are valid application assets and should remain:

- `src/`
- `pom.xml`
- `mvnw` and `.mvn/`
- `Dockerfile`
- `.dockerignore`
- `src/main/resources/application*.properties`
- application test code and application documentation

The service is not yet strictly application-only. It still contains:

- `Jenkinsfile`
- `Jenkinsfile-ec2`
- `sonar-project.properties`
- CI documentation under `docs/`
- CI references in `readme.md`
- `.env.example` with CI variables
- Ignored runtime/secrets material: `.env`, `.env.aws`, `swiftkart-key.pem`, `volume_data/`
- Nonstandard files such as `BuggyService.java`, `kbd/`, and local database data

The application `Dockerfile` itself should remain in `swiftkart-ai-assistant`. It builds only from `src/`, `pom.xml`, and Maven wrapper files, so it is a microservice image definition rather than a CI container asset.
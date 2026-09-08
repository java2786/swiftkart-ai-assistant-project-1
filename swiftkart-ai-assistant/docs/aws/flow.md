```plaintext
GitHub
   |
   v
Jenkins CI
   |
   | Build + Test + SonarQube
   |
   v
Jenkins Deployment Job
   |
   | Docker Build
   v
Amazon ECR
   |
   | SSH
   v
AWS EC2
   |
   | docker pull
   v
Docker Container
```
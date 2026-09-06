# The idea is: 
```plaintext
Developer
   |
   | git push
   v
GitHub
   |
   v
Jenkins
   |
   | 1. CI
   v
swiftkart-pipeline
   |
   | Checkout
   | Maven Build
   | Tests
   | JaCoCo
   | SonarQube
   | Quality Gate
   |
   | SUCCESS
   v
swiftkart-deploy-ec2
   |
   | Docker Build
   | Push image
   | Deploy
   v
Amazon ECR
   |
   v
AWS EC2
   |
   | Docker
   | Pull image
   | Run container
   |
   v
SwiftKart Application
```

# Do not do this:
```plaintext
Jenkins
   |
   | AWS_ACCESS_KEY_ID
   | AWS_SECRET_ACCESS_KEY
   | OPENAI_API_KEY
   v
SSH -> EC2
```

# Do this:
```plaintext
Jenkins
   |
   | SSH key
   v
EC2
   |
   | IAM Role
   |----> Amazon ECR
   |
   | Production environment file
   |----> OpenAI API key
   |----> DB credentials
   |----> other secrets
   |
   v
Docker Container
```

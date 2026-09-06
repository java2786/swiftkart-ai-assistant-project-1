> docker compose -f docker-compose-cicd.yml up -d

> docker compose -f docker-compose-cicd.yml ps
> docker compose -f docker-compose-cicd.yml logs -f sonarqube



## Quick Reference Commands  

| Task | Command |
| :--- | :--- |
| Start Stack | `docker compose -f docker-compose-cicd.yml up -d` |
| Check Status | `docker compose -f docker-compose-cicd.yml ps` |
| View Live Logs | `docker compose -f docker-compose-cicd.yml logs -f [jenkins\|sonarqube]` |
| Stop Stack (Saves Data) | `docker compose -f docker-compose-cicd.yml stop` |
| Shut Down Stack (Saves Volumes) | `docker compose -f docker-compose-cicd.yml down` |
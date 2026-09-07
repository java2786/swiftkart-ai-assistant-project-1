FROM jenkins/jenkins:lts-jdk21

USER root

# Install required tools
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        # docker-cli \
        awscli \
        git \
        curl \
    && rm -rf /var/lib/apt/lists/*

    # Install Docker CLI
RUN curl -fsSL https://download.docker.com/linux/static/stable/aarch64/docker-26.1.5.tgz \
    | tar -xz --strip-components=1 -C /usr/local/bin docker/docker

USER jenkins


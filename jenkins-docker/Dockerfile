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
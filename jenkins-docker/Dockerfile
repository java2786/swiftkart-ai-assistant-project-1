FROM jenkins/jenkins:lts-jdk21

USER root

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        awscli \
        maven \
        git \
        curl \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

ARG DOCKER_VERSION=27.5.1
ARG BUILDX_VERSION=0.36.1

RUN curl -fsSL \
    "https://download.docker.com/linux/static/stable/aarch64/docker-${DOCKER_VERSION}.tgz" \
    -o /tmp/docker.tgz \
    && tar -xzf /tmp/docker.tgz \
        --strip-components=1 \
        -C /usr/local/bin \
        docker/docker \
    && rm /tmp/docker.tgz \
    && docker --version

RUN mkdir -p /usr/local/lib/docker/cli-plugins \
    && curl -fsSL \
        "https://github.com/docker/buildx/releases/download/v${BUILDX_VERSION}/buildx-v${BUILDX_VERSION}.linux-arm64" \
        -o /usr/local/lib/docker/cli-plugins/docker-buildx \
    && chmod +x /usr/local/lib/docker/cli-plugins/docker-buildx \
    && docker buildx version

USER jenkins
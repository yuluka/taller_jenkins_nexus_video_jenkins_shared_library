# ==============================================================================
# Jenkins Controller personalizado con Docker CLI, JDK 17, Maven y Herramientas CI
# Universidad Icesi - Ingesoft V
# ==============================================================================
FROM jenkins/jenkins:lts-jdk17

USER root

# 1. Instalar dependencias del sistema operativo, cliente Docker oficial, Maven y SSH
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    gnupg \
    lsb-release \
    git \
    maven \
    openssh-client \
    procps \
    tar \
    gzip \
    && mkdir -p /etc/apt/keyrings \
    && curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null \
    && apt-get update && apt-get install -y --no-install-recommends docker-ce-cli \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# 2. Configurar grupo docker para que el usuario 'jenkins' pueda interactuar con /var/run/docker.sock
ARG DOCKER_GID=998
RUN (groupadd -g ${DOCKER_GID} docker || groupmod -g ${DOCKER_GID} docker || true) \
    && usermod -aG docker,root jenkins

# 3. Preinstalar plugins esenciales para Pipelines, GitHub Webhooks, Docker y Shared Libraries
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugins-file /usr/share/jenkins/ref/plugins.txt || true

# 4. Regresar al usuario estándar seguro de Jenkins
USER jenkins

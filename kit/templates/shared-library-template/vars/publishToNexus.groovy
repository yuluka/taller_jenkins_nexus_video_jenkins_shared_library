/**
 * Step modular: publishToNexus
 * Publica el JAR en maven-releases y/o la imagen en Docker Registry de Sonatype Nexus 3.
 */
def call(Map params = [:]) {
    String serviceName = params.serviceName
    String nexusHost = params.nexusHost
    String dockerPort = params.dockerPort ?: '9080'
    boolean publishJar = params.publishJar != null ? params.publishJar : true
    boolean publishDocker = params.publishDocker != null ? params.publishDocker : true

    // Identificador inmutable para la imagen Docker: ${BUILD_NUMBER}-${GIT_COMMIT[0..7]}
    String commitHash = env.GIT_COMMIT ? env.GIT_COMMIT.take(8) : 'latest'
    String imageTag = "${env.BUILD_NUMBER}-${commitHash}"
    String registryUrl = "${nexusHost}:${dockerPort}"
    String fullImageName = "${registryUrl}/${serviceName}:${imageTag}"

    // 1. Publicar JAR en Nexus (maven-releases)
    if (publishJar) {
        echo ">> [publishToNexus] Publicando JAR en maven-releases de Nexus..."
        withCredentials([usernamePassword(credentialsId: 'nexus-credentials', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS')]) {
            // TODO: Ejecutar mvn clean deploy apuntando a maven-releases de Nexus
            sh """
                mvn -B clean deploy -DskipTests \
                    -DaltDeploymentRepository=nexus::default::http://${NEXUS_USER}:${NEXUS_PASS}@${nexusHost}:8081/repository/maven-releases/
            """
        }
    }

    // 2. Construir y Publicar Imagen Docker en Nexus (:9080)
    if (publishDocker) {
        echo ">> [publishToNexus] Construyendo imagen Docker: ${fullImageName}"
        // TODO: Construir la imagen localmente con 'docker build'
        sh "docker build -t ${fullImageName} ."

        echo ">> [publishToNexus] Iniciando sesión y empujando a Nexus Registry..."
        withCredentials([usernamePassword(credentialsId: 'nexus-credentials', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS')]) {
            // TODO: docker login, docker push y docker logout
            sh """
                echo "\$NEXUS_PASS" | docker login ${registryUrl} -u "\$NEXUS_USER" --password-stdin
                docker push ${fullImageName}
                docker logout ${registryUrl}
            """
        }
    }

    return [imageTag: imageTag, fullImageName: fullImageName]
}

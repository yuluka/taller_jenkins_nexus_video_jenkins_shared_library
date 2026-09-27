import org.iaslab.cicd.PipelineConfig

/**
 * Orquestador principal de ciclo de vida CI/CD empresarial.
 * Uso en Jenkinsfile: standardPipeline(serviceName: '...', nexusHost: '...', ...)
 */
def call(Map rawConfig = [:]) {
    PipelineConfig cfg = new PipelineConfig(rawConfig)

    String deployedImage = ""
    String previousImage = ""
    boolean deployAttempted = false

    pipeline {
        agent any

        tools {
            maven 'Maven-3.9'
            jdk 'JDK-17'
        }

        options {
            timeout(time: 30, unit: 'MINUTES')
            timestamps()
        }

        stages {
            stage('Checkout SCM') {
                steps {
                    echo ">> [Checkout] Obteniendo código fuente del repositorio..."
                    checkout scm
                }
            }

            stage('Compile & Test') {
                steps {
                    echo ">> [Compile & Test] Compilando con Maven y ejecutando pruebas unitarias..."
                    // TODO: Ejecutar mvn clean test para validar el microservicio
                    sh "mvn -B clean test"
                }
            }

            stage('Package & Publish to Nexus') {
                steps {
                    echo ">> [Package & Publish] Empaquetando y publicando en Sonatype Nexus..."
                    script {
                        def pubResult = publishToNexus(
                            serviceName: cfg.serviceName,
                            nexusHost: cfg.nexusHost,
                            dockerPort: cfg.dockerPort,
                            publishJar: cfg.publishJar,
                            publishDocker: cfg.publishDocker
                        )
                        deployedImage = pubResult.fullImageName
                    }
                }
            }

            stage('Deploy to QA (grid100 / ec2-deploy)') {
                steps {
                    echo ">> [Deploy] Desplegando versión inmutable en nodo QA: ${cfg.deployTarget}"
                    script {
                        deployAttempted = true
                        withCredentials([
                            sshUserPrivateKey(credentialsId: 'ssh-deploy-qa', keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER'),
                            usernamePassword(credentialsId: 'nexus-credentials', usernameVariable: 'NEXUS_USER', passwordVariable: 'NEXUS_PASS')
                        ]) {
                            sh """
                                echo "\$NEXUS_PASS" | ssh -o StrictHostKeyChecking=no -i "\$SSH_KEY" "\$SSH_USER@${cfg.deployTarget}" "docker login ${cfg.nexusHost}:${cfg.dockerPort} -u '\$NEXUS_USER' --password-stdin"
                                ssh -o StrictHostKeyChecking=no -i "\$SSH_KEY" "\$SSH_USER@${cfg.deployTarget}" '
                                    set -e
                                    echo "Descargando imagen: ${deployedImage}..."
                                    docker pull ${deployedImage}

                                    # Guardar nombre de la imagen previa para posible rollback
                                    PREV_IMAGE=\$(docker inspect --format="{{.Config.Image}}" ${cfg.serviceName} 2>/dev/null || echo "")
                                    echo "\$PREV_IMAGE" > /tmp/${cfg.serviceName}_prev_image.txt

                                    # Detener y remover contenedor previo si existe
                                    docker stop ${cfg.serviceName} 2>/dev/null || true
                                    docker rm ${cfg.serviceName} 2>/dev/null || true

                                    # Iniciar nuevo contenedor
                                    docker run -d --name ${cfg.serviceName} -p 9021:8080 ${deployedImage}
                                '
                            """
                        }
                    }
                }
            }

            stage('Healthcheck & Smoke Test') {
                steps {
                    echo ">> [Healthcheck] Ejecutando Smoke Test contra ${cfg.healthEndpoint}..."
                    script {
                        // Bucle de reintentos: 5 intentos con espera de 5 segundos
                        sh """
                            ENDPOINT="http://${cfg.deployTarget}:9021${cfg.healthEndpoint}"
                            echo "Validando salud en: \$ENDPOINT"
                            SUCCESS=0
                            for i in 1 2 3 4 5; do
                                echo "Intento \$i de 5..."
                                STATUS=\$(curl -s -o /dev/null -w "%{http_code}" "\$ENDPOINT" || echo "000")
                                if [ "\$STATUS" -ge 200 ] && [ "\$STATUS" -lt 400 ]; then
                                    echo ">> Smoke Test Exitoso! Código HTTP: \$STATUS"
                                    SUCCESS=1
                                    break
                                fi
                                sleep 5
                            done

                            if [ \$SUCCESS -ne 1 ]; then
                                echo ">> ERROR: Smoke Test falló tras 5 reintentos."
                                exit 1
                            fi
                        """
                    }
                }
            }
        }

        post {
            failure {
                script {
                    if (deployAttempted) {
                        echo ">> [ALERTA] Fallo detectado. Iniciando Rollback automático en ${cfg.deployTarget}..."
                        withCredentials([sshUserPrivateKey(credentialsId: 'ssh-deploy-qa', keyFileVariable: 'SSH_KEY', usernameVariable: 'SSH_USER')]) {
                            sh """
                                ssh -o StrictHostKeyChecking=no -i "\$SSH_KEY" "\$SSH_USER@${cfg.deployTarget}" '
                                    if [ -f /tmp/${cfg.serviceName}_prev_image.txt ]; then
                                        PREV_IMG=\$(cat /tmp/${cfg.serviceName}_prev_image.txt)
                                        if [ -n "\$PREV_IMG" ]; then
                                            echo "Restaurando contenedor con versión anterior: \$PREV_IMG..."
                                            docker stop ${cfg.serviceName} 2>/dev/null || true
                                            docker rm ${cfg.serviceName} 2>/dev/null || true
                                            docker run -d --name ${cfg.serviceName} -p 9021:8080 "\$PREV_IMG"
                                            echo ">> Rollback completado exitosamente."
                                        fi
                                    fi
                                '
                            """
                        }
                    }
                }
            }
        }
    }
}

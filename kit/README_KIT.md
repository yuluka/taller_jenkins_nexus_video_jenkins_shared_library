# Kit de Inicio: Taller Evaluativo Jenkins CI/CD + Shared Libraries
### Universidad Icesi — Ingeniería de Software V

Este kit contiene el andamiaje (*scaffolding*) de infraestructura y las plantillas base para iniciar el desarrollo del **Taller Evaluativo de Pipelines CI/CD con Jenkins**.

---

## 📂 Contenido del Kit

```
kit_taller_jenkins/
├── jenkins/
│   ├── docker-compose.yml       # Stack local de Jenkins Controller + Smee Relay
│   ├── Dockerfile               # Imagen de Jenkins con Docker CLI, JDK 17, Maven y SSH
│   ├── .env.example             # Configuración de puertos y canal de Smee.io
│   ├── plugins.txt              # Plugins preconfigurados recomendados
│   └── smee/
│       └── Dockerfile           # Contenedor ultraligero para el cliente relay de Smee.io
├── templates/
│   ├── shared-library-template/ # Esqueleto para su repositorio 'jenkins-pipeline-shared-lib'
│   │   ├── vars/
│   │   │   ├── standardPipeline.groovy
│   │   │   └── publishToNexus.groovy
│   │   └── src/org/iaslab/cicd/
│   │       └── PipelineConfig.groovy
│   └── microservicio-template/  # Plantilla para el Jenkinsfile del microservicio
│       ├── Jenkinsfile.template
│       └── pom-snippet.xml
└── README_KIT.md
```

---

## 🚀 Inicio Rápido de la Infraestructura en 3 Pasos

### 1. Iniciar Jenkins en su máquina local
1. Ingrese a la carpeta `jenkins/`:
   ```bash
   cd jenkins
   cp .env.example .env
   ```
2. Obtenga su canal público en [https://smee.io](https://smee.io) presionando **Start a new channel** y péguelo en `SMEE_URL` dentro de `.env`.
3. Levante el contenedor:
   ```bash
   docker compose up -d --build
   ```

### 2. Desbloquear Jenkins
Espere 60 segundos y obtenga la contraseña de desbloqueo:
```bash
docker exec -it jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```
Abra en su navegador `http://localhost:8080`, ingrese la clave y complete el asistente inicial creando su usuario administrador.

### 3. Crear sus Repositorios en GitHub
1. Cree un repositorio en GitHub para su librería compartida: **`jenkins-pipeline-shared-lib`**.
   * Copie el contenido de `templates/shared-library-template/` allí, implemente la lógica y haga push con un tag `v1.0`.
2. En su repositorio de aplicación **`microservicio-backend`**:
   * Copie `Jenkinsfile.template` como `Jenkinsfile` y ajuste las IPs de su servidor Nexus y servidor de despliegue QA.

# Spec Delta

## MODIFIED Requirements

### Requirement: Artefactos de despliegue agrupados
Todos los archivos de Docker MUST estar en la carpeta `docker/` de la raíz del repositorio:
- el Dockerfile y sus reglas de exclusión del build;
- el compose de despliegue y su plantilla de variables;
- el compose de desarrollo, en `docker/local/`, con su plantilla de variables;
- la guía de despliegue y de uso local.

#### Scenario: Ubicación de los archivos
- **WHEN** se lista el contenido de `docker/`
- **THEN** contiene el Dockerfile, sus reglas de exclusión, `docker-compose.yml`, `.env.example` y la guía
- **AND** `docker/local/` contiene `docker-compose.yml` y `.env.example`
- **AND** no hay archivos de Docker fuera de esa carpeta

### Requirement: Orquestación con Docker Compose
`docker/docker-compose.yml` MUST definir un servicio de PostgreSQL, un servicio del backend y un servicio de n8n.
- El backend MUST conectarse a la base de datos de ese compose y MUST arrancar recién cuando la base de datos acepte conexiones.
- n8n MUST alcanzar al backend por la red interna del compose, sin pasar por el dominio público.
- Ningún servicio MUST publicar puertos en el host. El backend y n8n MUST exponer su puerto HTTP solo en la red de contenedores, para que el enrutamiento de sus dominios lo haga el proxy de Dokploy.

#### Scenario: Levantar el stack
- **WHEN** se crea `docker/.env` a partir de `docker/.env.example` con valores válidos y se ejecuta `docker compose -f docker/docker-compose.yml up -d`
- **THEN** los tres servicios quedan saludables
- **AND** desde la red del compose, `GET http://backend:8080/api/productos` responde 200

#### Scenario: Base de datos no expuesta
- **WHEN** el stack está en ejecución
- **THEN** ni el puerto de PostgreSQL ni el de n8n quedan publicados en el host

#### Scenario: n8n alcanza al backend internamente
- **WHEN** el stack está en ejecución
- **THEN** desde el contenedor de n8n, `GET http://backend:8080/actuator/health` responde con `"status":"UP"`

### Requirement: Persistencia de datos
Los datos de PostgreSQL, los archivos que guarda la aplicación (notas de voz) y los datos de n8n MUST persistir en volúmenes con nombre de Docker, de modo que sobrevivan a redeploys y reinicios y se puedan respaldar con Dokploy.

#### Scenario: Persistencia tras redeploy
- **WHEN** se ejecuta `docker compose down` sin `-v` y luego se vuelve a levantar el stack
- **THEN** los registros de la base de datos, los archivos guardados y los workflows de n8n siguen disponibles

### Requirement: Configuración del despliegue mediante .env
El compose MUST tomar su configuración del archivo `docker/.env`:
- credenciales de la base de datos;
- variables de Telegram;
- activación de la documentación;
- estrategia de esquema;
- clave de cifrado, dominio público y zona horaria de n8n.

Las variables obligatorias MUST hacer fallar el despliegue con un mensaje claro si faltan. Entre ellas están la contraseña de la base de datos, la clave de cifrado de n8n y el dominio de n8n. El repositorio MUST incluir `docker/.env.example` con todas las variables documentadas, y git MUST ignorar `docker/.env`.

#### Scenario: Variable obligatoria ausente
- **WHEN** `docker/.env` no define la contraseña de la base de datos y se ejecuta `docker compose up`
- **THEN** compose termina con un error que nombra la variable faltante, sin crear los contenedores

#### Scenario: Clave de cifrado de n8n ausente
- **WHEN** `docker/.env` no define la clave de cifrado de n8n y se ejecuta `docker compose up`
- **THEN** compose termina con un error que nombra la variable faltante, sin crear los contenedores

#### Scenario: Activar Swagger desde .env
- **WHEN** se define `API_DOCS_ENABLED=true` en `docker/.env` y se recrea el servicio del backend
- **THEN** `/swagger-ui.html` está disponible en el backend desplegado

#### Scenario: URLs públicas de n8n
- **WHEN** `docker/.env` define el dominio de n8n y se despliega
- **THEN** las URLs de webhook que muestra el editor de n8n usan `https://` y ese dominio

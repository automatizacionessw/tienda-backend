# Spec Delta

## MODIFIED Requirements

### Requirement: Orquestación con Docker Compose
`docker/docker-compose.yml` MUST definir un servicio de PostgreSQL, un servicio del backend y un servicio de n8n.
- El backend MUST conectarse a la base de datos de ese compose y MUST arrancar recién cuando la base de datos acepte conexiones.
- n8n MUST alcanzar al backend por la red interna del compose, sin pasar por ninguna dirección del host.
- PostgreSQL y n8n MUST NOT publicar puertos en el host. n8n MUST exponer su puerto HTTP solo en la red de contenedores, para que el enrutamiento de su dominio lo haga el proxy de Dokploy.
- El backend MUST publicar su puerto HTTP en el host **solo en la dirección IP configurada** para ello, que en el servidor es la IP interna. Si no se configura ninguna, MUST publicarse solo en `127.0.0.1`. El backend MUST NOT ser accesible desde la red pública.

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

#### Scenario: Backend solo en la IP interna
- **WHEN** `docker/.env` define como IP de publicación del backend la IP interna del servidor y el stack está en ejecución
- **THEN** el puerto del backend escucha solo en esa IP
- **AND** desde la red interna, `GET http://<ip-interna>:8080/actuator/health` responde con `"status":"UP"`
- **AND** el puerto del backend no responde en la IP pública del servidor

#### Scenario: Sin IP configurada
- **WHEN** `docker/.env` no define la IP de publicación del backend y el stack está en ejecución
- **THEN** el puerto del backend escucha solo en `127.0.0.1` del host

### Requirement: Configuración del despliegue mediante .env
El compose MUST tomar su configuración del archivo `docker/.env`:
- credenciales de la base de datos;
- variables de Telegram;
- activación de la documentación;
- estrategia de esquema;
- IP y puerto del host en los que se publica el backend;
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
- **THEN** `/swagger-ui.html` está disponible en el backend desplegado, desde la red interna

#### Scenario: URLs públicas de n8n
- **WHEN** `docker/.env` define el dominio de n8n y se despliega
- **THEN** las URLs de webhook que muestra el editor de n8n usan `https://` y ese dominio

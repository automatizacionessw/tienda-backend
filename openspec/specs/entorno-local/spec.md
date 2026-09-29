# entorno-local Specification

## Purpose

Permite levantar en la máquina de un desarrollador o evaluador la base de datos y n8n, y opcionalmente también el backend, con un solo comando y sin configuración previa.

## Requirements

### Requirement: Compose de desarrollo con dos modos
El repositorio MUST incluir `docker/local/docker-compose.yml` con dos modos:
- **desarrollo**, el modo por defecto: levanta PostgreSQL y n8n. El backend se ejecuta fuera de Docker, en el IDE o con `./mvnw spring-boot:run`.
- **completo**: además construye el backend desde `docker/Dockerfile` y lo levanta conectado a esa base de datos.

Los dos modos MUST levantarse con el mismo comando, `docker compose -f docker/local/docker-compose.yml up -d --build --wait`. El modo MUST elegirse desde `docker/local/.env`. Ese archivo MUST ser opcional: si no existe, rige el modo desarrollo.

#### Scenario: Modo desarrollo sin configuración
- **WHEN** `docker/local/.env` no existe y se ejecuta el comando de arranque
- **THEN** quedan saludables los servicios de PostgreSQL y n8n
- **AND** no se crea ningún contenedor del backend

#### Scenario: Modo completo
- **WHEN** `docker/local/.env` activa el modo completo según `docker/local/.env.example` y se ejecuta el mismo comando
- **THEN** quedan saludables PostgreSQL, n8n y el backend
- **AND** `GET http://localhost:8080/actuator/health` responde con `"status":"UP"` desde el host

### Requirement: Puertos locales solo en loopback
El compose de desarrollo MUST publicar PostgreSQL (5432), n8n (5678) y, en modo completo, el backend (8080) solo en `127.0.0.1` del host. Cada puerto del host MUST poder cambiarse con una variable de `docker/local/.env`.

#### Scenario: Servicios no expuestos a la red
- **WHEN** el entorno local está en ejecución
- **THEN** los puertos publicados escuchan solo en `127.0.0.1`

#### Scenario: Puerto ocupado
- **WHEN** el host ya usa el puerto 5432 y `docker/local/.env` asigna otro puerto a PostgreSQL
- **THEN** el entorno levanta y PostgreSQL queda accesible en el puerto indicado

### Requirement: Backend del IDE conectado sin configuración
La base de datos local MUST usar credenciales de desarrollo fijas. `src/main/resources/application-local.properties.example` MUST apuntar a esa base de datos, de modo que una copia sin editar conecte el backend del IDE al entorno local.

#### Scenario: Copia directa del ejemplo
- **WHEN** el entorno local está en modo desarrollo, se copia `application-local.properties.example` como `application-local.properties` sin editarlo y se arranca el backend con `./mvnw spring-boot:run`
- **THEN** el backend arranca conectado a la base de datos local

### Requirement: n8n alcanza al backend en ambos modos
En los dos modos, `TIENDA_BACKEND_URL` en n8n MUST apuntar a un backend alcanzable:
- en modo desarrollo, el backend que corre en el host;
- en modo completo, el contenedor del backend.

`docker/local/.env.example` MUST indicar qué valores cambiar para pasar al modo completo.

#### Scenario: Backend del IDE desde n8n
- **WHEN** el entorno está en modo desarrollo y el backend corre en el host en el puerto 8080
- **THEN** una petición desde n8n a `{{ $env.TIENDA_BACKEND_URL }}/actuator/health` responde con `"status":"UP"`

#### Scenario: Backend en contenedor desde n8n
- **WHEN** el entorno está en modo completo
- **THEN** una petición desde n8n a `{{ $env.TIENDA_BACKEND_URL }}/actuator/health` responde con `"status":"UP"`

### Requirement: Backend local con valores de desarrollo
En modo completo, el backend MUST arrancar con la documentación de la API habilitada y el bot de Telegram deshabilitado. El bot MUST poder habilitarse en modo polling desde `docker/local/.env`, indicando un token.

#### Scenario: Swagger disponible
- **WHEN** el entorno está en modo completo con los valores por defecto
- **THEN** `http://localhost:8080/swagger-ui.html` está disponible
- **AND** el bot de Telegram no consume actualizaciones

### Requirement: Entorno local aislado del despliegue
El compose de desarrollo MUST NOT leer `docker/.env`, que es el archivo de variables del despliegue. Sus contenedores y volúmenes MUST tener un nombre de proyecto propio, distinto del que usa el stack de despliegue levantado localmente. Git MUST ignorar `docker/local/.env`.

#### Scenario: Convivencia con el .env de despliegue
- **WHEN** existe `docker/.env` con credenciales de despliegue y se levanta el entorno local
- **THEN** la base de datos local usa las credenciales de desarrollo fijas y no las de `docker/.env`

#### Scenario: Archivo local ignorado
- **WHEN** se crea `docker/local/.env` y se ejecuta `git status`
- **THEN** el archivo no aparece entre los cambios ni entre los archivos sin seguimiento

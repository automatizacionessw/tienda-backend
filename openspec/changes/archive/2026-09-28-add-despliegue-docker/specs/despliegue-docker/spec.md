# Spec Delta

## Purpose

Permite construir el backend como imagen Docker y desplegarlo junto a su base de datos PostgreSQL con Docker Compose en un servidor gestionado por Dokploy, configurado mediante variables de entorno.

## ADDED Requirements

### Requirement: Artefactos de despliegue agrupados
Todos los archivos de despliegue con Docker (Dockerfile, reglas de exclusión del build, compose, plantilla de variables y guía de despliegue) MUST estar en la carpeta `docker/` de la raíz del repositorio.

#### Scenario: Ubicación de los archivos
- **WHEN** se lista el contenido de `docker/`
- **THEN** contiene el Dockerfile, sus reglas de exclusión, `docker-compose.yml`, `.env.example` y la guía de despliegue
- **AND** no hay archivos de Docker fuera de esa carpeta

### Requirement: Imagen Docker del backend
El repositorio MUST permitir construir una imagen ejecutable del backend con Java 17 sin tener Maven ni JDK instalados en el host. La imagen MUST NOT contener `application-local.properties`, archivos `.env`, el directorio `data/` ni artefactos de `target/` del host.

#### Scenario: Construcción de la imagen
- **WHEN** se construye la imagen desde la raíz del repositorio con el Dockerfile de `docker/`
- **THEN** la construcción termina correctamente en un host que solo tiene Docker
- **AND** la imagen resultante no contiene `application-local.properties`, aunque exista en el árbol de trabajo

### Requirement: Orquestación con Docker Compose
`docker/docker-compose.yml` MUST definir un servicio de PostgreSQL y un servicio del backend. El backend MUST conectarse a la base de datos de ese compose y MUST arrancar recién cuando la base de datos acepte conexiones. Ningún servicio MUST publicar puertos en el host: el backend MUST exponer el puerto HTTP solo en la red de contenedores, para que el enrutamiento lo haga el proxy de Dokploy.

#### Scenario: Levantar el stack
- **WHEN** se crea `docker/.env` a partir de `docker/.env.example` con valores válidos y se ejecuta `docker compose -f docker/docker-compose.yml up -d`
- **THEN** ambos servicios quedan saludables
- **AND** desde la red del compose, `GET http://backend:8080/api/productos` responde 200

#### Scenario: Base de datos no expuesta
- **WHEN** el stack está en ejecución
- **THEN** el puerto de PostgreSQL no queda publicado en el host

### Requirement: Persistencia de datos
Los datos de PostgreSQL y los archivos que guarda la aplicación (notas de voz) MUST persistir en volúmenes con nombre de Docker, de modo que sobrevivan a redeploys y reinicios y se puedan respaldar con Dokploy.

#### Scenario: Persistencia tras redeploy
- **WHEN** se ejecuta `docker compose down` sin `-v` y luego se vuelve a levantar el stack
- **THEN** los registros de la base de datos y los archivos guardados siguen disponibles

### Requirement: Configuración del despliegue mediante .env
El compose MUST tomar su configuración (credenciales de la base de datos, variables de Telegram, activación de la documentación, estrategia de esquema) del archivo `docker/.env`. Las variables obligatorias MUST hacer fallar el despliegue con un mensaje claro si faltan. El repositorio MUST incluir `docker/.env.example` con todas las variables documentadas, y git MUST ignorar `docker/.env`.

#### Scenario: Variable obligatoria ausente
- **WHEN** `docker/.env` no define la contraseña de la base de datos y se ejecuta `docker compose up`
- **THEN** compose termina con un error que nombra la variable faltante, sin crear los contenedores

#### Scenario: Activar Swagger desde .env
- **WHEN** se define `API_DOCS_ENABLED=true` en `docker/.env` y se recrea el servicio del backend
- **THEN** `/swagger-ui.html` está disponible en el backend desplegado

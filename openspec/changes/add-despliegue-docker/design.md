# Design

## Context

- `application.properties` está en `.gitignore` desde `41deab6`. Cada desarrollador tiene su copia con credenciales reales; en el repo solo existe `application.properties.example`.
- Spring Boot 4.1.1, Java 17, PostgreSQL. El esquema lo gestiona Hibernate (`ddl-auto=update`): no hay Flyway ni Liquibase.
- Ya se incluye `spring-boot-starter-actuator`, así que `/actuator/health` está disponible sin cambios.
- `TelegramBotProperties` ya tolera la URL y el secret del webhook vacíos cuando el bot está deshabilitado o en modo POLLING, y `AlmacenamientoLocalProperties` usa `./data/archivos` por defecto. No hace falta tocar Java.
- `TiendaBackendApplicationTests.contextLoads()` levanta el contexto completo y necesita una base de datos real.
- Dokploy: clona el repo en cada deploy, escribe las variables de su UI en un `.env` junto al compose (sin inyectarlas en los contenedores), agrega labels de Traefik para los dominios configurados en la UI, y solo respalda volúmenes con nombre.

## Goals / Non-Goals

**Goals:**
- Un único `application.properties` versionado que sirva, sin cambios, para el contenedor (vía variables de entorno) y como base para desarrollo.
- Que el flujo del desarrollador siga siendo `./mvnw spring-boot:run` / `./mvnw test` sin pasos extra.
- Un despliegue en Dokploy que solo requiera configurar Compose Path, variables y dominio.

**Non-Goals:**
- Usar el compose para desarrollo local (sin override de compose para dev ni hot reload).
- Migraciones de esquema versionadas (Flyway o Liquibase).
- CI/CD, publicación de imágenes en un registry o réplicas múltiples.
- Rotar la contraseña de base de datos expuesta en el historial.

## Decisions

### 1. Placeholders con nombres propios en `application.properties`
Cada propiedad dependiente del entorno usa `${VAR:default}`:

| Propiedad | Variable | Default |
|---|---|---|
| `spring.datasource.url` | `DB_URL` | `jdbc:postgresql://localhost:5432/tienda_db` |
| `spring.datasource.username` | `DB_USERNAME` | `postgres` |
| `spring.datasource.password` | `DB_PASSWORD` | *(vacío)* |
| `spring.jpa.hibernate.ddl-auto` | `JPA_DDL_AUTO` | `update` |
| `spring.jpa.show-sql` | `JPA_SHOW_SQL` | `false` |
| `telegram.bot.habilitado` | `TELEGRAM_BOT_ENABLED` | `false` |
| `telegram.bot.token` | `TELEGRAM_BOT_TOKEN` | *(vacío)* |
| `telegram.bot.modo` | `TELEGRAM_BOT_MODE` | `POLLING` |
| `telegram.bot.webhook.url` | `TELEGRAM_WEBHOOK_URL` | *(vacío)* |
| `telegram.bot.webhook.secret` | `TELEGRAM_WEBHOOK_SECRET` | *(vacío)* |
| `almacenamiento.local.directorio` | `STORAGE_DIR` | `./data/archivos` |
| `springdoc.api-docs.enabled` y `springdoc.swagger-ui.enabled` | `API_DOCS_ENABLED` (una sola para las dos) | `false` |

- **Por qué:** los nombres cortos documentan en un solo lugar qué es configurable. Con una sola variable `API_DOCS_ENABLED` para las dos propiedades de springdoc, no se pueden desalinear (el README advierte que deben tener el mismo valor). Se conservan `TELEGRAM_BOT_TOKEN` y `TELEGRAM_WEBHOOK_SECRET`, que ya existían.
- **Alternativa descartada:** relaxed binding de Spring (`SPRING_DATASOURCE_URL`, etc.) sin placeholders. Funciona sin tocar el archivo, pero los nombres son largos, `springdoc` necesitaría dos variables y el archivo no muestra qué es configurable.

### 2. Override local con `spring.config.import=optional:classpath:application-local.properties`
- El archivo vive en `src/main/resources/`, junto a `application.properties`. Para migrar basta con renombrar la copia local actual.
- `optional:` hace que su ausencia no sea error. En Spring Boot, las propiedades importadas tienen precedencia sobre las del archivo que las importa.
- Se carga desde cualquier directorio de trabajo (IDE, `mvnw`, tests), porque el classpath no depende del directorio actual.
- **Alternativas descartadas:**
  - Perfil `local`: requiere activarlo a mano, y si se olvida los tests fallan contra `localhost`.
  - `optional:file:./application-local.properties`: depende del directorio de trabajo y, si el IDE usa otro, falla en silencio.
- **Matiz de precedencia:** si el override local asigna un valor literal (`spring.datasource.url=...`), ese valor reemplaza al placeholder y `DB_URL` deja de tener efecto en esa máquina. Las variables con relaxed binding (`SPRING_DATASOURCE_URL`) siguen ganando sobre ambos archivos. Se documenta en el ejemplo.

### 3. Carpeta `docker/` con el contexto de build en la raíz
- `docker-compose.yml` declara `build: { context: .., dockerfile: docker/Dockerfile }`, porque el build necesita `pom.xml` y `src/`.
- Las exclusiones van en `docker/Dockerfile.dockerignore`: BuildKit usa `<Dockerfile>.dockerignore` junto al Dockerfile en lugar del `.dockerignore` de la raíz del contexto. Así la raíz no se ensucia. Se excluyen `**/application-local.properties`, `**/.env`, `target/`, `data/`, `.git/`, `.idea/`, `.vscode/` y `openspec/`.
- **Alternativa descartada:** `.dockerignore` en la raíz, que contradice la decisión de agrupar todo en `docker/`.

### 4. Dockerfile multi-stage
- **Build:** `maven:3.9-eclipse-temurin-17`. Primero se copia `pom.xml` y se ejecuta `mvn dependency:go-offline`, para cachear dependencias en su propia capa. Después se copia `src/` y se ejecuta `mvn package -DskipTests`.
- **Runtime:** `eclipse-temurin:17-jre-alpine`, con usuario no root. El directorio `/app/data/archivos` pertenece a ese usuario y se arranca con `java -jar app.jar`.
- **Por qué la imagen de Maven y no `./mvnw`:** en checkouts de Windows `mvnw` puede quedar con CRLF o sin permiso de ejecución, y `maven-wrapper.jar` está en `.gitignore`. La imagen oficial evita esos problemas.
- **Por qué `-DskipTests`:** `contextLoads()` necesita una base de datos, que no existe durante el build. Los tests se corren en desarrollo.
- **Por qué alpine:** trae `wget` (busybox), que el healthcheck usa sin instalar nada.

### 5. Compose orientado a Dokploy
- **Servicio `db`:** `postgres:17-alpine`; `POSTGRES_DB`, `POSTGRES_USER` y `POSTGRES_PASSWORD` desde `.env`; volumen con nombre `pgdata`; healthcheck `pg_isready`.
- **Servicio `backend`:** `depends_on: db (condition: service_healthy)`; `expose: ["8080"]`, sin `ports`; volumen con nombre `archivos` en `/app/data/archivos`; healthcheck `wget -qO- http://localhost:8080/actuator/health`; `restart: unless-stopped` en ambos servicios.
- **Mapeo explícito de variables** en `environment:` (no `env_file`). `DB_URL=jdbc:postgresql://db:5432/${POSTGRES_DB}`, y `DB_USERNAME` y `DB_PASSWORD` se derivan de las mismas variables que usa `db`, así hay una sola fuente para las credenciales. `backend` no recibe variables que no usa.
- **Variables obligatorias** con `${VAR:?mensaje}` (`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`). Las opcionales usan `${VAR:-default}`.
- **Red:** no se declara `dokploy-network`; se recomienda activar "Isolated Deployments" en Dokploy, que conecta los servicios a Traefik sin editar el compose. El dominio se asigna en la UI, apuntando a `backend:8080`.
- **Alternativa descartada:** `ports: 8080:8080`, que expone el backend en el host y se salta Traefik.

### 6. Documentación de la decisión
- `README.md`, sección "Configuración": el esquema de tres capas (versionado → override local → variables de entorno), cómo crear `application-local.properties` desde el ejemplo y la nota de migración.
- `docker/README.md`: los pasos en Dokploy (Compose Path `./docker/docker-compose.yml`, variables, dominio, volúmenes y backups) y una prueba local con `docker compose`.
- El porqué de las decisiones queda en este design (y en la spec archivada).

## Risks / Trade-offs

- [Un desarrollador hace pull sin renombrar su `application.properties`] → git bloquea el merge ("would be overwritten"); no hay pérdida silenciosa. La nota de migración del README da el comando exacto, y el PR la destaca.
- [Un desarrollador agrega credenciales directamente en el `application.properties` versionado] → los comentarios del archivo indican usar el override local, y en la revisión del PR se verifica que no haya valores reales.
- [`./mvnw package` local incluye `application-local.properties` en el jar] → ese jar no se distribuye; la imagen se construye desde fuentes y excluye el archivo.
- [`ddl-auto=update` en producción puede aplicar cambios de esquema no deseados] → es el comportamiento actual; queda configurable con `JPA_DDL_AUTO` (por ejemplo `validate`) y se registra como deuda técnica: adoptar Flyway.
- [Sin SSL entre backend y db] → el tráfico no sale de la red interna del compose; el `sslmode=require` de la plantilla anterior no aplica.
- [La contraseña de BD sigue en el historial de git] → hay que rotarla; es una acción aparte.

## Migration Plan

1. Cada desarrollador, **antes** de hacer pull, renombra su copia local (no versionada) de `src/main/resources/application.properties` a `application-local.properties`.
2. Merge del cambio y pull: el `application.properties` versionado aparece; `./mvnw test` debe pasar usando el override.
3. En Dokploy: crear un servicio Compose desde el repo o rama, Compose Path `./docker/docker-compose.yml`, cargar las variables según `docker/.env.example`, activar Isolated Deployments, asignar el dominio a `backend` puerto 8080 y desplegar.
4. **Rollback:** redeploy del commit anterior en Dokploy. Los volúmenes con nombre se mantienen.

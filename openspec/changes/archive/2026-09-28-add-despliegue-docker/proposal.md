# Proposal

## Why

Hoy el backend solo se ejecuta con `./mvnw spring-boot:run` contra un PostgreSQL que cada desarrollador configura a mano, y `application.properties` está fuera del control de versiones porque cada persona escribe ahí sus credenciales. No existe una configuración base compartida ni una forma reproducible de desplegar. Se quiere desplegar en un servidor con Dokploy usando Docker Compose y configurar el despliegue con variables de entorno (`.env`). Para eso la configuración base tiene que volver al repositorio, sin secretos.

## What Changes

- `src/main/resources/application.properties` vuelve al control de versiones. Todas las propiedades que dependen del entorno se leen de variables de entorno (`${VAR:default}`), con valores por defecto seguros: sin credenciales, bot de Telegram deshabilitado, Swagger apagado y `show-sql` apagado.
- Nuevo override local `src/main/resources/application-local.properties`, ignorado por git. Se carga automáticamente si existe (sin activar perfiles) y tiene precedencia sobre `application.properties`. Se versiona `application-local.properties.example` como plantilla.
- Se elimina `application.properties.example`; su función la cubren `application.properties` y el nuevo ejemplo local.
- Nueva carpeta `docker/` en la raíz con `Dockerfile` (multi-stage, Java 17), `Dockerfile.dockerignore`, `docker-compose.yml` (servicios `db` PostgreSQL y `backend`), `.env.example` y `README.md` con la guía de despliegue en Dokploy.
- El compose está pensado para Dokploy: usa `expose` en lugar de `ports` (el dominio se asigna desde Dokploy/Traefik), volúmenes con nombre para la base de datos y para los archivos almacenados, y healthchecks para que `backend` arranque recién cuando `db` esté disponible.
- `.gitignore`: se deja de ignorar `application.properties` y se ignoran `application-local.properties` y `docker/.env`.
- README: la estrategia de configuración (base versionada + variables de entorno + override local), la guía de migración y un enlace a `docker/README.md`.
- **BREAKING (desarrolladores)**: antes de traer este cambio, cada desarrollador debe renombrar su `application.properties` local a `application-local.properties`. Si no lo hace, git bloquea el pull o las credenciales pueden terminar en un commit.

## Capabilities

### New Capabilities
- `configuracion-entorno`: la configuración base versionada se resuelve desde variables de entorno con valores por defecto seguros y admite overrides locales no versionados que se cargan automáticamente.
- `despliegue-docker`: la aplicación y su base de datos se construyen y despliegan con Docker Compose en Dokploy, configuradas mediante `.env`.

### Modified Capabilities
<!-- ninguna: documentacion-api y telegram-recepcion mantienen sus requisitos; solo cambia de dónde salen sus valores -->

## Impact

- **Archivos:** `.gitignore`, `src/main/resources/application.properties` (vuelve a versionarse), `application.properties.example` (se elimina), `application-local.properties.example` (nuevo), `docker/*` (nuevo), `README.md`.
- **Código Java:** no cambia; `TelegramBotProperties` y `AlmacenamientoLocalProperties` siguen enlazando las mismas propiedades.
- **Tests:** `@SpringBootTest` va a cargar el `application-local.properties` del desarrollador, igual que hoy carga su `application.properties`.
- **Operación:** en Dokploy hay que configurar Compose Path, variables de entorno y dominio.
- **Seguridad:** el historial de git contiene una contraseña de base de datos anterior a `41deab6`. Hay que rotarla; esa tarea queda fuera del alcance de este cambio.

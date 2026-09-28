# Despliegue con Docker (Dokploy)

Esta carpeta contiene todo lo necesario para desplegar el backend y su base de datos PostgreSQL con Docker Compose.

| Archivo | Descripción |
|---|---|
| `Dockerfile` | Imagen multi-stage: compila con Maven y JDK 17, y ejecuta con JRE 17 (Alpine) usando un usuario no root |
| `Dockerfile.dockerignore` | Exclusiones del contexto de build. `application-local.properties` y los `.env` nunca entran en la imagen |
| `docker-compose.yml` | Servicios `db` (PostgreSQL 17) y `backend` |
| `.env.example` | Plantilla con todas las variables del despliegue |

El contexto de build es la **raíz del repositorio** (`context: ..`), porque la imagen necesita `pom.xml` y `src/`.

## Cómo está armado

```
 Dokploy UI (Environment) --> docker/.env --> ${VAR} en docker-compose.yml
 Dokploy UI (Domains)     --> Traefik --> backend:8080
                                             |
                                             v
                                           db:5432   (solo en la red interna)
```

- **Sin puertos publicados:** `backend` usa `expose: 8080` y Traefik (Dokploy) enruta el dominio hacia él. PostgreSQL no es accesible desde fuera.
- **Credenciales en un solo lugar:** `POSTGRES_DB`, `POSTGRES_USER` y `POSTGRES_PASSWORD` configuran la base de datos y también la conexión del backend.
- **Variables obligatorias:** si falta alguna de las tres anteriores, `docker compose` se detiene con un error que la nombra.
- **Arranque ordenado:** `backend` espera a que `db` pase su healthcheck (`pg_isready`). A su vez, `backend` se reporta sano con `/actuator/health`.
- **Persistencia:** volúmenes con nombre `pgdata` (datos de PostgreSQL) y `archivos` (notas de voz del bot). Sobreviven a redeploys y se pueden respaldar desde Dokploy, que solo respalda volúmenes con nombre.

## Desplegar en Dokploy

1. Crea un servicio de tipo **Compose** apuntando a este repositorio y a la rama que quieras desplegar.
2. En **Compose Path**, indica `./docker/docker-compose.yml`.
3. En **Environment**, carga las variables siguiendo [`.env.example`](.env.example). Como mínimo, define `POSTGRES_DB`, `POSTGRES_USER` y un `POSTGRES_PASSWORD` seguro. Dokploy las escribe en `docker/.env` al desplegar.
4. Activa **Isolated Deployments**, que conecta los servicios con Traefik sin editar el compose.
5. En **Domains**, agrega tu dominio apuntando al servicio `backend`, puerto `8080`, con HTTPS.
6. Pulsa **Deploy**. Para comprobarlo, `https://<tu-dominio>/actuator/health` debe responder `{"status":"UP"}`.

Para habilitar el bot en modo webhook: `TELEGRAM_BOT_ENABLED=true`, `TELEGRAM_BOT_MODE=WEBHOOK`, `TELEGRAM_WEBHOOK_URL=https://<tu-dominio>/telegram/webhook` y un `TELEGRAM_WEBHOOK_SECRET`. Usa un bot distinto al de desarrollo.

**Rollback:** redespliega un commit anterior desde Dokploy. Los volúmenes se conservan.

> `JPA_DDL_AUTO=update` deja que Hibernate modifique el esquema al arrancar. Es el comportamiento actual del proyecto, porque aún no hay migraciones versionadas. Si prefieres que el esquema no cambie solo, usa `validate`.

## Probar localmente

Desde la raíz del repositorio:

```bash
cp docker/.env.example docker/.env      # y edita POSTGRES_PASSWORD
docker compose -f docker/docker-compose.yml up -d --build --wait
docker compose -f docker/docker-compose.yml ps
```

Como no se publican puertos, prueba desde dentro del contenedor:

```bash
docker compose -f docker/docker-compose.yml exec backend wget -qO- http://localhost:8080/api/productos
```

Para detenerlo, usa `docker compose -f docker/docker-compose.yml down`. Si agregas `-v`, también borras los volúmenes y con ellos los datos.

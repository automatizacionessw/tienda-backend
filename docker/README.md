# Docker: despliegue (Dokploy) y entorno local

Esta carpeta contiene todo lo necesario para desplegar el backend, su base de datos PostgreSQL y una instancia de n8n con Docker Compose, y para levantar un entorno de desarrollo local.

| Archivo | Descripción |
|---|---|
| `Dockerfile` | Imagen multi-stage: compila con Maven y JDK 17, y ejecuta con JRE 17 (Alpine) usando un usuario no root |
| `Dockerfile.dockerignore` | Exclusiones del contexto de build. `application-local.properties` y los `.env` nunca entran en la imagen |
| `docker-compose.yml` | Despliegue: servicios `db` (PostgreSQL 17), `backend` y `n8n` |
| `.env.example` | Plantilla con todas las variables del despliegue |
| `local/docker-compose.yml` | Entorno local: `db` y `n8n`, y `backend` en modo completo |
| `local/.env.example` | Plantilla opcional del entorno local (modo, puertos, bot) |

El contexto de build es la **raíz del repositorio**, porque la imagen necesita `pom.xml` y `src/`.

## Cómo está armado el despliegue

```
 Dokploy UI (Environment) --> docker/.env --> ${VAR} en docker-compose.yml
 Dokploy UI (Domains)     --> Traefik --+--> backend:8080   (dominio del backend)
                                        +--> n8n:5678       (dominio de n8n)
                                               |
      red interna del compose                  |  TIENDA_BACKEND_URL=http://backend:8080
      +----------------------------------------+-------------+
      |  db:5432  <-- backend  <-- n8n                        |
      +-------------------------------------------------------+
```

- **Sin puertos publicados:** `backend` y `n8n` usan `expose` y Traefik (Dokploy) enruta un dominio hacia cada uno. PostgreSQL no es accesible desde fuera.
- **Credenciales en un solo lugar:** `POSTGRES_DB`, `POSTGRES_USER` y `POSTGRES_PASSWORD` configuran la base de datos y también la conexión del backend.
- **Variables obligatorias:** si falta `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `N8N_ENCRYPTION_KEY` o `N8N_HOST`, `docker compose` se detiene con un error que la nombra.
- **Arranque ordenado:** `backend` espera a que `db` pase su healthcheck (`pg_isready`). A su vez, `backend` se reporta sano con `/actuator/health` y `n8n` con `/healthz`.
- **Persistencia:** volúmenes con nombre `pgdata` (datos de PostgreSQL), `archivos` (notas de voz del bot) y `n8n_data` (workflows, credenciales, ejecuciones y usuarios de n8n, en SQLite). Sobreviven a redeploys y se pueden respaldar desde Dokploy, que solo respalda volúmenes con nombre.

### n8n

- **Versión fija** (`n8nio/n8n:2.40.7`), la misma en el compose local. Actualizar n8n es un cambio explícito en el repositorio.
- **`N8N_ENCRYPTION_KEY`** cifra las credenciales guardadas en n8n. Hay que generarla una sola vez (`openssl rand -hex 32`), guardarla junto a los demás secretos y **no cambiarla después**, porque las credenciales guardadas dejarían de poder descifrarse.
- **`N8N_HOST`** es el dominio público de n8n. Con él se arman la URL del editor y la de los webhooks, siempre con `https://`.
- **`TIENDA_BACKEND_URL`:** los workflows llegan al backend con `{{ $env.TIENDA_BACKEND_URL }}`, que en el despliegue vale `http://backend:8080` (red interna, sin salir a internet). Para eso el compose habilita `$env` en los workflows (`N8N_BLOCK_ENV_ACCESS_IN_NODE=false`). Como consecuencia, cualquier workflow puede leer todas las variables del contenedor de n8n, así que ese contenedor **nunca** recibe secretos del backend (base de datos, Telegram).
- **Telemetría** de n8n deshabilitada.
- **Memoria:** n8n suma unos 300-500 MB. Revisa el margen del servidor antes del primer deploy. Si no alcanza, el servicio `n8n` puede detenerse desde Dokploy sin afectar al backend.

## Desplegar en Dokploy

1. Crea un servicio de tipo **Compose** apuntando a este repositorio y a la rama que quieras desplegar.
2. En **Compose Path**, indica `./docker/docker-compose.yml`.
3. En **Environment**, carga las variables siguiendo [`.env.example`](.env.example). Como mínimo:
   - `POSTGRES_DB`, `POSTGRES_USER` y un `POSTGRES_PASSWORD` seguro;
   - `N8N_ENCRYPTION_KEY` (generada con `openssl rand -hex 32`) y `N8N_HOST`, el dominio de n8n.

   Dokploy las escribe en `docker/.env` al desplegar.
4. Activa **Isolated Deployments**, que conecta los servicios con Traefik sin editar el compose.
5. En **Domains**, agrega dos dominios con HTTPS:
   - el del backend, apuntando al servicio `backend`, puerto `8080`;
   - el de n8n (el mismo valor que `N8N_HOST`), apuntando al servicio `n8n`, puerto `5678`.
6. Pulsa **Deploy**. Para comprobarlo, `https://<dominio-backend>/actuator/health` debe responder con `"status":"UP"`.
7. **De inmediato**, abre `https://<dominio-n8n>` y crea la cuenta owner.

> **Importante:** n8n deja crear la cuenta owner al primer visitante del editor. Si el dominio queda público sin owner, cualquiera puede tomar la instancia. Crea el owner apenas termina el deploy, o asigna el dominio de n8n recién cuando vayas a crearlo.

Para habilitar el bot en modo webhook: `TELEGRAM_BOT_ENABLED=true`, `TELEGRAM_BOT_MODE=WEBHOOK`, `TELEGRAM_WEBHOOK_URL=https://<dominio-backend>/telegram/webhook` y un `TELEGRAM_WEBHOOK_SECRET`. Usa un bot distinto al de desarrollo.

**Actualizar un despliegue anterior a n8n:** antes de redesplegar, agrega `N8N_ENCRYPTION_KEY` y `N8N_HOST` en Environment y el dominio de n8n en Domains. Sin esas variables, el deploy falla con un error que las nombra.

**Rollback:** redespliega un commit anterior desde Dokploy. Los volúmenes se conservan. Si el commit es anterior a n8n, el servicio desaparece, pero su volumen `n8n_data` queda con los datos por si se vuelve a desplegar.

> `JPA_DDL_AUTO=update` deja que Hibernate modifique el esquema al arrancar. Es el comportamiento actual del proyecto, porque aún no hay migraciones versionadas. Si prefieres que el esquema no cambie solo, usa `validate`.

## Probar el despliegue localmente

Desde la raíz del repositorio:

```bash
cp docker/.env.example docker/.env      # y edita POSTGRES_PASSWORD y N8N_ENCRYPTION_KEY
docker compose -f docker/docker-compose.yml up -d --build --wait
docker compose -f docker/docker-compose.yml ps
```

Como no se publican puertos, prueba desde dentro de los contenedores:

```bash
docker compose -f docker/docker-compose.yml exec backend wget -qO- http://localhost:8080/api/productos
docker compose -f docker/docker-compose.yml exec n8n node -e "fetch('http://backend:8080/actuator/health').then(r=>r.text()).then(console.log)"
```

Para detenerlo, usa `docker compose -f docker/docker-compose.yml down`. Si agregas `-v`, también borras los volúmenes y con ellos los datos. Para desarrollar, usa en cambio el entorno local.

## Entorno local

`local/docker-compose.yml` levanta un entorno de desarrollo con un solo comando, sin configuración previa:

```bash
docker compose -f docker/local/docker-compose.yml up -d --build --wait
```

| Modo | Servicios | Para quién |
|---|---|---|
| **desarrollo** (por defecto) | `db` y `n8n` | Quien corre el backend en el IDE o con `./mvnw spring-boot:run` |
| **completo** | `db`, `n8n` y `backend` (construido desde `docker/Dockerfile`) | Quien quiere levantar todo sin IDE, por ejemplo para evaluar el proyecto o armar workflows |

| Servicio | URL desde tu máquina | Detalle |
|---|---|---|
| PostgreSQL | `localhost:5432` | Base `tienda_db`, usuario `tienda`, contraseña `tienda` |
| n8n | `http://localhost:5678` | En el primer acceso crea tu cuenta owner local |
| backend (modo completo) | `http://localhost:8080` | Swagger en `http://localhost:8080/swagger-ui.html`, bot deshabilitado |

- **Modo desarrollo:** copia `src/main/resources/application-local.properties.example` como `application-local.properties`. Ya apunta a esta base de datos, así que no hace falta editarlo. Los tests (`./mvnw test`) también usan esa base.
- **Modo completo:** copia `docker/local/.env.example` como `docker/local/.env`, descomenta las dos líneas de la sección "Modo completo" (`COMPOSE_PROFILES=completo` y `TIENDA_BACKEND_URL=http://backend:8080`) y ejecuta el mismo comando. Agrega `--build` después de cada cambio de código para reconstruir la imagen. El primer build tarda unos minutos.
- **El puerto 8080 es uno solo:** detén el backend del IDE antes de pasar al modo completo, y viceversa.
- **n8n y el backend:** en modo desarrollo, `TIENDA_BACKEND_URL` vale `http://host.docker.internal:8080` (el backend del IDE, en tu máquina). En modo completo vale `http://backend:8080`. Los workflows usan `{{ $env.TIENDA_BACKEND_URL }}` y funcionan igual en los dos modos y en el despliegue.
- **Puertos ocupados:** en `docker/local/.env`, define `LOCAL_DB_PORT`, `LOCAL_N8N_PORT` o `LOCAL_BACKEND_PORT`. Si cambias el de la base de datos, ajusta también `spring.datasource.url` en tu `application-local.properties`.
- **Bot de Telegram en modo completo:** `TELEGRAM_BOT_ENABLED=true` y `TELEGRAM_BOT_TOKEN` en `docker/local/.env`, siempre en modo polling. No lo corras a la vez desde el IDE con el mismo token.
- **Aislado del despliegue:** este compose lee `docker/local/.env`, nunca `docker/.env`. Sus contenedores y volúmenes llevan el prefijo `tienda-local`. Todos los puertos se publican solo en `127.0.0.1`.

Para detenerlo (incluido el backend, si usaste el modo completo):

```bash
docker compose -f docker/local/docker-compose.yml --profile completo down
```

Sin `--profile completo`, `down` no detiene un backend levantado antes en modo completo. Si agregas `-v`, borras los volúmenes locales: la base de datos, los workflows y la cuenta de n8n.

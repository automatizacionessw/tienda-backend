# Design

## Context

- `docker/docker-compose.yml` (de `add-despliegue-docker`) define `db` (PostgreSQL 17) y `backend`, y está pensado para Dokploy:
  - variables en `docker/.env`, con las obligatorias marcadas con `:?`;
  - `expose` en lugar de `ports`;
  - Traefik enruta los dominios configurados en la UI, y está activo *Isolated Deployments*;
  - volúmenes con nombre.
- Ese cambio dejó como no-objetivo "usar el compose para desarrollo local". Este cambio lo revierte con un compose separado, sin tocar el de despliegue más allá de sumar n8n.
- `docker/Dockerfile.dockerignore` ya excluye `**/.env`, `**/application-local.properties` y `docker/`. El build del backend desde el compose local hereda esas exclusiones.
- Compose resuelve `${VAR}` con el `.env` del **directorio del proyecto**, que por defecto es la carpeta del primer archivo de compose. También lee de ahí variables propias como `COMPOSE_PROFILES`.
- n8n, verificado sobre la versión `2.40.7`, que es la etiqueta `stable` publicada el 2026-09-25:
  - La base de datos por defecto es SQLite en `/home/node/.n8n`, y el contenedor corre como el usuario `node`.
  - `WEBHOOK_URL` está deprecada. Su reemplazo es `N8N_WEBHOOK_URL`. `N8N_EDITOR_BASE_URL` fija la URL pública del editor.
  - `N8N_PROXY_HOPS` vale 0 por defecto, y `N8N_SECURE_COOKIE` vale `true` por defecto, lo que exige HTTPS.
  - `N8N_BLOCK_ENV_ACCESS_IN_NODE`: `$env` en expresiones y en el nodo Code queda bloqueado salvo que la variable valga exactamente `false`. Cuando se desbloquea, las expresiones ven **todo** `process.env` del contenedor.
  - La imagen se construye sobre una base Alpine endurecida que solo agrega `busybox-binsh`. `wget` y `curl` no están garantizados, pero `node` sí.
  - La protección SSRF está apagada por defecto (`N8N_SSRF_PROTECTION_ENABLED=false`), así que n8n puede llamar a `backend:8080` y a `host.docker.internal`.
  - `N8N_DIAGNOSTICS_ENABLED` vale `true` por defecto.

## Goals / Non-Goals

**Goals:**
- Un solo comando para cada entorno:
  - Dokploy despliega db, backend y n8n;
  - en local, el mismo comando levanta el modo desarrollo o el modo completo.
- El compose de despliegue y el local no se pisan: no comparten `.env`, nombre de proyecto ni volúmenes.
- Los workflows que se construyan después usan `$env.TIENDA_BACKEND_URL` y funcionan igual en los tres escenarios:
  - Dokploy;
  - local en modo desarrollo;
  - local en modo completo.

**Non-Goals:**
- Workflows, credenciales de n8n, servidor MCP, disparo desde el backend y exportación o importación versionada de workflows.
- n8n en modo cola (queue mode) con Redis y workers, o sobre PostgreSQL.
- Hot reload del backend dentro de Docker. En el modo completo, un cambio de código se toma reconstruyendo con `--build`.
- Rotación de `N8N_ENCRYPTION_KEY`.

## Decisions

### 1. n8n en el mismo compose de despliegue
El servicio `n8n` vive en `docker/docker-compose.yml`, junto a `db` y `backend`. n8n alcanza al backend por `http://backend:8080` en la red interna, y el cambio que agregue MCP no va a necesitar publicar `/mcp` en internet.
- **Alternativa descartada:** un servicio aparte en Dokploy, con su plantilla de n8n. Tendría su propia red aislada, así que para llegar al backend habría que publicar sus endpoints o editar las redes de Dokploy. Tampoco se reproduce con un solo `docker compose up`.

### 2. SQLite en un volumen con nombre
El volumen `n8n_data` se monta en `/home/node/.n8n` y guarda la base SQLite y la configuración de la instancia.
- **Alternativa descartada:** usar el PostgreSQL existente con otra base de datos. El script de inicialización de la imagen de Postgres solo corre con el volumen vacío. En un despliegue que ya tiene `pgdata` habría que crear la base a mano, y además se acoplaría el ciclo de vida de n8n al de la base del negocio. Para un proyecto académico con un solo usuario, SQLite alcanza.

### 3. Imagen `n8nio/n8n:2.40.7`, igual en los dos compose
Es la versión estable vigente e incluye el MCP Client Tool y el AI Agent con DeepSeek, que van a usar los cambios siguientes. Actualizar n8n pasa a ser un cambio explícito en el repositorio.
- **Alternativa descartada:** `latest` o `stable`. Un redeploy podría cambiar la versión de los nodos sin que nadie lo decida.

### 4. Variables de n8n en el despliegue
| Variable del contenedor | Valor en `docker-compose.yml` | En `docker/.env` |
|---|---|---|
| `N8N_ENCRYPTION_KEY` | `${N8N_ENCRYPTION_KEY:?...}` | **obligatoria** |
| `N8N_HOST` | `${N8N_HOST:?...}` | **obligatoria** (dominio sin esquema) |
| `N8N_PROTOCOL` | `https` | - |
| `N8N_EDITOR_BASE_URL`, `N8N_WEBHOOK_URL` | `https://${N8N_HOST}/` | - |
| `N8N_PROXY_HOPS` | `1` (Traefik) | - |
| `GENERIC_TIMEZONE`, `TZ` | `${N8N_TIMEZONE:-UTC}` | opcional |
| `N8N_DIAGNOSTICS_ENABLED` | `false` | - |
| `N8N_BLOCK_ENV_ACCESS_IN_NODE` | `false` | - |
| `TIENDA_BACKEND_URL` | `http://backend:8080` | - |

- `N8N_HOST` es obligatoria porque, sin dominio, el editor genera URLs de webhook con `localhost`. Ese error no se ve hasta que se intenta usar un webhook.
- Solo se exponen como variables de `.env` los valores que cambian entre servidores. El resto va fijo en el compose, así no se pueden desalinear. Por ejemplo, `N8N_PROTOCOL` y las URLs públicas se derivan de `N8N_HOST`.
- **Alternativa descartada:** `WEBHOOK_URL`, deprecada en 2.x.

### 5. `TIENDA_BACKEND_URL` con `N8N_BLOCK_ENV_ACCESS_IN_NODE=false`
Los workflows leen la URL del backend con `{{ $env.TIENDA_BACKEND_URL }}`, y cada compose le da el valor de su entorno.
- **Consecuencia:** al desbloquear `$env`, cualquier workflow puede leer todas las variables del contenedor de n8n, incluida `N8N_ENCRYPTION_KEY`. Se acota así:
  - n8n recibe solo sus propias variables. Nunca recibe `DB_PASSWORD`, `TELEGRAM_BOT_TOKEN` ni `TELEGRAM_WEBHOOK_SECRET`, y así lo exige la spec.
  - La instancia tiene un único owner, que es quien edita workflows.
- **Alternativas descartadas:**
  - Variables de n8n (`$vars`): requieren una licencia paga.
  - URL escrita en cada workflow: habría que editarlo al cambiar de entorno, y es justo lo que se quiere evitar.

### 6. Healthcheck de n8n con `node`
```
node -e "fetch('http://localhost:5678/healthz').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
```
Usa Node 24, que ya está en la imagen y trae `fetch`.
- **Alternativa descartada:** `wget` o `curl`. La base endurecida no garantiza tenerlos.

### 7. Compose local en `docker/local/`, con su propio `.env`
Al vivir en su propia carpeta, Compose toma `docker/local/.env` y nunca `docker/.env`. Así, un `.env` de despliegue usado para probar el stack de Dokploy no contamina el entorno local.
- El compose declara `name: tienda-local`. Sin eso, el nombre de proyecto sería `local` (el nombre de la carpeta), un nombre genérico propenso a choques. Los volúmenes quedan como `tienda-local_pgdata`, `tienda-local_n8n_data` y `tienda-local_archivos`.
- El build del backend usa `context: ../..` y `dockerfile: docker/Dockerfile`: es el mismo Dockerfile y el mismo `.dockerignore` que en el despliegue.
- **Alternativas descartadas:**
  - `docker/docker-compose.local.yml` junto al de despliegue: comparte el `.env` de la carpeta.
  - Base + override (`-f base -f local`): obliga a sacar `backend` con profiles, a razonar sobre cómo se fusionan las claves, y hace que el archivo local no se entienda solo. La duplicación que evita es de unas 15 líneas.

### 8. Modos con `profiles` y `COMPOSE_PROFILES`
- `backend` lleva `profiles: ["completo"]`. `db` y `n8n` no llevan profile.
- `docker/local/.env.example` trae comentadas:
  ```
  #COMPOSE_PROFILES=completo
  #TIENDA_BACKEND_URL=http://backend:8080
  ```
- En n8n: `TIENDA_BACKEND_URL: ${TIENDA_BACKEND_URL:-http://host.docker.internal:8080}`, con `extra_hosts: ["host.docker.internal:host-gateway"]` para que ese nombre también exista en Linux.
- **Por qué dos líneas y no una:** Compose no permite condicionar una variable al profile activo. La otra opción, usar siempre `host.docker.internal` también en modo completo, funciona en Docker Desktop pero no en Linux, porque un puerto publicado en `127.0.0.1` no se alcanza por `host-gateway`.
- **Alternativa descartada:** pasar variables inline en el comando. La sintaxis cambia entre PowerShell y bash.

### 9. Valores del entorno local
- **`db`:** `tienda_db` / `tienda` / `tienda`, fijos. `application-local.properties.example` pasa a usar esas credenciales.
- **`n8n`:**
  - `N8N_HOST=localhost`, `N8N_PROTOCOL=http`, `N8N_SECURE_COOKIE=false`;
  - `N8N_EDITOR_BASE_URL` y `N8N_WEBHOOK_URL` iguales a `http://localhost:${LOCAL_N8N_PORT:-5678}/`;
  - sin `N8N_ENCRYPTION_KEY`, porque n8n la genera y la guarda en su volumen;
  - `N8N_DIAGNOSTICS_ENABLED=false` y `N8N_BLOCK_ENV_ACCESS_IN_NODE=false`, igual que en el despliegue.
- **`backend` (modo completo):**
  - `DB_URL=jdbc:postgresql://db:5432/tienda_db` con las credenciales fijas;
  - `API_DOCS_ENABLED=true`;
  - `TELEGRAM_BOT_ENABLED=${TELEGRAM_BOT_ENABLED:-false}`, `TELEGRAM_BOT_TOKEN=${TELEGRAM_BOT_TOKEN:-}` y `TELEGRAM_BOT_MODE=POLLING` fijo, porque en local no hay URL pública para el webhook;
  - volumen `archivos`;
  - healthcheck con `wget` sobre `/actuator/health`, igual que en el despliegue.
- **Puertos:**
  - `127.0.0.1:${LOCAL_DB_PORT:-5432}:5432`;
  - `127.0.0.1:${LOCAL_N8N_PORT:-5678}:5678`;
  - `127.0.0.1:${LOCAL_BACKEND_PORT:-8080}:8080`.
- Solo se usan variables `LOCAL_*` más las del bot y las dos del cambio de modo. Ninguna variable del compose de despliegue se reutiliza.

## Risks / Trade-offs

- **[El primer visitante del editor crea la cuenta owner]**
  - En Dokploy, si el dominio de n8n queda público antes de crear el owner, cualquiera puede tomar la instancia.
  - → La guía indica crear el owner apenas termina el deploy. Una alternativa más segura es asignar el dominio y abrirlo en ese mismo momento. Se verifica en las tareas.
- **[`$env` desbloqueado expone `N8N_ENCRYPTION_KEY` a los workflows]**
  - → n8n tiene un solo usuario editor y no recibe secretos del backend (decisión 5).
  - Si en el futuro se suman usuarios con permiso de edición, hay que revisar esta decisión.
- **[Perder `N8N_ENCRYPTION_KEY` hace ilegibles las credenciales guardadas]** → La guía indica guardarla junto a los demás secretos del Environment de Dokploy y no cambiarla después del primer deploy.
- **[Memoria: n8n suma unos 300-500 MB]** → Se revisa el margen del servidor antes del primer deploy. Si no alcanza, n8n puede apagarse desde Dokploy sin afectar al backend.
- **[Puerto 8080 compartido]** → El modo completo y el backend del IDE no pueden correr a la vez. La guía lo menciona.
- **[`--wait` en el primer build]** → El build con Maven puede tardar varios minutos. `start_period` del backend se mantiene en 60 s, porque `--wait` empieza a contar después de que termina el build.
- **[Tests contra la base local]** → `@SpringBootTest` carga `application-local.properties`. Con el ejemplo nuevo, los tests corren contra la base del compose local, y eso es lo esperado.

## Migration Plan

1. **Antes de redesplegar**, cargar en Dokploy (Environment):
   - `N8N_ENCRYPTION_KEY`: un valor aleatorio largo, por ejemplo `openssl rand -hex 32`;
   - `N8N_HOST`: el dominio de n8n;
   - opcionalmente `N8N_TIMEZONE`.
2. En **Domains**, agregar el dominio hacia el servicio `n8n`, puerto `5678`, con HTTPS.
3. Deploy.
4. Abrir el dominio de n8n de inmediato y crear la cuenta owner.
5. **Rollback:** redesplegar el commit anterior.
   - El servicio `n8n` desaparece. El volumen `n8n_data` queda huérfano y conserva los datos por si se vuelve a desplegar.
   - `db` y `backend` no cambian.
6. **Desarrolladores:** quien quiera usar la base local copia de nuevo `application-local.properties.example`, o ajusta la conexión en su copia. Quien siga usando una base remota no necesita hacer nada.

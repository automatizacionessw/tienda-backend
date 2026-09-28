# Proposal

## Why

El proyecto es académico y quiere mostrar automatización de software con n8n y MCP: más adelante un agente en n8n, con DeepSeek, va a clasificar intenciones y a ejecutar funciones del backend a través de un servidor MCP. Lo primero es tener una instancia de n8n que viva junto al backend, tanto en el despliegue de Dokploy como en la máquina de cada desarrollador. Hoy el único compose es el de despliegue: no publica puertos y no sirve para desarrollar ni para que alguien sin IDE levante el proyecto completo.

## What Changes

- `docker/docker-compose.yml` (despliegue) suma un servicio `n8n`:
  - imagen con versión fija;
  - datos en SQLite dentro de un volumen con nombre;
  - clave de cifrado obligatoria;
  - URL pública propia detrás del proxy de Dokploy;
  - sin puertos publicados.
  Desde n8n, el backend se alcanza por la red interna del compose.
- Nuevo compose de desarrollo en `docker/local/`, con su propio `.env` opcional e ignorado por git. Ofrece dos modos:
  - **desarrollo** (por defecto): PostgreSQL y n8n, publicados solo en `127.0.0.1`. El backend corre en el IDE, y n8n lo alcanza en el host.
  - **completo**: además construye y levanta el backend desde el mismo `Dockerfile`, con Swagger activo y el bot deshabilitado por defecto.
  El modo se elige en `docker/local/.env` y los dos se levantan con el mismo comando.
- Ambos compose definen `TIENDA_BACKEND_URL` en n8n, con la URL del backend según el entorno. Así los workflows futuros funcionan sin editarse al cambiar de entorno.
- `docker/.env.example` documenta las variables nuevas de n8n. Se agrega `docker/local/.env.example` y `.gitignore` ignora `docker/local/.env`.
- `application-local.properties.example` apunta a la base de datos del compose local, así que copiarlo sin editar alcanza.
- Guías: `docker/README.md` documenta n8n en Dokploy (dominio y creación inmediata de la cuenta owner) y los dos modos locales. El `README.md` raíz enlaza el entorno local.
- **BREAKING (operación)**: antes de redesplegar en Dokploy hay que definir `N8N_ENCRYPTION_KEY` y `N8N_HOST` en el Environment del servicio. Sin esas variables, el despliegue falla con un error que las nombra.

## Capabilities

### New Capabilities
- `instancia-n8n`: una instancia de n8n con versión fija, persistente, con credenciales cifradas con una clave estable, que conoce la URL del backend de su entorno.
- `entorno-local`: un compose de desarrollo con base de datos y n8n para trabajar con el backend en el IDE, o con el stack completo sin IDE.

### Modified Capabilities
- `despliegue-docker`:
  - los artefactos agrupados incluyen el compose local;
  - la orquestación suma el servicio `n8n`;
  - la persistencia cubre los datos de n8n;
  - el `.env` de despliegue incluye las variables obligatorias de n8n.

## Impact

- **Archivos:**
  - `docker/docker-compose.yml` y `docker/.env.example`;
  - `docker/local/docker-compose.yml` y `docker/local/.env.example` (nuevos);
  - `docker/README.md`, `README.md`, `.gitignore` y `src/main/resources/application-local.properties.example`.
- **Código Java y tests:** no cambian.
- **Operación (Dokploy):**
  - dos variables nuevas obligatorias;
  - un segundo dominio, hacia el servicio `n8n` en el puerto 5678;
  - unos 300-500 MB más de memoria en el servidor.
- **Seguridad:**
  - el primer visitante del editor de n8n crea la cuenta owner, así que hay que crearla apenas se despliega;
  - los workflows pueden leer las variables de entorno del contenedor de n8n, lo que se necesita para `TIENDA_BACKEND_URL`. Por eso n8n no recibe secretos del backend.
- **Fuera de alcance:** workflows, credenciales de DeepSeek, el servidor MCP del backend y el disparo de n8n desde el backend. Quedan para cambios posteriores.

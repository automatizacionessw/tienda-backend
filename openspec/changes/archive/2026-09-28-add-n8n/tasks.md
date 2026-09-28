# Tasks

## 1. n8n en el compose de despliegue

- [x] 1.1 Agregar el servicio `n8n` a `docker/docker-compose.yml`:
  - imagen `n8nio/n8n:2.40.7`;
  - variables de design.md §4, con `${N8N_ENCRYPTION_KEY:?...}` y `${N8N_HOST:?...}`;
  - `TIENDA_BACKEND_URL=http://backend:8080` y `N8N_BLOCK_ENV_ACCESS_IN_NODE=false` (§5);
  - `expose: 5678`, volumen con nombre `n8n_data` en `/home/node/.n8n`;
  - healthcheck con `node` y `fetch` (§6).

  Actualizar el comentario de cabecera. Verificar que `docker compose -f docker/docker-compose.yml config` resuelve el servicio con un `.env` de prueba completo.
- [x] 1.2 Agregar a `docker/.env.example` la sección de n8n (`N8N_ENCRYPTION_KEY` y `N8N_HOST` obligatorias, `N8N_TIMEZONE` opcional), con comentarios sobre cómo generar la clave y por qué no hay que cambiarla después. Verificar que la plantilla lista todas las variables que el compose interpola.
- [x] 1.3 Verificar las obligatorias: `docker compose -f docker/docker-compose.yml config` falla nombrando `N8N_ENCRYPTION_KEY` cuando falta, y también nombrando `N8N_HOST` cuando falta.
- [x] 1.4 Levantar el stack de despliegue en local con un `docker/.env` de prueba (`N8N_HOST=n8n.example.test`) y verificar:
  - los tres servicios quedan `healthy`;
  - `docker compose ps` no muestra puertos publicados;
  - `docker compose exec n8n node -e "fetch('http://backend:8080/actuator/health').then(r=>r.text()).then(console.log)"` imprime `{"status":"UP"}`;
  - `docker compose exec n8n env` no contiene `DB_PASSWORD`, `POSTGRES_PASSWORD` ni variables de Telegram, y sí contiene `TIENDA_BACKEND_URL`.
- [x] 1.5 Con el mismo stack, entrar al editor por un port-forward temporal (`docker compose exec`, o un `ports` añadido solo en una copia descartable del compose), crear el owner y un workflow, y verificar:
  - la URL de webhook que muestra un nodo Webhook empieza con `https://n8n.example.test/`;
  - un HTTP Request a `{{ $env.TIENDA_BACKEND_URL }}/actuator/health` responde `{"status":"UP"}`.

  Luego ejecutar `docker compose down` (sin `-v`) y `up -d`, y comprobar que el owner y el workflow siguen existiendo. Bajar el stack de prueba y borrar el `docker/.env` de prueba.

## 2. Compose de desarrollo local

- [x] 2.1 Crear `docker/local/docker-compose.yml` con `name: tienda-local` y estos servicios (design.md §7-§9):
  - `db` con credenciales fijas y healthcheck;
  - `n8n` con las variables locales, `extra_hosts: host.docker.internal:host-gateway` y `TIENDA_BACKEND_URL` con valor por defecto `http://host.docker.internal:8080`;
  - `backend` con `profiles: ["completo"]`, build `context: ../..` / `dockerfile: docker/Dockerfile`, `API_DOCS_ENABLED=true`, bot configurable y siempre en POLLING;
  - puertos solo en `127.0.0.1` con `LOCAL_*`, volúmenes con nombre y healthchecks.

  Verificar que `docker compose -f docker/local/docker-compose.yml config` funciona sin ningún `.env`.
- [x] 2.2 Crear `docker/local/.env.example`:
  - el cambio de modo comentado (`COMPOSE_PROFILES=completo` y `TIENDA_BACKEND_URL=http://backend:8080`);
  - los puertos `LOCAL_*`;
  - las variables opcionales del bot.

  Agregar `docker/local/.env` a `.gitignore`. Verificar con `git check-ignore -v docker/local/.env` y que `docker/local/.env.example` no queda ignorado.
- [x] 2.3 Modo desarrollo: sin `docker/local/.env`, ejecutar `docker compose -f docker/local/docker-compose.yml up -d --build --wait` y verificar:
  - solo existen `db` y `n8n`, ambos `healthy`;
  - `docker compose ps` muestra los puertos en `127.0.0.1`;
  - existen los volúmenes `tienda-local_*`.
- [x] 2.4 Actualizar `src/main/resources/application-local.properties.example` para que apunte a `jdbc:postgresql://localhost:5432/tienda_db` con `tienda`/`tienda`. Verificar que, con una copia sin editar (respaldando antes el `application-local.properties` propio), `./mvnw spring-boot:run` arranca contra la base local, y que desde el editor de n8n en `http://localhost:5678` un HTTP Request a `{{ $env.TIENDA_BACKEND_URL }}/actuator/health` responde `{"status":"UP"}`. Restaurar el `application-local.properties` propio al terminar.
- [x] 2.5 Modo completo: detener el backend del IDE, crear `docker/local/.env` descomentando las dos líneas de modo, ejecutar el mismo comando de arranque y verificar:
  - `db`, `n8n` y `backend` quedan `healthy`;
  - `http://localhost:8080/actuator/health` y `http://localhost:8080/swagger-ui.html` responden desde el host;
  - el log del backend no muestra el bot iniciado;
  - desde n8n, `{{ $env.TIENDA_BACKEND_URL }}/actuator/health` responde `{"status":"UP"}`.
- [x] 2.6 Verificar el aislamiento:
  - con un `docker/.env` de despliegue presente y contraseña distinta, el entorno local sigue usando `tienda`/`tienda` (`docker compose -f docker/local/docker-compose.yml config` no muestra la contraseña de `docker/.env`);
  - con `LOCAL_DB_PORT=15432` en `docker/local/.env`, PostgreSQL queda publicado en `127.0.0.1:15432`.

  Borrar los `.env` de prueba y bajar el entorno.

## 3. Documentación

- [x] 3.1 Actualizar `docker/README.md`:
  - la tabla de archivos con `docker/local/`;
  - el diagrama con n8n y su dominio;
  - en los pasos de Dokploy, las variables nuevas, el dominio `n8n:5678` y la advertencia de crear el owner de inmediato;
  - la nota sobre la memoria;
  - en el rollback, el volumen `n8n_data`;
  - una sección "Entorno local" con los dos modos, el comando único, los puertos, que el puerto 8080 no se puede compartir con el IDE, y cómo acceder a n8n y a `TIENDA_BACKEND_URL`.

  Verificar que cada comando del documento se ejecutó en las tareas 1 y 2.
- [x] 3.2 Actualizar `README.md` raíz:
  - "Requisitos previos": Docker como alternativa para la base de datos;
  - "Configuración para desarrollo": levantar el entorno local y copiar el ejemplo;
  - "Despliegue con Docker": mencionar n8n y enlazar la sección local de `docker/README.md`.

  Verificar que los enlaces relativos apuntan a archivos existentes.
- [x] 3.3 Revisión final:
  - `openspec validate add-n8n --strict` pasa;
  - `git status` no muestra `docker/.env`, `docker/local/.env` ni `application-local.properties`;
  - ningún archivo versionado contiene claves reales (buscar `N8N_ENCRYPTION_KEY=` con valor, `password=` y `token=`);
  - `./mvnw test` sigue pasando.

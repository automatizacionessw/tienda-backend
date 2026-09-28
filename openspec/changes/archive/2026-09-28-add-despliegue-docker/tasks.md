# Tasks

## 1. Configuración de la aplicación

- [x] 1.1 Antes de empezar, renombrar la copia local `src/main/resources/application.properties` a `application-local.properties`, para no versionar credenciales; verificar que `git status` sigue limpio
- [x] 1.2 Actualizar `.gitignore`: quitar las entradas de `application.properties` y agregar `application-local.properties` y `docker/.env`; verificar con `git check-ignore -v` que `src/main/resources/application-local.properties` y `docker/.env` quedan ignorados y `application.properties` no
- [x] 1.3 Crear el `application.properties` versionado con los placeholders y defaults de design.md §1, `spring.config.import=optional:classpath:application-local.properties` y comentarios que remitan al override local; verificar con una búsqueda que no contiene credenciales, tokens ni IPs reales
- [x] 1.4 Crear `application-local.properties.example` con los overrides típicos de desarrollo (datasource, `show-sql`, Swagger habilitado, bot opcional), sin valores reales, y con la nota de precedencia de design.md §2; eliminar `application.properties.example`
- [x] 1.5 Verificar el override: `./mvnw test` pasa usando el `application-local.properties` local, y al arrancar con `./mvnw spring-boot:run` `/swagger-ui.html` responde si el override lo habilita
- [x] 1.6 Verificar los defaults seguros: con el override renombrado temporalmente y `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` exportadas, la app arranca, el bot no se inicia y `/v3/api-docs` responde 404

## 2. Imagen Docker

- [x] 2.1 Crear `docker/Dockerfile` multi-stage (Maven + Temurin 17 para el build con `-DskipTests`, `eclipse-temurin:17-jre-alpine` con usuario no root para el runtime, `/app/data/archivos` perteneciente a ese usuario) según design.md §4
- [x] 2.2 Crear `docker/Dockerfile.dockerignore` con las exclusiones de design.md §3
- [x] 2.3 Verificar que `docker build -f docker/Dockerfile .` termina bien desde la raíz y que `docker run --rm --entrypoint sh <imagen> -c "unzip -l app.jar | grep application-local"` no encuentra nada, aunque el archivo exista localmente

## 3. Docker Compose

- [x] 3.1 Crear `docker/docker-compose.yml` con los servicios `db` y `backend`, variables mapeadas explícitamente, `${VAR:?}` para las obligatorias, `expose` en lugar de `ports`, volúmenes con nombre `pgdata` y `archivos`, healthchecks y `depends_on: service_healthy` (design.md §5)
- [x] 3.2 Crear `docker/.env.example` con todas las variables, agrupadas y comentadas (obligatorias, BD, JPA, Telegram, documentación de la API)
- [x] 3.3 Verificar que `docker compose -f docker/docker-compose.yml config` falla nombrando `POSTGRES_PASSWORD` cuando esa variable falta en `docker/.env`
- [x] 3.4 Levantar el stack con un `docker/.env` de prueba y verificar que ambos servicios quedan `healthy`, que `docker compose exec backend wget -qO- http://localhost:8080/api/productos` responde, y que `docker compose ps` no muestra puertos publicados
- [x] 3.5 Verificar la persistencia: crear un producto, ejecutar `docker compose down` (sin `-v`) y `up -d`, y comprobar que el producto sigue existiendo
- [x] 3.6 Verificar que con `API_DOCS_ENABLED=true` en `docker/.env`, tras recrear `backend`, `/swagger-ui.html` responde 200 desde el contenedor

## 4. Documentación

- [x] 4.1 Reescribir la sección "Configuración" de `README.md` con el esquema de tres capas, cómo crear `application-local.properties` desde el ejemplo, la nota de migración para desarrolladores y los nombres de variables; actualizar los fragmentos de Telegram y Swagger para que remitan al override o a las variables
- [x] 4.2 Crear `docker/README.md` con la guía de Dokploy (Compose Path, variables, Isolated Deployments, dominio → `backend:8080`, volúmenes y backups, rollback) y una prueba local con `docker compose`; verificar que todos los comandos del documento se ejecutaron en las tareas 2 y 3
- [x] 4.3 Revisión final: `git status` no muestra `application-local.properties` ni `docker/.env`, y ningún archivo versionado contiene credenciales (buscar `password=`, `token=` e IPs)

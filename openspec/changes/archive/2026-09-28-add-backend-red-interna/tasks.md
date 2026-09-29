# Tasks

## 1. Compose de despliegue

- [x] 1.1 En `docker/docker-compose.yml`, agregar al servicio `backend` `ports: "${BACKEND_BIND_IP:-127.0.0.1}:${BACKEND_HOST_PORT:-8080}:8080"` (design.md §1-§2) y actualizar sus comentarios, que dejan de hablar de dominio. Verificar con `docker compose config` que:
  - sin `BACKEND_BIND_IP`, el puerto queda en `127.0.0.1:8080`;
  - con `BACKEND_BIND_IP=10.0.0.5` y `BACKEND_HOST_PORT=18080`, queda en `10.0.0.5:18080`.
- [x] 1.2 En `docker/.env.example`, agregar la sección del backend (`BACKEND_BIND_IP` y `BACKEND_HOST_PORT`), con la advertencia de no usar `0.0.0.0` ni la IP pública. Aclarar en la sección de Telegram que en este despliegue se usa POLLING y que WEBHOOK requiere exponer el backend. Verificar que la plantilla lista todas las variables que el compose interpola.
- [x] 1.3 Levantar el stack de despliegue en local, en un proyecto de prueba, sin `BACKEND_BIND_IP` y con `BACKEND_HOST_PORT` libre, y verificar:
  - los tres servicios quedan `healthy`;
  - `docker compose ps` muestra el backend solo en `127.0.0.1`, y ni db ni n8n publican puertos;
  - `http://127.0.0.1:<puerto>/actuator/health` responde desde el host;
  - n8n sigue alcanzando `http://backend:8080`.

  Bajar el stack y borrar el `.env` de prueba.

## 2. Documentación

- [x] 2.1 Actualizar `docker/README.md`:
  - el diagrama, con n8n como único dominio y el backend en la IP interna;
  - el punto "Sin puertos publicados";
  - los pasos de Dokploy: `BACKEND_BIND_IP`, un solo dominio para n8n, bot en POLLING y verificación desde la red interna;
  - la nota del webhook (no aplica en esta topología);
  - cómo actualizar un despliegue existente (quitar el dominio del backend);
  - la prueba local del despliegue con el puerto publicado y `BACKEND_HOST_PORT`.

  Verificar que ningún paso de la guía sigue pidiendo un dominio para el backend.
- [x] 2.2 Actualizar la sección "Despliegue con Docker" de `README.md` para indicar que el backend solo es accesible desde la red interna y que n8n es el único servicio público. Verificar que los enlaces siguen siendo válidos.
- [x] 2.3 Revisión final: `openspec validate add-backend-red-interna --strict` pasa y `git status` no muestra archivos `.env`.

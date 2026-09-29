# Proposal

## Why

La API REST del backend no tiene autenticación. Con un dominio público en Dokploy, cualquiera en internet puede crear o borrar productos o clasificar conversaciones. Pero nada en el proyecto necesita llegar al backend desde internet:
- el bot usa long polling, así que solo necesita salida hacia Telegram;
- n8n lo alcanza por la red interna del compose;
- el healthcheck corre dentro del contenedor.

El servidor tiene una IP interna y otra pública. Restringir el backend a la red interna elimina esa superficie de ataque y deja a n8n como único servicio público. También simplifica el próximo servidor MCP, que va a vivir en el backend.

## What Changes

- El servicio `backend` del compose de despliegue publica su puerto HTTP **solo en una IP del host configurable** (`BACKEND_BIND_IP`), que en el servidor es la IP interna. El puerto del host también es configurable (`BACKEND_HOST_PORT`).
- Si `BACKEND_BIND_IP` no se define, el backend se publica solo en `127.0.0.1`, de modo que un olvido nunca lo expone a la red pública.
- La guía de Dokploy deja de asignar dominio al backend: n8n es el único servicio con dominio. El modo recomendado del bot en el despliegue pasa a ser POLLING. El modo WEBHOOK sigue soportado por el código, pero requiere exponer el backend y queda documentado como fuera de esta topología.
- `docker/.env.example` documenta las dos variables nuevas y aclara que el webhook no aplica en esta topología.

## Capabilities

### New Capabilities
<!-- ninguna -->

### Modified Capabilities
- `despliegue-docker`:
  - la orquestación pasa a admitir que el backend publique su puerto, solo en la IP interna configurada, y exige que no sea accesible desde la red pública;
  - el `.env` de despliegue incluye la IP y el puerto de publicación del backend.

## Impact

- **Archivos:** `docker/docker-compose.yml`, `docker/.env.example`, `docker/README.md` y la sección de despliegue de `README.md`.
- **Código Java y tests:** no cambian.
- **Operación (Dokploy):**
  - definir `BACKEND_BIND_IP` con la IP interna del servidor;
  - quitar el dominio del servicio `backend`, si lo tiene;
  - mantener `TELEGRAM_BOT_MODE=POLLING`.
- **Acceso de operador:** Swagger y la API se usan desde la red interna, en `http://<ip-interna>:8080`.
- **Fuera de alcance:** el compose local, que ya publica solo en `127.0.0.1`, y la autenticación de la API.

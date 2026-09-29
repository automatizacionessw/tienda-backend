# Design

## Context

- `docker/docker-compose.yml` hoy publica el backend solo a través de Traefik (`expose: 8080` más un dominio asignado en Dokploy). No publica puertos en el host.
- El servidor tiene dos interfaces: una IP interna, de la red de la organización, y una pública.
- El bot corre en modo POLLING y, al arrancar en ese modo, borra cualquier webhook registrado (spec `telegram-recepcion`). No necesita tráfico entrante.
- n8n alcanza al backend por `http://backend:8080`, dentro de la red del compose.

## Goals / Non-Goals

**Goals:**
- Que el backend sea accesible desde la red interna, para Swagger y la API del operador, y nunca desde la pública.
- Que el comportamiento por defecto sea seguro: sin configuración, el backend no se expone fuera del host.

**Non-Goals:**
- Autenticación de la API REST.
- Reglas de firewall del sistema operativo.
- Cambios en el compose local.

## Decisions

### 1. Publicar el puerto ligado a una IP concreta del host
```yaml
ports:
  - "${BACKEND_BIND_IP:-127.0.0.1}:${BACKEND_HOST_PORT:-8080}:8080"
```
Docker solo abre el socket en esa dirección, así que el puerto no existe en la interfaz pública.
- **Por qué no depender del firewall:** Docker inserta sus propias reglas de iptables al publicar puertos y se salta firewalls como `ufw`. Ligar el puerto a la IP es el control que Docker respeta.
- **Alternativas descartadas:**
  - Dominio de Dokploy con restricción de IP en Traefik (middleware `ipAllowList`): requiere editar labels o la configuración de Traefik que Dokploy administra, y el backend seguiría atado al proxy público.
  - Dejar el backend sin puerto y sin dominio: sería lo más cerrado, pero perdería el acceso de operador a Swagger y a la API desde la red interna, que el usuario quiere conservar.

### 2. Valor por defecto `127.0.0.1` en lugar de variable obligatoria
Si falta `BACKEND_BIND_IP`, el backend queda accesible solo desde el propio servidor. Un olvido nunca lo expone.
- **Alternativa descartada:** `${BACKEND_BIND_IP:?}` obligatoria. Forzaría a configurarla, pero rompería el deploy por una variable cuyo valor seguro es obvio. La guía indica configurarla para acceder desde la red interna.
- **Riesgo aceptado:** alguien podría poner `0.0.0.0` o la IP pública. La plantilla y la guía lo advierten.

### 3. n8n como único servicio con dominio
La guía de Dokploy deja de asignar dominio al servicio `backend`. El modo WEBHOOK del bot sigue en el código, pero en esta topología no aplica, porque Telegram no puede llegar al backend. Se documenta así en lugar de quitarlo, para no tocar código ni la spec `telegram-recepcion`.

## Risks / Trade-offs

- **[Un dominio del backend en Dokploy seguiría exponiéndolo por Traefik]** → Hay que quitarlo en la UI al desplegar este cambio (migración, paso 2). El compose no puede impedirlo.
- **[Conflicto de puerto al probar el stack de despliegue en local con el backend del IDE corriendo]** → Se resuelve con `BACKEND_HOST_PORT`, documentado en la guía.
- **[Cambio de la IP interna del servidor]** → Hay que actualizar `BACKEND_BIND_IP` y redesplegar. Si la IP ya no existe en el host, el contenedor no arranca, y el error de Docker lo indica.

## Migration Plan

1. En el Environment de Dokploy, definir `BACKEND_BIND_IP=<ip-interna>` y, opcionalmente, `BACKEND_HOST_PORT`.
2. En Domains, quitar el dominio del servicio `backend`, si lo tiene.
3. Confirmar `TELEGRAM_BOT_MODE=POLLING` y redesplegar.
4. Verificar desde la red interna `http://<ip-interna>:8080/actuator/health`, y que el puerto no responda en la IP pública.
5. **Rollback:** redesplegar el commit anterior y volver a asignar el dominio al backend.

[README.md](https://github.com/user-attachments/files/32398803/README.md)
# Ventas MCP Server

Backend en Spring Boot para la automatización del flujo de ventas y validación de stock en tiempo real de una tienda de electrónica, con atención a clientes vía **bot de Telegram** integrado en el propio backend.

## Descripción del proyecto

Este backend es un **monolito modular**: el núcleo de negocio, la persistencia y el canal de Telegram viven en una sola aplicación. Expone:

- Una **API REST tradicional** para consultar productos y registrar ventas.
- Un **bot de Telegram** (módulo `chatbot`) que recibe los mensajes de los clientes por long polling o webhook, guarda todo el historial (incluidas las notas de voz) y responde. En esta iteración la respuesta es genérica (`mensaje recibido`); el NLP/IA se conectará más adelante a través de un punto de extensión.

### Arquitectura

```
Telegram  --(long polling | webhook POST /telegram/webhook)-->  Backend Spring Boot
                                                                 |
   +-------------------------------------------------------------+
   |
   +-- chatbot/            recepción de updates, historial de mensajes, respuesta
   |     telegram/         adaptadores de polling y webhook (TelegramBots 10.x)
   +-- cliente/            clientes identificados por su usuario de Telegram
   +-- producto/  venta/   catálogo, stock y ventas (API REST)
   +-- common/
         almacenamiento/   archivos binarios (notas de voz) en disco local
                                                                 |
                                                   JPA --> PostgreSQL
```

Los módulos colaboran a través de sus *services* y DTOs; un módulo no usa directamente las entidades ni los repositorios de otro.

Cuando se registra una venta, el backend notifica al dueño de la tienda mediante una notificación push para que confirme el pago y complete la venta.

## Tecnologías y dependencias

| Tecnología | Uso |
|---|---|
| **Spring Boot** | Framework base del backend |
| **Java 17 / 21** | Lenguaje |
| **Maven** | Gestión de dependencias y build |
| **Spring Web** | Endpoints REST (`/productos`, `/ventas`, etc.) |
| **Spring Data JPA** | ORM para el mapeo de entidades a la base de datos |
| **PostgreSQL Driver** | Conexión a la base de datos PostgreSQL |
| **Validation** | Validaciones de entrada (ej. cantidad ≤ stock disponible) |
| **Spring Boot DevTools** | Recarga en caliente durante el desarrollo |
| **Lombok** | Reducción de código repetitivo en las entidades (getters, setters, constructores) |
| **Spring Boot Actuator** | Endpoints de salud (`/actuator/health`) para monitoreo |
| **TelegramBots 10.x** (`telegrambots-client`, `telegrambots-longpolling`) | Integración con la Telegram Bot API (librería oficial, sin sus starters) |
| **springdoc-openapi 3.x** (`springdoc-openapi-starter-webmvc-ui`) | Especificación OpenAPI generada desde el código e interfaz Swagger UI para probar los endpoints |

## Modelo de datos

| Tabla | Propósito |
|---|---|
| `cliente` | Persona que interactúa con el bot de Telegram (identificada por su `telegram_user_id`) |
| `conversacion` | Agrupa los mensajes de un cliente: estado (`ABIERTA`, `CERRADA`), motivo de cierre (`INACTIVIDAD`, `INTENCION_RESUELTA`, `MANUAL`), fecha de inicio, del último mensaje y de cierre. `cliente_abierta` (única, nullable) garantiza una sola abierta por cliente |
| `conversacion_intencion` | Intención de una conversación (`SALUDO`, `CONSULTA_CATALOGO`, `INICIAR_PEDIDO`, `CONSULTAR_ESTADO_PEDIDO`, `OTRA` con detalle), su estado (`PENDIENTE`, `RESUELTA`), origen (`MANUAL`, `LLM`) y mensaje que la originó |
| `mensaje` | Cada mensaje de un chat privado con el bot, entrante o saliente: conversación, tipo (`TEXTO`, `VOZ`, `NO_SOPORTADO`), texto/caption y timestamps (dataset para el futuro NLP) |
| `adjunto` | Nota de voz asociada a un mensaje: metadatos de Telegram, clave en el almacenamiento y estado (`PENDIENTE`, `OK`, `ERROR`) |
| `usuario` | Dueño/vendedor que recibe notificaciones y confirma pagos |
| `producto` | Catálogo de productos con precio y stock |
| `venta` | Registro de una venta (pendiente, completada, cancelada) |
| `detalle_venta` | Productos y cantidades incluidos en cada venta |
| `notificacion` | Notificaciones push enviadas al dueño (nueva venta, pago pendiente, stock bajo) |

## Requisitos previos

- **JDK 17 o superior** instalado
- **PostgreSQL** corriendo (local o en Docker), con una base de datos creada
- **Maven** (o usar el wrapper `./mvnw` incluido en el proyecto)

## Configuración

La configuración se resuelve en tres capas. Cada una tiene prioridad sobre la anterior:

| Capa | Archivo / fuente | Versionado | Para qué |
|---|---|---|---|
| 1. Base | `src/main/resources/application.properties` | Sí | Todas las propiedades, con placeholders `${VARIABLE:default}` y valores por defecto seguros (sin credenciales, bot deshabilitado, Swagger apagado) |
| 2. Override local | `src/main/resources/application-local.properties` | **No** (ignorado) | Los ajustes de cada desarrollador: su base de datos, Swagger, bot de pruebas… |
| 3. Entorno | Variables de entorno (`docker/.env` en el despliegue) | No | La configuración de cada servidor; ver [docker/README.md](docker/README.md) |

**Decisión:** `application.properties` vuelve a estar versionado para tener una base compartida que se despliega sin cambios. Los secretos nunca se escriben ahí: en el servidor llegan por variables de entorno y en desarrollo por el override local. El override se carga automáticamente con `spring.config.import=optional:classpath:application-local.properties`, sin activar perfiles, así que `./mvnw spring-boot:run`, los tests y el IDE lo usan sin pasos extra. Si el archivo no existe, simplemente se ignora.

### Configuración para desarrollo

Crea tu override a partir del ejemplo y completa tus datos:

```bash
cp src/main/resources/application-local.properties.example src/main/resources/application-local.properties
```

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/nombre_de_tu_bd
spring.datasource.username=tu_usuario
spring.datasource.password=tu_password
spring.jpa.show-sql=true
springdoc.api-docs.enabled=true
springdoc.swagger-ui.enabled=true
```

> Asegúrate de tener PostgreSQL activo antes de arrancar la app; si no encuentra la base de datos configurada, el arranque fallará. Los tests (`contextLoads`) también usan esta base de datos.

> **Migración (si ya tenías el proyecto):** antes de hacer `git pull`, renombra tu `src/main/resources/application.properties` local a `application-local.properties`. Si no lo haces, git se niega a traer el archivo versionado, o tus credenciales pueden terminar en un commit.

Solo declara en el override lo que quieras cambiar. Ten en cuenta que un valor literal en el override reemplaza al placeholder, así que la variable corta equivalente (`DB_URL`, `API_DOCS_ENABLED`…) deja de tener efecto en tu máquina.

### Variables de entorno

| Variable | Propiedad | Default |
|---|---|---|
| `DB_URL` | `spring.datasource.url` | `jdbc:postgresql://localhost:5432/tienda_db` |
| `DB_USERNAME` | `spring.datasource.username` | `postgres` |
| `DB_PASSWORD` | `spring.datasource.password` | *(vacío)* |
| `JPA_DDL_AUTO` | `spring.jpa.hibernate.ddl-auto` | `update` |
| `JPA_SHOW_SQL` | `spring.jpa.show-sql` | `false` |
| `TELEGRAM_BOT_ENABLED` | `telegram.bot.habilitado` | `false` |
| `TELEGRAM_BOT_TOKEN` | `telegram.bot.token` | *(vacío)* |
| `TELEGRAM_BOT_MODE` | `telegram.bot.modo` | `POLLING` |
| `TELEGRAM_WEBHOOK_URL` | `telegram.bot.webhook.url` | *(vacío)* |
| `TELEGRAM_WEBHOOK_SECRET` | `telegram.bot.webhook.secret` | *(vacío)* |
| `STORAGE_DIR` | `almacenamiento.local.directorio` | `./data/archivos` |
| `API_DOCS_ENABLED` | `springdoc.api-docs.enabled` y `springdoc.swagger-ui.enabled` | `false` |

### Bot de Telegram

El bot está **deshabilitado por defecto**. Un token de bot admite un solo consumidor a la vez, así que usa un bot distinto por entorno (desarrollo / producción) y habilítalo solo donde corresponda. En desarrollo, en tu `application-local.properties`:

```properties
telegram.bot.habilitado=true
telegram.bot.token=<token-de-tu-bot-de-pruebas>
telegram.bot.modo=POLLING          # o WEBHOOK
telegram.bot.webhook.url=https://tu-dominio-publico/telegram/webhook
telegram.bot.webhook.secret=<secret>
```

- En el servidor se configuran con las variables `TELEGRAM_*` de la tabla anterior. Nunca escribas el token ni el secret en archivos versionados.
- **POLLING**: no necesita URL pública; es lo recomendado para desarrollo. Al arrancar elimina cualquier webhook registrado.
- **WEBHOOK**: Telegram envía los updates a `POST /telegram/webhook`, que exige el header `X-Telegram-Bot-Api-Secret-Token`. Requiere una URL **HTTPS pública**; en desarrollo puedes exponer el puerto local con un túnel (por ejemplo `ngrok http 8080`) y usar esa URL en `telegram.bot.webhook.url`.
- Si falta el token (o, en modo webhook, la URL o el secret), la aplicación no arranca e indica qué propiedad falta.
- Las notas de voz se guardan bajo `almacenamiento.local.directorio` (ignorado por git en `/data/`).
- Por ahora el bot procesa solo **chats privados**: texto y notas de voz. Fotos, documentos, stickers, etc. se registran como `NO_SOPORTADO` sin descargar el archivo; grupos y mensajes editados se ignoran.

### Conversaciones e intenciones

Los mensajes de cada cliente se agrupan en **conversaciones**, que se clasifican con **intenciones** para que el LLM (a futuro) solo actúe con contexto y con las herramientas adecuadas. Hoy la clasificación se hace a mano con la API `/api/conversaciones` (disponible aunque el bot esté deshabilitado; ver Swagger UI).

- El primer mensaje de un cliente abre una conversación `ABIERTA`; los siguientes se suman a ella mientras esté **vigente**.
- **Inactividad**: si pasa `conversacion.inactividad` (por defecto `2h`) desde el último mensaje, la conversación se da por cerrada con motivo `INACTIVIDAD` y el siguiente mensaje abre una nueva.
- **Cierre explícito** (`PATCH /api/conversaciones/{id}/cerrar`): `INTENCION_RESUELTA` exige que todas sus intenciones estén resueltas; `MANUAL` cierra sin condiciones.
- **Intenciones**: se registran en `PENDIENTE` (`POST .../intenciones`) y pasan a `RESUELTA` cuando se cumplió lo que el cliente quería (`PATCH .../intenciones/{intencionId}/resolver`). Solo puede haber una `PENDIENTE` por tipo. Lo que no encaja en el catálogo se registra como `OTRA` con un detalle.
- Las conversaciones cerradas o vencidas no admiten cambios (409).

> **Estado en la base vs. estado efectivo.** No hay un proceso programado que cierre las conversaciones vencidas: el cierre por inactividad se guarda recién cuando el cliente vuelve a escribir. Por eso una fila `ABIERTA` en `conversacion` puede estar vencida. La API siempre informa el estado **efectivo**; si consultas la base directamente, una conversación está vigente solo si `estado = 'ABIERTA'` y `fecha_ultimo_mensaje > now() - inactividad`.

#### Despliegue desde una versión sin conversaciones

Cada mensaje pertenece obligatoriamente a una conversación (`mensaje.conversacion_id NOT NULL`). Hibernate no puede agregar esa columna a una tabla con filas: registra el error y la aplicación **arranca igual, sin la columna**, fallando con el primer mensaje. Antes de desplegar esta versión sobre una base existente:

1. Detén la aplicación.
2. Vacía los datos del bot (los clientes se conservan):
   ```sql
   TRUNCATE adjunto, mensaje;
   ```
3. Borra las notas de voz guardadas: el contenido de `<almacenamiento.local.directorio>/voz/`.
4. Arranca la versión nueva y verifica que `mensaje.conversacion_id` existe y es `NOT NULL`.

### Documentación de la API (OpenAPI / Swagger UI)

La especificación OpenAPI y la interfaz Swagger UI están **apagadas por defecto**. Se activan con dos propiedades que deben tener **el mismo valor**: en el servidor, con la variable `API_DOCS_ENABLED=true` (controla las dos); en desarrollo, en tu override local:

```properties
springdoc.api-docs.enabled=true
springdoc.swagger-ui.enabled=true
```

- `GET /v3/api-docs`: especificación OpenAPI 3 en JSON (productos, ventas y conversaciones; el webhook de Telegram no se documenta).
- `/swagger-ui.html`: interfaz para explorar y probar los endpoints desde el navegador (por ejemplo `http://localhost:8080/swagger-ui.html`).
- Con ambas en `false`, las dos rutas responden 404. Si solo se apaga `api-docs`, la interfaz carga pero queda vacía.
- Por defecto quedan en `false` en `application.properties`. La API no tiene autenticación, así que no las actives en producción salvo que sea necesario.

## Cómo correrlo

### Desde la terminal

```bash
# Compilar el proyecto
./mvnw clean install

# Ejecutar la aplicación
./mvnw spring-boot:run
```

### Desde IntelliJ

1. Abre el proyecto.
2. Verifica que el plugin de **Lombok** esté instalado y habilitado (`Settings → Plugins`), y que **"Enable annotation processing"** esté activado (`Settings → Build, Execution, Deployment → Compiler → Annotation Processors`).
3. Ejecuta la clase principal `DemoApplication` (o el nombre que tenga tu clase `@SpringBootApplication`).

### Verificar que está corriendo

```
GET http://localhost:8080/actuator/health
```

Debería responder `{"status":"UP"}`.

## Despliegue con Docker

El backend se despliega junto a su PostgreSQL con Docker Compose en un servidor con **Dokploy**. Todo lo relacionado con Docker vive en la carpeta [`docker/`](docker/): `Dockerfile`, `docker-compose.yml` y la plantilla de variables `.env.example`. La guía paso a paso está en [docker/README.md](docker/README.md).

## Estado del proyecto

🚧 En desarrollo — próximos pasos:

- [x] Implementar endpoints REST de Producto y Venta
- [x] Bot de Telegram integrado: recepción (polling/webhook), historial de mensajes y notas de voz
- [x] Conversaciones con clasificación manual de intenciones (API REST)
- [ ] Clasificación de intenciones y respuestas del bot con NLP/IA (reemplazando la respuesta genérica)
- [ ] Evaluar si el servidor MCP sigue siendo necesario con la IA dentro del backend
- [ ] Implementar validación de stock antes de registrar una venta
- [ ] Implementar sistema de notificaciones push al dueño
- [ ] (Futuro) Validación de stock en tiempo real y alertas automáticas

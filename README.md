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
| `mensaje` | Cada mensaje de un chat privado con el bot, entrante o saliente: tipo (`TEXTO`, `VOZ`, `NO_SOPORTADO`), texto/caption y timestamps (dataset para el futuro NLP) |
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

Antes de levantar la aplicación, configura la conexión a la base de datos en `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/nombre_de_tu_bd
spring.datasource.username=tu_usuario
spring.datasource.password=tu_password
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

> Asegúrate de tener PostgreSQL activo antes de arrancar la app; si no encuentra la base de datos configurada, el arranque fallará.

Toma como base `src/main/resources/application.properties.example`.

### Bot de Telegram

El bot está **deshabilitado por defecto**. Un token de bot admite un solo consumidor a la vez, así que usa un bot distinto por entorno (desarrollo / producción) y habilítalo solo donde corresponda:

```properties
telegram.bot.habilitado=true
telegram.bot.token=${TELEGRAM_BOT_TOKEN:}
telegram.bot.modo=POLLING          # o WEBHOOK
telegram.bot.webhook.url=https://tu-dominio-publico/telegram/webhook
telegram.bot.webhook.secret=${TELEGRAM_WEBHOOK_SECRET:}
almacenamiento.local.directorio=./data/archivos
```

- El token y el secret se leen de las variables de entorno `TELEGRAM_BOT_TOKEN` y `TELEGRAM_WEBHOOK_SECRET`; no los escribas en archivos versionados.
- **POLLING**: no necesita URL pública; es lo recomendado para desarrollo. Al arrancar elimina cualquier webhook registrado.
- **WEBHOOK**: Telegram envía los updates a `POST /telegram/webhook`, que exige el header `X-Telegram-Bot-Api-Secret-Token`. Requiere una URL **HTTPS pública**; en desarrollo puedes exponer el puerto local con un túnel (por ejemplo `ngrok http 8080`) y usar esa URL en `telegram.bot.webhook.url`.
- Si falta el token (o, en modo webhook, la URL o el secret), la aplicación no arranca e indica qué propiedad falta.
- Las notas de voz se guardan bajo `almacenamiento.local.directorio` (ignorado por git en `/data/`).
- Por ahora el bot procesa solo **chats privados**: texto y notas de voz. Fotos, documentos, stickers, etc. se registran como `NO_SOPORTADO` sin descargar el archivo; grupos y mensajes editados se ignoran.

### Documentación de la API (OpenAPI / Swagger UI)

La especificación OpenAPI y la interfaz Swagger UI se activan o desactivan con dos propiedades, que deben tener **el mismo valor**:

```properties
springdoc.api-docs.enabled=true
springdoc.swagger-ui.enabled=true
```

- `GET /v3/api-docs`: especificación OpenAPI 3 en JSON (productos y ventas; el webhook de Telegram no se documenta).
- `/swagger-ui.html`: interfaz para explorar y probar los endpoints desde el navegador (por ejemplo `http://localhost:8080/swagger-ui.html`).
- Con ambas en `false`, las dos rutas responden 404. Si solo se apaga `api-docs`, la interfaz carga pero queda vacía.
- **Si las propiedades no están declaradas, springdoc las considera habilitadas.** La API no tiene autenticación, así que en producción decláralas explícitamente en `false`.

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

## Estado del proyecto

🚧 En desarrollo — próximos pasos:

- [x] Implementar endpoints REST de Producto y Venta
- [x] Bot de Telegram integrado: recepción (polling/webhook), historial de mensajes y notas de voz
- [ ] Respuestas del bot con NLP/IA (reemplazando la respuesta genérica)
- [ ] Evaluar si el servidor MCP sigue siendo necesario con la IA dentro del backend
- [ ] Implementar validación de stock antes de registrar una venta
- [ ] Implementar sistema de notificaciones push al dueño
- [ ] (Futuro) Validación de stock en tiempo real y alertas automáticas

# Proposal

## Why

Hoy la interacción con clientes por Telegram está planificada como un proceso Python separado (bot + NLP + IA) que consume el backend vía MCP. Se decidió pasar a un **monolito modular**: el backend Spring Boot pasa a ser el único punto de entrada del bot de Telegram, lo que simplifica la operación (un solo despliegue, un solo consumidor del token) y deja el historial de conversaciones en la misma base de datos que el negocio, disponible para el futuro NLP/IA.

Esta primera iteración establece la base: recibir mensajes, persistir todo el historial y responder de forma genérica.

## What Changes

- Nuevo módulo `chatbot` que integra el backend con la Telegram Bot API usando la librería oficial **TelegramBots 10.x** (`telegrambots-client` + `telegrambots-longpolling`), sin sus Spring Boot starters.
- Soporte de dos modos de recepción de updates, seleccionables por configuración: **long polling** y **webhook** (este último expuesto como `@RestController` propio con validación de secret token).
- Configuración en `application.properties`: token del bot, modo, URL y secret del webhook, directorio de almacenamiento.
- Persistencia de todos los mensajes de **chats privados**, entrantes y salientes: cliente, dirección, tipo, texto/caption, timestamp de Telegram y de registro.
- Tipos soportados: **texto** y **notas de voz** (`voice`). Las notas de voz se descargan y guardan en disco. Cualquier otro tipo (foto, documento, sticker, etc.) se persiste como `NO_SOPORTADO`, sin descargar el archivo.
- Respuesta automática genérica "mensaje recibido" a todo mensaje privado, persistida como mensaje saliente.
- Updates de grupos/canales y updates distintos de `message` (ediciones, callbacks, etc.) se ignoran (solo log).
- Nuevo módulo `cliente`: registro/actualización del cliente de Telegram (upsert por `telegram_user_id`), consumible por otros módulos vía `ClienteService`.
- Nueva abstracción de almacenamiento de archivos (`AlmacenamientoArchivos`) con implementación en sistema de archivos local, reemplazable a futuro (S3/MinIO) sin migrar datos.
- **BREAKING (arquitectura)**: se abandona el bot Python como consumidor de Telegram; el README se actualiza para reflejar el monolito modular.

### Fuera de alcance

- Chats grupales, supergrupos y canales.
- Mensajes editados, callbacks, inline queries y demás tipos de update.
- Descarga de adjuntos distintos de notas de voz (foto, audio, documento, video, stickers).
- NLP / IA / respuestas contextuales (se deja un punto de extensión `GeneradorRespuesta`).
- Decisión sobre el futuro del servidor MCP una vez que la IA viva dentro del backend.

## Capabilities

### New Capabilities
- `telegram-recepcion`: recepción de updates de Telegram por long polling o webhook según configuración, filtrado de chats privados y updates `message`, y respuesta genérica al remitente.
- `historial-mensajes`: persistencia de mensajes entrantes y salientes del bot, clasificación por tipo (texto, voz, no soportado), gestión de adjuntos de voz e idempotencia ante updates repetidos.
- `clientes`: registro y actualización de clientes identificados por su usuario de Telegram.
- `almacenamiento-archivos`: guardado y recuperación de archivos binarios mediante una abstracción de almacenamiento, con implementación en disco local.

### Modified Capabilities
<!-- No existen specs previas en openspec/specs/. -->

## Impact

- **Código nuevo**: paquetes `cliente/`, `chatbot/` (incl. `chatbot/telegram/`) y `common/almacenamiento/`.
- **Base de datos**: nuevas tablas `cliente`, `mensaje`, `adjunto` (generadas por Hibernate `ddl-auto=update`, como el resto del esquema actual).
- **Dependencias** (`pom.xml`): `org.telegram:telegrambots-client` y `org.telegram:telegrambots-longpolling` 10.x (traen Jackson 2 y OkHttp). Requiere verificar compatibilidad con Spring Boot 4.1.1.
- **Configuración**: `application.properties.example` con las nuevas propiedades `telegram.bot.*` y `almacenamiento.local.*`; el token y el secret se leen de variables de entorno.
- **API HTTP**: nuevo endpoint `POST /telegram/webhook` (solo activo en modo webhook).
- **Infraestructura**: en modo webhook se necesita una URL HTTPS pública; el directorio de almacenamiento debe existir/ser escribible en el servidor.
- **Documentación**: README (diagrama de arquitectura, tabla de tecnologías, configuración).
- **Sistemas externos**: el bot Python deja de consumir el token del bot.

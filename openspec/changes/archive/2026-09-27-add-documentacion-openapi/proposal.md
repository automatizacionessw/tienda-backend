# Proposal

## Why

El backend expone la API REST de productos y ventas, pero no publica ninguna especificación OpenAPI ni ofrece una interfaz para probar los endpoints. Hoy, para probarlos a mano hay que leer los controllers y armar las peticiones con herramientas externas. Tampoco existe un contrato publicado que puedan consumir otros sistemas, por ejemplo el servidor MCP o la IA que crea ventas.

## What Changes

- Se genera y publica una especificación **OpenAPI 3** de la API REST en `GET /v3/api-docs`.
- Se expone una **interfaz Swagger UI** en `/swagger-ui.html` que carga esa especificación y permite ejecutar peticiones de prueba.
- La API se documenta **en español**: datos generales (título, versión, descripción), agrupación por recurso (productos, ventas), descripción de cada operación, parámetros, cuerpos de petición y respuesta (con ejemplos), y las respuestas de error que cada endpoint ya devuelve hoy (400, 404, 409).
- Hay **dos propiedades de configuración** que activan o desactivan la especificación y la UI. Con ambas desactivadas, `/v3/api-docs` y `/swagger-ui.html` responden 404. En `application.properties.example` vienen desactivadas y con un comentario, siguiendo la convención de `telegram.bot.habilitado`.
- El endpoint `POST /telegram/webhook` se **excluye** de la documentación: lo invoca Telegram, no una persona, y exige un header secreto.

### Fuera de alcance

- Modificar contratos existentes: rutas, cuerpos, códigos de estado o las formas actuales de las respuestas de error (`mensaje` / `error` / `errores`). Se documentan tal como están.
- Autenticación o autorización de la API o de la propia documentación.
- Generación de clientes o servidores a partir de la especificación.

## Capabilities

### New Capabilities
- `documentacion-api`: publicación de la especificación OpenAPI de la API REST y de una interfaz Swagger UI para probarla, ambas activables por configuración, con descripciones en español y el webhook de Telegram excluido.

### Modified Capabilities
<!-- Ninguna: las capacidades existentes no cambian su comportamiento. -->

## Impact

- **Dependencias:** se agrega `springdoc-openapi-starter-webmvc-ui` en la línea 3.x, compatible con Spring Boot 4.
- **Código:** anotaciones de documentación en `ProductoController`, `VentaController` y sus DTOs; `@Hidden` en `TelegramWebhookController`; una nueva clase de configuración con los metadatos generales de la API.
- **Configuración:** nuevas propiedades `springdoc.api-docs.enabled` y `springdoc.swagger-ui.enabled` en `application.properties.example`.
- **Tests:** tests nuevos que verifican el interruptor (200 con los flags activados, 404 con los flags desactivados).
- **Docs:** el README incorpora cómo activar y acceder a Swagger UI.
- **APIs:** los endpoints de negocio no cambian. Solo aparecen `/v3/api-docs` y `/swagger-ui.html`, y únicamente cuando están habilitados.

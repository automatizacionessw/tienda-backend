# documentacion-api Specification

## Purpose
Publica una especificación OpenAPI de la API REST del backend y una interfaz Swagger UI para probar sus endpoints a mano. Ambas se pueden activar o desactivar por configuración según el entorno.

## Requirements

### Requirement: Especificación OpenAPI de la API REST
Cuando la documentación está habilitada, el sistema MUST publicar en `GET /v3/api-docs` un documento OpenAPI 3 en formato JSON. Ese documento MUST describir todas las operaciones de `/api/productos` y `/api/ventas`, con sus parámetros, cuerpos de petición y cuerpos de respuesta.

#### Scenario: Consulta de la especificación habilitada
- **WHEN** la documentación está habilitada y un cliente hace `GET /v3/api-docs`
- **THEN** el sistema responde 200 con un JSON que declara una versión `openapi` 3.x
- **AND** el documento contiene las rutas `/api/productos`, `/api/productos/{id}`, `/api/ventas`, `/api/ventas/{id}`, `/api/ventas/{id}/confirmar` y `/api/ventas/{id}/cancelar`, con todos sus métodos HTTP

### Requirement: Interfaz Swagger UI
Cuando la documentación está habilitada, el sistema MUST ofrecer en `/swagger-ui.html` una interfaz web que cargue la especificación publicada y permita ejecutar peticiones contra los endpoints documentados.

#### Scenario: Acceso a la interfaz habilitada
- **WHEN** la documentación está habilitada y un usuario abre `/swagger-ui.html` en el navegador
- **THEN** se muestra la interfaz Swagger UI con las operaciones de productos y ventas agrupadas por recurso
- **AND** el usuario puede enviar una petición de prueba y ver la respuesta real del backend

### Requirement: Activación por configuración
El sistema MUST permitir activar o desactivar la especificación y la interfaz mediante propiedades de configuración de la aplicación. Con ambas desactivadas, ni la especificación ni la interfaz MUST ser accesibles. Las demás rutas de la API MUST seguir funcionando igual.

#### Scenario: Documentación desactivada
- **WHEN** la aplicación arranca con la documentación desactivada y un cliente hace `GET /v3/api-docs` o `GET /swagger-ui.html`
- **THEN** el sistema responde 404 en ambos casos
- **AND** `GET /api/productos` sigue respondiendo como siempre

#### Scenario: Documentación activada
- **WHEN** la aplicación arranca con la documentación activada
- **THEN** `GET /v3/api-docs` responde 200

### Requirement: Descripciones en español
La especificación MUST incluir en español: título, versión y descripción general de la API; un grupo por recurso (productos, ventas) con su descripción; un resumen y una descripción de cada operación; la descripción de cada parámetro de ruta; y la descripción y un ejemplo de cada campo de los cuerpos de petición y respuesta.

#### Scenario: Operación documentada
- **WHEN** se consulta en la especificación la operación `PATCH /api/ventas/{id}/confirmar`
- **THEN** tiene un resumen en español que dice que confirma una venta pendiente cuando se recibió el pago
- **AND** el parámetro `id` tiene una descripción en español

#### Scenario: Campo de un cuerpo documentado
- **WHEN** se consulta en la especificación el esquema del cuerpo de creación de productos
- **THEN** los campos `nombre`, `precio` y `stock` tienen una descripción en español y un valor de ejemplo

### Requirement: Respuestas de error documentadas sin cambiar contratos
La especificación MUST documentar, en cada operación, las respuestas de error que la API ya devuelve hoy: 400 por validación o stock insuficiente, 404 por recurso inexistente y 409 por estado de venta inválido. Cada una MUST llevar su forma de cuerpo actual. Documentar la API MUST NOT modificar rutas, cuerpos, códigos de estado ni formatos de respuesta existentes.

#### Scenario: Error de recurso inexistente documentado
- **WHEN** se consulta en la especificación la operación `GET /api/productos/{id}`
- **THEN** incluye una respuesta 404 descrita en español con un cuerpo que tiene `timestamp`, `status` y `mensaje`

#### Scenario: Conflicto de estado de venta documentado
- **WHEN** se consulta en la especificación la operación `PATCH /api/ventas/{id}/cancelar`
- **THEN** incluye una respuesta 409 descrita en español con un cuerpo que tiene `error`

#### Scenario: Contratos intactos
- **WHEN** se invoca cualquier endpoint de productos o ventas con la documentación habilitada
- **THEN** la ruta, el código de estado y el cuerpo de la respuesta son idénticos a los que se obtienen con la documentación deshabilitada

### Requirement: Webhook de Telegram excluido
La especificación MUST NOT incluir el endpoint `POST /telegram/webhook`, aunque esté activo porque el bot funciona en modo webhook.

#### Scenario: Webhook activo pero no documentado
- **WHEN** el bot de Telegram está habilitado en modo webhook, la documentación está habilitada y un cliente hace `GET /v3/api-docs`
- **THEN** el documento no contiene la ruta `/telegram/webhook`

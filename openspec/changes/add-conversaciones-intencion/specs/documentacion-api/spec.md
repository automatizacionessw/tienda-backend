# Spec Delta

## MODIFIED Requirements

### Requirement: Especificación OpenAPI de la API REST
Cuando la documentación está habilitada, el sistema MUST publicar en `GET /v3/api-docs` un documento OpenAPI 3 en formato JSON. Ese documento MUST describir todas las operaciones de `/api/productos`, `/api/ventas` y `/api/conversaciones`, con sus parámetros, cuerpos de petición y cuerpos de respuesta.

#### Scenario: Consulta de la especificación habilitada
- **WHEN** la documentación está habilitada y un cliente hace `GET /v3/api-docs`
- **THEN** el sistema responde 200 con un JSON que declara una versión `openapi` 3.x
- **AND** el documento contiene las rutas `/api/productos`, `/api/productos/{id}`, `/api/ventas`, `/api/ventas/{id}`, `/api/ventas/{id}/confirmar` y `/api/ventas/{id}/cancelar`, con todos sus métodos HTTP
- **AND** el documento contiene las rutas `/api/conversaciones`, `/api/conversaciones/{id}`, `/api/conversaciones/{id}/intenciones`, `/api/conversaciones/{id}/intenciones/{intencionId}/resolver` y `/api/conversaciones/{id}/cerrar`, con todos sus métodos HTTP

### Requirement: Interfaz Swagger UI
Cuando la documentación está habilitada, el sistema MUST ofrecer en `/swagger-ui.html` una interfaz web que cargue la especificación publicada y permita ejecutar peticiones contra los endpoints documentados.

#### Scenario: Acceso a la interfaz habilitada
- **WHEN** la documentación está habilitada y un usuario abre `/swagger-ui.html` en el navegador
- **THEN** se muestra la interfaz Swagger UI con las operaciones de productos, ventas y conversaciones agrupadas por recurso
- **AND** el usuario puede enviar una petición de prueba y ver la respuesta real del backend

### Requirement: Descripciones en español
La especificación MUST incluir en español:
- el título, la versión y la descripción general de la API;
- un grupo por recurso (productos, ventas, conversaciones) con su descripción;
- un resumen y una descripción de cada operación;
- la descripción de cada parámetro de ruta y de consulta;
- la descripción y un ejemplo de cada campo de los cuerpos de petición y respuesta.

Los valores admitidos de intención, estado de intención, origen de la clasificación, estado de conversación y motivo de cierre MUST aparecer enumerados en la especificación.

#### Scenario: Operación documentada
- **WHEN** se consulta en la especificación la operación `PATCH /api/ventas/{id}/confirmar`
- **THEN** tiene un resumen en español que dice que confirma una venta pendiente cuando se recibió el pago
- **AND** el parámetro `id` tiene una descripción en español

#### Scenario: Campo de un cuerpo documentado
- **WHEN** se consulta en la especificación el esquema del cuerpo de creación de productos
- **THEN** los campos `nombre`, `precio` y `stock` tienen una descripción en español y un valor de ejemplo

#### Scenario: Catálogo de intenciones documentado
- **WHEN** se consulta en la especificación el esquema del cuerpo de registro de intenciones
- **THEN** el campo de la intención enumera `SALUDO`, `CONSULTA_CATALOGO`, `INICIAR_PEDIDO`, `CONSULTAR_ESTADO_PEDIDO` y `OTRA`, y su descripción en español indica que `OTRA` exige un detalle

### Requirement: Respuestas de error documentadas sin cambiar contratos
La especificación MUST documentar, en cada operación, las respuestas de error que la API devuelve, cada una con su forma de cuerpo actual:
- 400 por validación o por una regla de negocio incumplida;
- 404 por recurso inexistente;
- 409 por estado inválido de una venta, de una conversación o de una intención.

Documentar la API MUST NOT modificar rutas, cuerpos, códigos de estado ni formatos de respuesta.

#### Scenario: Error de recurso inexistente documentado
- **WHEN** se consulta en la especificación la operación `GET /api/productos/{id}`
- **THEN** incluye una respuesta 404 descrita en español con un cuerpo que tiene `timestamp`, `status` y `mensaje`

#### Scenario: Conflicto de estado de venta documentado
- **WHEN** se consulta en la especificación la operación `PATCH /api/ventas/{id}/cancelar`
- **THEN** incluye una respuesta 409 descrita en español con un cuerpo que tiene `error`

#### Scenario: Conflicto de estado de conversación documentado
- **WHEN** se consulta en la especificación la operación `POST /api/conversaciones/{id}/intenciones`
- **THEN** incluye respuestas 400, 404 y 409 descritas en español, y la 409 tiene un cuerpo con `error`

#### Scenario: Contratos intactos
- **WHEN** se invoca cualquier endpoint de productos, ventas o conversaciones con la documentación habilitada
- **THEN** la ruta, el código de estado y el cuerpo de la respuesta son idénticos a los que se obtienen con la documentación deshabilitada

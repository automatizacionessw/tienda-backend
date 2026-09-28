# Proposal

## Why

El bot responde hoy a todo con un texto genérico, y el LLM que se conectará después no debe hacer lo mismo: tiene que actuar solo cuando sabe qué quiere el cliente y con las herramientas adecuadas. Para eso hace falta, primero, agrupar los mensajes en **conversaciones** con un ciclo de vida y, segundo, poder **clasificar la intención** de cada conversación. Este change arma ese modelo y expone la clasificación por REST, de forma manual. Ese mismo endpoint es el que después usará el LLM.

## What Changes

- Nueva entidad **Conversación** en el módulo `chatbot`. Agrupa los mensajes entrantes y salientes de un cliente, y un cliente tiene como máximo una conversación abierta.
- Cada mensaje nuevo se asigna a la conversación vigente de su cliente, o abre una nueva si no hay ninguna.
- **Cierre por inactividad**: una conversación deja de estar vigente cuando pasan 2 horas desde su último mensaje (valor por defecto, configurable). La regla se evalúa cuando se usa la conversación, sin proceso programado. El mensaje siguiente abre una conversación nueva.
- **Cierre explícito** por endpoint con dos motivos:
  - `INTENCION_RESUELTA`: exige que todas las intenciones estén resueltas. Es el cierre que hará el LLM después de preguntar "¿puedo ayudarte en algo más?".
  - `MANUAL`: sin condiciones, para un operador.
- **Intenciones** de una conversación, con un catálogo cerrado de prueba de concepto: `SALUDO`, `CONSULTA_CATALOGO`, `INICIAR_PEDIDO` y `CONSULTAR_ESTADO_PEDIDO`.
  - Lo que no encaja en el catálogo se registra como `OTRA`, con un detalle obligatorio. Es la señal de que el LLM no debe responder.
  - Una intención está `PENDIENTE` hasta que se cumple lo que el cliente quería, y entonces pasa a `RESUELTA`.
  - Una conversación puede tener varias intenciones de tipos distintos, pero solo una `PENDIENTE` por tipo.
- **API REST** `/api/conversaciones`, documentada en OpenAPI:
  - consultar conversaciones (con filtro de vigencia y de "sin clasificar"),
  - ver una conversación con sus mensajes e intenciones,
  - registrar una intención,
  - resolver una intención,
  - cerrar una conversación.
- Las conversaciones cerradas o vencidas no admiten nuevas intenciones, resoluciones ni cierres.
- **BREAKING (datos)**: cada mensaje pasa a pertenecer obligatoriamente a una conversación. Los mensajes y notas de voz existentes, que son solo datos de desarrollo, se descartan antes del despliegue. Los clientes se conservan.

### Fuera de alcance

- Integración con el LLM y clasificación automática.
- El margen de espera antes de clasificar, por ejemplo cuando el cliente saluda y a los pocos segundos pide algo.
- Qué responde el bot ante `OTRA` o mientras la conversación no está clasificada. La respuesta sigue siendo `mensaje recibido`.
- Las herramientas que resuelven cada intención.
- La definición final del catálogo de intenciones, incluida una intención para consultar un producto concreto.
- El vínculo entre `Venta` y el cliente, que hará falta para consultar el estado de un pedido.
- Un proceso programado que cierre en la base las conversaciones vencidas.

## Capabilities

### New Capabilities
- `conversaciones`: ciclo de vida de la conversación (apertura, vigencia por inactividad, cierre explícito), catálogo y registro de intenciones con su resolución, y la API REST para consultarlas y operarlas manualmente.

### Modified Capabilities
- `historial-mensajes`: los mensajes entrantes y salientes se persisten asociados a una conversación.
- `documentacion-api`: la especificación OpenAPI incluye también las operaciones de `/api/conversaciones` y su grupo.
- `configuracion-entorno`: el tiempo de inactividad de las conversaciones se suma a las propiedades que se pueden definir por variable de entorno.

## Impact

- **Código**:
  - `chatbot/` (entidades, repositorios, servicio y controlador de conversaciones);
  - `ChatbotService`, que asigna cada mensaje a una conversación;
  - `MensajeEntranteDTO`, que pasa a llevar el id de la conversación;
  - `common/exception` (conflictos de estado de conversación);
  - `common/openapi` (grupo nuevo).
- **Base de datos**: tablas nuevas `conversacion` y `conversacion_intencion`, y columna obligatoria `mensaje.conversacion_id`, generadas por Hibernate (`ddl-auto=update`). Antes de desplegar hay que vaciar `adjunto` y `mensaje` y borrar los audios guardados.
- **API HTTP**: endpoints nuevos bajo `/api/conversaciones`. Están disponibles aunque el bot de Telegram esté deshabilitado.
- **Configuración**: propiedad nueva `conversacion.inactividad` (por defecto 2 horas) en `application.properties`, que se puede definir con la variable de entorno `CONVERSACION_INACTIVIDAD`. También se agrega a `docker/docker-compose.yml`, `docker/.env.example` y `application-local.properties.example`.
- **Documentación**: README (modelo de datos, paso de despliegue).

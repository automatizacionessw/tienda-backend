# telegram-recepcion Specification

## Purpose

Permite que el backend reciba los mensajes que los clientes envían al bot de Telegram, ya sea por long polling o por webhook según la configuración, y les responda de forma automática.

## Requirements

### Requirement: Modo de recepción configurable
El sistema MUST recibir updates de Telegram mediante exactamente uno de dos modos, seleccionado por configuración: `POLLING` (long polling) o `WEBHOOK`. El token del bot MUST provenir de la configuración externa (variable de entorno o propiedades no versionadas) y MUST NOT estar versionado en el repositorio.

#### Scenario: Arranque en modo polling
- **WHEN** la aplicación arranca con el modo configurado en `POLLING` y un token válido
- **THEN** el sistema consulta a Telegram periódicamente por nuevos updates y los procesa
- **AND** el endpoint HTTP de webhook no está disponible (responde 404)

#### Scenario: Arranque en modo webhook
- **WHEN** la aplicación arranca con el modo configurado en `WEBHOOK`, un token válido, una URL pública y un secret configurados
- **THEN** el sistema registra en Telegram el webhook apuntando a la URL configurada, asociado al secret configurado
- **AND** el sistema no realiza long polling

#### Scenario: Token ausente
- **WHEN** la aplicación arranca con el bot habilitado y sin token configurado
- **THEN** el arranque falla con un mensaje que indica que falta el token

#### Scenario: Webhook sin URL o sin secret
- **WHEN** la aplicación arranca en modo `WEBHOOK` sin URL o sin secret configurados
- **THEN** el arranque falla con un mensaje que indica qué propiedad falta

### Requirement: Bot deshabilitable
El sistema MUST permitir deshabilitar por configuración toda la integración con Telegram. Con el bot deshabilitado, la aplicación MUST arrancar sin token, sin realizar polling, sin registrar ni eliminar webhooks y sin exponer el endpoint de webhook; el resto de la aplicación funciona con normalidad.

#### Scenario: Arranque con el bot deshabilitado
- **WHEN** la aplicación arranca con el bot deshabilitado y sin token configurado
- **THEN** el arranque es exitoso y no se realiza ninguna llamada a Telegram
- **AND** el endpoint de webhook responde 404

### Requirement: Exclusividad entre modos
Al arrancar en modo `POLLING`, el sistema MUST eliminar cualquier webhook previamente registrado para el bot, de modo que Telegram entregue los updates por polling.

#### Scenario: Cambio de webhook a polling
- **WHEN** el bot tenía un webhook registrado y la aplicación arranca en modo `POLLING`
- **THEN** el webhook queda eliminado en Telegram antes de iniciar el polling
- **AND** los mensajes nuevos se reciben por polling

### Requirement: Autenticación del webhook
En modo `WEBHOOK`, el sistema MUST aceptar una petición entrante al endpoint de webhook solo si incluye el header `X-Telegram-Bot-Api-Secret-Token` con el valor del secret configurado. Las peticiones sin header o con un valor distinto MUST rechazarse sin procesar su contenido.

#### Scenario: Petición con secret válido
- **WHEN** llega una petición al endpoint de webhook con el secret correcto y un update válido
- **THEN** el sistema procesa el update y responde con un código 2xx

#### Scenario: Petición sin secret o con secret incorrecto
- **WHEN** llega una petición al endpoint de webhook sin el header de secret o con un valor distinto al configurado
- **THEN** el sistema responde 401 y no persiste ni responde ningún mensaje

### Requirement: Aislamiento de fallos por update
Un error al procesar un update MUST NOT impedir el procesamiento de los updates siguientes, en ninguno de los dos modos. El error MUST quedar registrado en el log.

#### Scenario: Fallo al procesar un update en modo webhook
- **WHEN** ocurre un error interno al procesar un update autenticado recibido por webhook
- **THEN** el sistema registra el error en el log y responde con un código 2xx para que Telegram no bloquee la entrega de updates posteriores

#### Scenario: Fallo al procesar un update en modo polling
- **WHEN** ocurre un error interno al procesar un update recibido por polling
- **THEN** el sistema registra el error en el log y continúa procesando los updates siguientes

### Requirement: Filtrado de updates fuera de alcance
El sistema MUST procesar únicamente updates de tipo `message` provenientes de chats privados. Cualquier otro update (mensajes de grupos, supergrupos o canales; mensajes editados; callbacks; inline queries; etc.) MUST ignorarse: no se persiste, no se responde y solo se registra en el log a nivel informativo o de depuración.

#### Scenario: Mensaje en chat privado
- **WHEN** llega un update `message` cuyo chat es de tipo privado
- **THEN** el sistema lo procesa (persistencia y respuesta)

#### Scenario: Mensaje en un grupo
- **WHEN** llega un update `message` cuyo chat es un grupo, supergrupo o canal
- **THEN** el sistema no persiste el mensaje ni envía respuesta

#### Scenario: Mensaje editado
- **WHEN** llega un update `edited_message` de un chat privado
- **THEN** el sistema no persiste cambios ni envía respuesta

### Requirement: Respuesta genérica
Por cada mensaje privado procesado por primera vez, el sistema MUST responder en el mismo chat con el texto `mensaje recibido`, independientemente del tipo de mensaje recibido (texto, nota de voz o tipo no soportado).

#### Scenario: Respuesta a mensaje de texto
- **WHEN** un cliente envía un mensaje de texto al bot por chat privado
- **THEN** el cliente recibe en ese chat la respuesta `mensaje recibido`

#### Scenario: Respuesta a tipo no soportado
- **WHEN** un cliente envía una foto al bot por chat privado
- **THEN** el cliente recibe en ese chat la respuesta `mensaje recibido`

#### Scenario: Update repetido
- **WHEN** Telegram entrega nuevamente un mensaje que ya fue procesado
- **THEN** el sistema no envía una segunda respuesta

#### Scenario: Fallo al enviar la respuesta
- **WHEN** el envío de la respuesta a Telegram falla
- **THEN** el mensaje entrante permanece persistido
- **AND** el fallo queda registrado en el log
- **AND** no se persiste ningún mensaje saliente para ese intento

# Spec Delta

## MODIFIED Requirements

### Requirement: Persistencia de mensajes entrantes
El sistema MUST persistir cada mensaje privado procesado con los siguientes datos:
- el cliente al que pertenece;
- la conversación a la que se asigna, según las reglas de la capacidad `conversaciones`;
- el identificador del mensaje en Telegram;
- la dirección `ENTRANTE`;
- el tipo de mensaje;
- el texto, o el caption si lo hubiera;
- la fecha y hora del mensaje según Telegram (UTC);
- la fecha y hora en que el sistema lo registró.

El mensaje MUST quedar persistido antes de enviar la respuesta.

#### Scenario: Registro de un mensaje de texto
- **WHEN** un cliente envía el texto "hola" por chat privado
- **THEN** existe un mensaje persistido con dirección `ENTRANTE`, tipo `TEXTO`, texto "hola", el identificador de Telegram del mensaje, la fecha de Telegram y la fecha de registro, asociado a ese cliente y a su conversación vigente

### Requirement: Persistencia de mensajes salientes
Cada respuesta enviada con éxito por el bot MUST persistirse como mensaje con estos datos:
- la dirección `SALIENTE`;
- el mismo cliente y la misma conversación del mensaje que la originó;
- el tipo `TEXTO`;
- el texto enviado;
- el identificador del mensaje asignado por Telegram;
- su fecha.

#### Scenario: Registro de la respuesta
- **WHEN** el bot responde `mensaje recibido` a un cliente y Telegram confirma el envío
- **THEN** existe un mensaje persistido con dirección `SALIENTE`, tipo `TEXTO`, texto `mensaje recibido` y el identificador asignado por Telegram, asociado a ese cliente y a la conversación del mensaje entrante que respondió

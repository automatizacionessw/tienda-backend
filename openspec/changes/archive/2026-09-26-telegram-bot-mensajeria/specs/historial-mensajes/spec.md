# Spec Delta

## Purpose

Conserva el historial completo de mensajes intercambiados entre los clientes y el bot en chats privados, incluidos los archivos de las notas de voz, como registro del negocio y como base para el futuro procesamiento con NLP/IA.

## ADDED Requirements

### Requirement: Persistencia de mensajes entrantes
El sistema MUST persistir cada mensaje privado procesado con: el cliente al que pertenece, el identificador del mensaje en Telegram, la dirección `ENTRANTE`, el tipo de mensaje, el texto (o caption, si lo hubiera), la fecha y hora del mensaje según Telegram (UTC) y la fecha y hora en que el sistema lo registró. El mensaje MUST quedar persistido antes de enviar la respuesta.

#### Scenario: Registro de un mensaje de texto
- **WHEN** un cliente envía el texto "hola" por chat privado
- **THEN** existe un mensaje persistido con dirección `ENTRANTE`, tipo `TEXTO`, texto "hola", el identificador de Telegram del mensaje, la fecha de Telegram y la fecha de registro, asociado a ese cliente

### Requirement: Clasificación por tipo de mensaje
El sistema MUST clasificar cada mensaje entrante en uno de tres tipos:
- `TEXTO`: el mensaje contiene texto.
- `VOZ`: el mensaje contiene una nota de voz; el texto almacenado es su caption, si lo tiene.
- `NO_SOPORTADO`: cualquier otro contenido (foto, audio, documento, video, sticker, ubicación, contacto, etc.); el texto almacenado es su caption, si lo tiene, y el archivo asociado MUST NOT descargarse.

#### Scenario: Nota de voz
- **WHEN** un cliente envía una nota de voz por chat privado
- **THEN** el mensaje se persiste con tipo `VOZ`

#### Scenario: Foto con caption
- **WHEN** un cliente envía una foto con el caption "este modelo" por chat privado
- **THEN** el mensaje se persiste con tipo `NO_SOPORTADO` y texto "este modelo"
- **AND** la foto no se descarga ni se almacena

#### Scenario: Sticker sin texto
- **WHEN** un cliente envía un sticker por chat privado
- **THEN** el mensaje se persiste con tipo `NO_SOPORTADO` y sin texto

### Requirement: Persistencia de mensajes salientes
Cada respuesta enviada con éxito por el bot MUST persistirse como mensaje con dirección `SALIENTE`, asociado al mismo cliente del mensaje que la originó, con tipo `TEXTO`, el texto enviado, el identificador del mensaje asignado por Telegram y su fecha.

#### Scenario: Registro de la respuesta
- **WHEN** el bot responde `mensaje recibido` a un cliente y Telegram confirma el envío
- **THEN** existe un mensaje persistido con dirección `SALIENTE`, tipo `TEXTO`, texto `mensaje recibido` y el identificador asignado por Telegram, asociado a ese cliente

### Requirement: Idempotencia ante updates repetidos
El sistema MUST garantizar que un mismo mensaje de Telegram (mismo cliente y mismo identificador de mensaje) quede persistido una sola vez por dirección, aunque Telegram lo entregue más de una vez.

#### Scenario: Reentrega del mismo mensaje
- **WHEN** Telegram entrega dos veces el mismo mensaje entrante
- **THEN** existe un único registro de ese mensaje entrante
- **AND** existe como máximo una respuesta saliente asociada a él

### Requirement: Almacenamiento de notas de voz
Para cada mensaje de tipo `VOZ`, el sistema MUST descargar el archivo de audio desde Telegram, guardarlo mediante el almacenamiento de archivos y registrar un adjunto asociado al mensaje con: el identificador del archivo en Telegram, su identificador único, el tipo MIME, el tamaño en bytes, la duración en segundos, la referencia al archivo en el almacenamiento y el estado `OK`.

#### Scenario: Nota de voz almacenada
- **WHEN** un cliente envía una nota de voz de 5 segundos por chat privado
- **THEN** el mensaje tiene un adjunto con estado `OK`, duración 5, su tipo MIME y tamaño, y una referencia al almacenamiento
- **AND** el contenido del audio puede recuperarse del almacenamiento mediante esa referencia

### Requirement: Tolerancia a fallos de descarga
Si la descarga o el guardado del audio de una nota de voz fallan, el sistema MUST conservar el mensaje entrante, registrar el adjunto con sus metadatos de Telegram y estado `ERROR` (sin referencia al almacenamiento), y continuar con el envío de la respuesta.

#### Scenario: Falla la descarga del audio
- **WHEN** un cliente envía una nota de voz y la descarga del archivo desde Telegram falla
- **THEN** el mensaje se persiste con tipo `VOZ`
- **AND** su adjunto queda con estado `ERROR` y los metadatos de Telegram
- **AND** el cliente recibe igualmente la respuesta `mensaje recibido`

# Tasks

## 1. Spike de compatibilidad y dependencias

- [x] 1.1 Agregar `org.telegram:telegrambots-client` y `org.telegram:telegrambots-longpolling` 10.x al `pom.xml` y verificar que `./mvnw clean compile` termina sin errores ni conflictos de versiones (si los hay, fijar versiones en `<dependencyManagement>` y documentarlo en design.md)
- [x] 1.2 Crear un test unitario que deserialice updates JSON de ejemplo (texto, nota de voz, foto con caption, sticker, mensaje de grupo, `edited_message`) con un `ObjectMapper` Jackson 2 privado y verificar que `./mvnw test` pasa con los campos esperados (`chat.type`, `voice.duration`, `caption`, etc.)
- [x] 1.3 Verificar manualmente con un bot de pruebas que un `TelegramBotsLongPollingApplication` mínimo arranca dentro de la app con Boot 4.1.1 y recibe un mensaje (log visible); descartar el código de prueba o convertirlo en la base de la tarea 5.2

## 2. Almacenamiento de archivos (`common/almacenamiento`)

- [x] 2.1 Crear `AlmacenamientoArchivos` (guardar/abrir), `ArchivoNoEncontradoException` y `AlmacenamientoLocalProperties` (`almacenamiento.local.directorio`), y verificar que compila
- [x] 2.2 Implementar `AlmacenamientoLocal`: clave `<categoria>/<yyyy>/<MM>/<dd>/<uuid>.<ext>`, escritura temporal + `ATOMIC_MOVE`, confinamiento con `normalize()` + `startsWith(base)`, creación/verificación del directorio al arrancar; verificar con tests `@TempDir` que cubren: ida y vuelta del contenido, dos archivos con el mismo nombre -> claves distintas, clave inexistente -> `ArchivoNoEncontradoException`, clave con `../` rechazada, directorio inexistente creado y directorio no escribible -> error con la ruta

## 3. Módulo `cliente`

- [x] 3.1 Crear entidad `Cliente` (tabla `cliente`, `telegram_user_id` bigint UQ, perfil, `fecha_alta`/`fecha_actualizacion` como `Instant`), `ClienteRepository` (`findByTelegramUserId`), `ClienteDTO` y `DatosClienteTelegram`; verificar que la app arranca y Hibernate crea la tabla con la restricción única en PostgreSQL local
- [x] 3.2 Implementar `ClienteService.registrarOActualizarDesdeTelegram` con alta, actualización de perfil conservando `fecha_alta`, y reintento único ante `DataIntegrityViolationException` fuera de la transacción fallida; verificar con tests unitarios (Mockito) los casos: usuario nuevo, cambio de username, colisión concurrente simulada que termina devolviendo el cliente existente

## 4. Módulo `chatbot`: modelo y persistencia

- [x] 4.1 Crear enums `Direccion` (ENTRANTE/SALIENTE), `TipoMensaje` (TEXTO/VOZ/NO_SOPORTADO), `EstadoAdjunto` (PENDIENTE/OK/ERROR) y entidades `Mensaje` (`clienteId` Long sin `@ManyToOne`, índice en `cliente_id`, UQ `(cliente_id, telegram_message_id)`, `texto` length 4096, fechas `Instant`) y `Adjunto` (`@OneToOne` a `Mensaje`, `mensaje_id` UQ); verificar que la app arranca y las tablas `mensaje` y `adjunto` se crean con sus restricciones
- [x] 4.2 Crear `MensajeRepository` (`existsByClienteIdAndTelegramMessageId`) y `AdjuntoRepository`; verificar que compila y que el método derivado se resuelve al arrancar
- [x] 4.3 Crear `GeneradorRespuesta`, `MensajeEntranteDTO` y `RespuestaFija` (`"mensaje recibido"`); verificar con un test unitario el texto devuelto

## 5. Integración con Telegram (`chatbot/telegram`)

- [x] 5.1 Crear `TelegramBotProperties` (`telegram.bot.habilitado` default false, `token`, `modo` POLLING|WEBHOOK, `webhook.url`, `webhook.secret`) con validación al arrancar que nombra la propiedad faltante, y `TelegramConfig` con el bean `TelegramClient` (`OkHttpTelegramClient`) solo si `habilitado=true`; verificar con tests de `ApplicationContextRunner`: deshabilitado sin token arranca sin beans de Telegram, habilitado sin token falla, WEBHOOK sin url/secret falla
- [x] 5.2 Implementar `ChatbotService.procesar(Update)` según design D4 (filtro privado/`message`, upsert cliente, dedupe, clasificación, adjunto PENDIENTE -> descarga sin tx -> OK/ERROR, respuesta vía `GeneradorRespuesta`, envío con `TelegramClient`, persistencia del saliente, manejo de fallos de envío); verificar con tests unitarios (Mockito) para: texto, voz OK, voz con descarga fallida -> ERROR y respuesta enviada, foto con caption -> NO_SOPORTADO sin descarga, grupo ignorado, `edited_message` ignorado, mensaje duplicado sin segunda respuesta, carrera con `DataIntegrityViolationException` sin respuesta, fallo de envío sin saliente
- [x] 5.3 Implementar `TelegramPollingIngreso` (condicional a habilitado + POLLING): `deleteWebhook` al arrancar, registro del consumer con `allowed_updates=[message]`, captura y log de errores por update; verificar manualmente con el bot de pruebas que un texto recibe `mensaje recibido` y queda persistido
- [x] 5.4 Implementar `TelegramWebhookController` (condicional a habilitado + WEBHOOK) en `POST /telegram/webhook`: validación del header con `MessageDigest.isEqual` -> 401, parseo del body `String` con el `ObjectMapper` Jackson 2 privado, 200 tras autenticar aunque el procesamiento falle; verificar con `@WebMvcTest`/MockMvc: sin header -> 401, secret incorrecto -> 401 sin invocar el servicio, secret correcto -> 200 e invocación, excepción del servicio -> 200
- [ ] 5.5 **(pendiente de verificar con webhook real)** Implementar `TelegramWebhookRegistrador` (`ApplicationReadyEvent`, `setWebhook` con url, secret y `allowed_updates=[message]`); verificar manualmente vía túnel HTTPS (ngrok) que `getWebhookInfo` muestra la URL y que un mensaje recibe respuesta en modo webhook
- [x] 5.6 Verificar que en modo POLLING `POST /telegram/webhook` responde 404 y que con `habilitado=false` la app arranca sin llamadas a Telegram (log y MockMvc/`contextLoads`)

## 6. Configuración y documentación

- [x] 6.1 Actualizar `src/main/resources/application.properties.example` con las propiedades `telegram.bot.*` y `almacenamiento.local.directorio` (token/secret por variables de entorno) y verificar que la app arranca copiando el ejemplo con `habilitado=false`
- [x] 6.2 Agregar el directorio de almacenamiento por defecto (`data/`) al `.gitignore` y verificar con `git status` que los audios guardados no aparecen como cambios
- [x] 6.3 Actualizar el README: diagrama de arquitectura de monolito modular (sin bot Python), tabla de tecnologías (TelegramBots), sección de configuración del bot (modos, variables de entorno, ngrok para webhook, un bot por entorno) y tablas `cliente`/`mensaje`/`adjunto`; verificar revisando el render del Markdown

## 7. Verificación integral

- [x] 7.1 Con PostgreSQL local y un bot de pruebas en modo POLLING, enviar desde un chat privado: un texto, una nota de voz, una foto con caption y un sticker; verificar que cada uno recibe `mensaje recibido`, que existen los registros ENTRANTE/SALIENTE correctos en `mensaje`, el adjunto de voz con estado OK y el archivo `.ogg` recuperable bajo el directorio configurado, y que la foto/sticker no generaron archivos
- [ ] 7.2 **(pendiente de verificar con webhook real)** Repetir el envío de un texto en modo WEBHOOK (túnel HTTPS) y agregar el bot a un grupo para enviar un mensaje; verificar respuesta y persistencia del privado, y que el mensaje del grupo no se persiste ni se responde
- [x] 7.3 Ejecutar `./mvnw test` y verificar que toda la suite pasa

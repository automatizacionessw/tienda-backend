# Design

## Context

Ver `proposal.md` (Why) para la motivación. Estado actual relevante:

- **Stack**: Spring Boot **4.1.1** (Spring Framework 7, Jackson 3 por defecto en Spring MVC), Java 17, Spring Data JPA + PostgreSQL, Lombok. Esquema generado por Hibernate (`ddl-auto=update`), sin migraciones.
- **Organización**: paquetes por módulo (`producto/`, `venta/`, `common/`), cada uno con Entity, Repository, Service, Controller y DTOs en el mismo paquete.
- **Convención de módulos ("regla 3")**: citada en `venta/Venta.java` pero no documentada: un módulo no debe usar Entities/Repositories de otro módulo; la colaboración es vía Service + DTO. Existe una excepción previa: `venta/Detalle` tiene `@ManyToOne Producto`. Este change **respeta** la regla para los módulos nuevos y no toca la excepción existente.
- **Configuración**: `src/main/resources/application.properties` está en `.gitignore`; se versiona `application.properties.example`.
- **Tests**: solo `TiendaBackendApplicationTests.contextLoads()` (`@SpringBootTest`).

Requisitos de comportamiento: ver `specs/telegram-recepcion`, `specs/historial-mensajes`, `specs/clientes` y `specs/almacenamiento-archivos`.

## Goals / Non-Goals

**Goals:**
- Un único flujo de procesamiento de updates, independiente del modo de ingreso (polling/webhook).
- Fronteras de módulo explícitas: `chatbot` depende de `cliente` y de `common/almacenamiento`, nunca al revés.
- Puntos de extensión para lo que viene: generación de respuestas (NLP/IA) y medio de almacenamiento (S3/MinIO).
- Aislar la integración con Telegram de la versión de Spring Boot y de Jackson 3.

**Non-Goals:**
- Enrutamiento de comandos por anotaciones (`/start`, etc.).
- Procesamiento asíncrono con colas, reintentos programados de descargas fallidas o múltiples instancias del backend.
- Migraciones de esquema versionadas (se sigue con `ddl-auto=update`).
- Resolver la excepción `Detalle -> Producto` ni documentar formalmente la regla 3 fuera de este documento.

## Decisions

### D1. Librería: TelegramBots oficial 10.x, sin starters

Se usan `org.telegram:telegrambots-client` (cliente HTTP sobre OkHttp, `OkHttpTelegramClient`) y `org.telegram:telegrambots-longpolling` (`TelegramBotsLongPollingApplication`). **No** se usan `telegrambots-springboot-longpolling-starter` ni `telegrambots-springboot-webhook-starter`; la integración con Spring se escribe a mano (unas pocas clases).

Alternativas evaluadas:

| Opción | Motivo de descarte |
|---|---|
| `io.github.drednote:spring-boot-starter-telegram` (pedida originalmente) | El modo webhook lanza `UnsupportedOperationException` ("not implemented yet"); compilada contra Boot 3.3. |
| `com.github.kshashov:spring-boot-starter-telegram` | El estilo más parecido a Spring MVC (`@BotController`), pero el webhook levanta un **Javalin embebido en otro puerto** (8443), no Spring MVC; Boot 2.7/3.x. |
| `io.github.ksilisk:telegram-bot-spring-boot-starter` | Joven (2025, ~40★), Boot 3.3.5, handlers por interfaz; poca adopción. |
| `io.github.solmosov:telegram-bot-spring-boot` | Declara Boot 4, pero exige **Java 21** y está en beta sin adopción. |
| Starters oficiales de TelegramBots | Webhook starter: no valida `secret_token`, mapea `POST /{botPath}` en la raíz (captura cualquier POST de un segmento no mapeado), deserializa con `@RequestBody` (Jackson 3 en Boot 4) y apunta a Boot 3.5.5. |

Los módulos base (`client`, `longpolling`, `meta`) no dependen de Spring, por lo que la versión de Boot deja de ser un factor de compatibilidad.

### D2. Arquitectura de ingreso: dos adaptadores, un servicio

```
                         telegram.bot.modo
                                |
        +-----------------------+------------------------+
        | POLLING                                        | WEBHOOK
        v                                                v
 TelegramPollingIngreso                        TelegramWebhookController
  - al arrancar: deleteWebhook                  POST /telegram/webhook
  - registra DefaultLongPolling-                - valida X-Telegram-Bot-Api-Secret-Token
    UpdateConsumer (1 hilo)                       (MessageDigest.isEqual) -> 401
  - allowed_updates = [message]                 - body String -> Update (ObjectMapper Jackson 2)
        |                                       - responde 200 siempre tras autenticar
        |                                      TelegramWebhookRegistrador
        |                                       - al arrancar: setWebhook(url, secret,
        |                                         allowed_updates=[message])
        +-----------------------+------------------------+
                                v
                    ChatbotService.procesar(Update)
                                |
        +-----------+-----------+------------+----------------+
        v           v                        v                v
  ClienteService  MensajeRepository   AlmacenamientoArchivos  TelegramClient
  (módulo cliente) AdjuntoRepository  (common)                (getFile, download,
                                                               sendMessage)
                    GeneradorRespuesta -> RespuestaFija("mensaje recibido")
```

- Cada adaptador se activa con `@ConditionalOnProperty(prefix="telegram.bot", name="modo", havingValue=...)` además de la condición `habilitado=true`. En modo polling el controller no existe (404); en modo webhook no hay polling.
- `allowed_updates=["message"]` se envía tanto en `setWebhook` como en `getUpdates` para que Telegram no entregue lo que igual se ignoraría. El filtro en `ChatbotService` se mantiene como defensa.
- En ambos modos la respuesta se envía con `TelegramClient.execute(SendMessage)`, **no** devolviendo el método en el cuerpo de la respuesta del webhook: así el flujo es idéntico en los dos modos y se obtiene el `message_id` del mensaje saliente para persistirlo.
- Registro/eliminación del webhook en `ApplicationReadyEvent`. La eliminación al apagar no se hace: evita que un reinicio pierda updates y el webhook es idempotente.

Alternativa descartada: interfaz `FuenteDeUpdates` con implementaciones intercambiables. Los dos adaptadores no comparten ciclo de vida (uno es un proceso de polling, el otro un endpoint), así que la abstracción común aporta poco; basta con que ambos deleguen en `ChatbotService`.

### D3. Configuración

```properties
telegram.bot.habilitado=false               # default false: opt-in explícito
telegram.bot.token=${TELEGRAM_BOT_TOKEN:}
telegram.bot.modo=POLLING                   # POLLING | WEBHOOK
telegram.bot.webhook.url=https://<host-publico>/telegram/webhook
telegram.bot.webhook.secret=${TELEGRAM_WEBHOOK_SECRET:}
almacenamiento.local.directorio=./data/archivos
```

- `@ConfigurationProperties` + `@Validated` (`TelegramBotProperties`, `AlmacenamientoLocalProperties`). Las validaciones condicionales (token obligatorio si habilitado; url+secret obligatorios si WEBHOOK) se hacen al arrancar con mensajes que nombran la propiedad faltante.
- **`habilitado=false` por defecto**: un token admite un solo consumidor; con opt-in explícito, una máquina de desarrollo no "roba" updates al bot productivo por accidente, y `contextLoads()` no intenta conectarse a Telegram.
- La ruta del endpoint es fija (`/telegram/webhook`); `webhook.url` es la URL pública completa que se registra en Telegram (puede diferir por proxies/ngrok).
- El token nunca se loguea; el logging HTTP de OkHttp queda desactivado.

### D4. Flujo de procesamiento y transacciones

```
procesar(update)
 1. ¿update.message != null && chat.type == "private"?  no -> log debug, return
 2. cliente = clienteService.registrarOActualizarDesdeTelegram(from)      [tx propia]
 3. ¿existe mensaje (cliente.id, message_id)?  sí -> return  (dedupe)
 4. tipo = TEXTO | VOZ | NO_SOPORTADO
    guardar Mensaje ENTRANTE (+ Adjunto PENDIENTE si VOZ)                  [tx corta]
    DataIntegrityViolation (carrera con reentrega) -> return
 5. si VOZ: getFile -> descargar stream -> almacenamiento.guardar()        [SIN tx]
           -> adjunto OK + clave | excepción -> adjunto ERROR              [tx corta]
 6. texto = generadorRespuesta.generar(mensajeEntrante)
 7. enviado = telegramClient.execute(SendMessage(chatId = telegram_user_id, texto))
    guardar Mensaje SALIENTE(enviado.message_id, enviado.date)            [tx corta]
    excepción de envío -> log warn, sin saliente
```

- Ninguna transacción queda abierta durante llamadas HTTP a Telegram (descarga o envío).
- La deduplicación tiene dos capas: consulta previa (paso 3) y restricción única en BD (paso 4) para la carrera entre reentregas concurrentes.
- `PENDIENTE` es un estado transitorio interno: si el proceso muere entre 4 y 5, el adjunto queda visible como pendiente en lugar de desaparecer. El spec solo exige los estados finales `OK`/`ERROR`.
- Errores no capturados en `procesar` los atrapa cada adaptador (log + seguir; en webhook, 200).
- Procesamiento síncrono: en webhook la respuesta HTTP sale tras procesar. Las notas de voz son pequeñas; si Telegram reintentara por timeout, la deduplicación absorbe la reentrega.

### D5. Fronteras de módulo

```
com.tiendabackend.tiendabackend
 +-- cliente/                 Cliente, ClienteRepository, ClienteService, ClienteDTO,
 |                            DatosClienteTelegram (DTO de entrada)
 +-- chatbot/                 ChatbotService, Mensaje, Adjunto, MensajeRepository,
 |    |                       AdjuntoRepository, enums (Direccion, TipoMensaje, EstadoAdjunto),
 |    |                       GeneradorRespuesta, RespuestaFija
 |    +-- telegram/           TelegramBotProperties, TelegramConfig (TelegramClient bean),
 |                            TelegramPollingIngreso, TelegramWebhookController,
 |                            TelegramWebhookRegistrador
 +-- common/almacenamiento/   AlmacenamientoArchivos, AlmacenamientoLocal,
                              AlmacenamientoLocalProperties, ArchivoNoEncontradoException
```

- `chatbot` usa `ClienteService` y recibe `ClienteDTO`; `Mensaje.clienteId` es un `Long` sin `@ManyToOne` (regla 3). Consecuencia: Hibernate no genera FK `mensaje.cliente_id -> cliente.id`; la integridad la garantiza la aplicación (el cliente siempre se crea antes que el mensaje) y se agrega índice sobre `cliente_id`.
- `Adjunto -> Mensaje` sí es `@OneToOne` JPA (mismo módulo).
- Las clases de la librería de Telegram (`Update`, `Message`, `TelegramClient`) solo aparecen en `chatbot/`. `cliente` recibe un DTO propio, no el `User` de Telegram.

### D6. Modelo de datos

```
cliente                                 mensaje                                     adjunto
------------------------------------    ----------------------------------------    -----------------------------------
id               bigserial PK           id                  bigserial PK            id               bigserial PK
telegram_user_id bigint NOT NULL UQ     cliente_id          bigint NOT NULL (idx)   mensaje_id       bigint NOT NULL UQ
username         varchar(64)            telegram_message_id bigint NOT NULL         tipo             varchar (VOZ)
nombre           varchar(128) NOT NULL  direccion           varchar NOT NULL        telegram_file_id varchar NOT NULL
apellido         varchar(128)           tipo                varchar NOT NULL        telegram_file_unique_id varchar NOT NULL
idioma           varchar(16)            texto               varchar(4096)           mime_type        varchar(128)
fecha_alta       timestamptz NOT NULL   fecha_telegram      timestamptz NOT NULL    tamano_bytes     bigint
fecha_actualizacion timestamptz         fecha_registro      timestamptz NOT NULL    duracion_seg     integer
                                        UQ(cliente_id, telegram_message_id)         clave_almacenamiento varchar(512)
                                                                                    estado           varchar NOT NULL
                                                                                    fecha_registro   timestamptz NOT NULL
```

- IDs de Telegram como `bigint` (los user id superan 32 bits).
- En chats privados `chat.id == from.id`: no se guarda `chat_id` aparte; para responder se usa `telegram_user_id`.
- `message_id` es único por chat y compartido por ambas direcciones, por eso `UQ(cliente_id, telegram_message_id)` sirve para entrantes y salientes.
- Fechas como `Instant` (mapeadas a `timestamp with time zone`); `fecha_telegram` viene del epoch UTC de Telegram. Se diverge a propósito del `LocalDateTime` usado en `venta`.
- Enums con `@Enumerated(EnumType.STRING)`.
- `texto` hasta 4096 (máximo de Telegram para texto; caption hasta 1024).

### D7. Upsert de cliente con concurrencia

`registrarOActualizarDesdeTelegram`: buscar por `telegram_user_id` -> si existe, actualizar campos de perfil; si no, insertar. Ante `DataIntegrityViolationException` por la UQ (dos mensajes del mismo usuario nuevo en paralelo), se reintenta una vez leyendo el registro ya creado. El reintento ocurre fuera de la transacción fallida (el método transaccional se invoca desde un envoltorio no transaccional).

Alternativa descartada: `INSERT ... ON CONFLICT` nativo de PostgreSQL. Más atómico, pero ata el módulo a SQL nativo y el volumen no lo justifica.

### D8. Almacenamiento de archivos

```java
public interface AlmacenamientoArchivos {
    String guardar(String categoria, InputStream contenido, String extension); // -> clave
    InputStream abrir(String clave);  // ArchivoNoEncontradoException si no existe
}
```

- **Clave** relativa y opaca: `<categoria>/<yyyy>/<MM>/<dd>/<uuid>.<ext>` (p. ej. `voz/2026/09/26/3f2a....ogg`). La BD guarda la clave; cambiar a S3 significa usar la misma clave como object key.
- `AlmacenamientoLocal`: `base.resolve(clave).normalize()` y verificación `startsWith(base)` para rechazar recorridos (`../`). Escritura a archivo temporal en el mismo directorio y `Files.move(ATOMIC_MOVE)` para no dejar archivos parciales.
- Al arrancar (`@PostConstruct`) crea el directorio base si falta y verifica `Files.isWritable`; si falla, excepción con la ruta.
- Extensión derivada del MIME (`audio/ogg` -> `ogg`; desconocido -> `bin`).
- `eliminar` no se incluye en esta iteración (no hay caso de uso).

### D9. Jackson 2 vs Jackson 3

`telegrambots-meta` modela la API con Jackson 2 (`com.fasterxml.jackson`), incluidos deserializadores propios para tipos polimórficos. Spring MVC en Boot 4 usa Jackson 3 (`tools.jackson`), que ignora deserializadores de Jackson 2. Por eso el webhook recibe el cuerpo como `String` y lo deserializa con un `com.fasterxml.jackson.databind.ObjectMapper` propio (instancia privada del módulo, **no** bean de Spring, para no interferir con la autoconfiguración de Jackson de Boot). Ambas versiones conviven en el classpath al usar paquetes distintos. El polling no pasa por Spring MVC y no se ve afectado.

### D10. Punto de extensión de respuestas

`GeneradorRespuesta { String generar(MensajeEntranteDTO mensaje); }` con implementación única `RespuestaFija` que devuelve `"mensaje recibido"`. El futuro NLP/IA se conecta reemplazando este bean, sin tocar ingreso ni persistencia.

## Risks / Trade-offs

- **[Compatibilidad de TelegramBots 10.x con Boot 4.1.1 / sus versiones gestionadas (OkHttp, Jackson 2)]** → Primera tarea: spike que agrega las dependencias, compila, arranca en polling contra un bot de pruebas y deserializa updates de ejemplo (texto, voz, foto, sticker) con el `ObjectMapper` de la librería. Si hay conflicto de versiones, fijarlas en `<dependencyManagement>`.
- **[Un token = un consumidor]**: el bot Python, otra instancia o una máquina de desarrollo con polling compiten (409) o desvían updates → `habilitado=false` por defecto; usar bots distintos para desarrollo y producción; apagar el bot Python antes de habilitar este.
- **[Webhook requiere HTTPS público]** → En desarrollo usar polling o un túnel (ngrok); documentarlo en README.
- **[Sin FK `mensaje.cliente_id`]** por respetar la frontera de módulos → integridad garantizada por el flujo (cliente antes que mensaje) + índice; reevaluar si se adoptan migraciones.
- **[Procesamiento síncrono y secuencial]** (polling de un hilo; webhook responde tras procesar) → suficiente para el volumen esperado; si crece, mover a ejecución asíncrona manteniendo la deduplicación.
- **[Descargas fallidas no se reintentan]** → el adjunto queda `ERROR` con `telegram_file_id`, que permite un reintento manual o futuro job mientras Telegram conserve el archivo.
- **[Archivos en disco local]**: sin réplica, ligado a una sola instancia, crecimiento sin política de retención → la abstracción permite migrar a almacenamiento de objetos; retención queda como pregunta abierta.
- **[`ddl-auto=update`]** no elimina ni renombra columnas y no versiona cambios → consistente con el proyecto actual; recomendable migrar a Flyway en un change aparte.
- **[Endpoint de webhook público]** → autenticación por secret con comparación en tiempo constante; el cuerpo solo se parsea tras autenticar.
- **[Dos versiones de Jackson en classpath]** → uso aislado dentro de `chatbot/telegram`; nunca exponer el `ObjectMapper` Jackson 2 como bean.

## Migration Plan

1. Desplegar con `telegram.bot.habilitado=false` (default): se crean las tablas nuevas, sin efecto sobre Telegram.
2. Detener el bot Python (deja de consumir el token).
3. Configurar `TELEGRAM_BOT_TOKEN`, modo y, si es webhook, `webhook.url` + `TELEGRAM_WEBHOOK_SECRET`; habilitar y reiniciar.
4. Verificar enviando texto, nota de voz y foto desde un chat privado; revisar tablas y directorio de almacenamiento.

**Rollback**: `telegram.bot.habilitado=false` y reinicio. En modo webhook, además, eliminar el webhook (`deleteWebhook`) si se vuelve al bot Python con polling. Las tablas y archivos nuevos no afectan a los módulos existentes y pueden conservarse.

## Open Questions

- ¿Sigue siendo necesario el servidor MCP cuando la IA se ejecute dentro del backend? No afecta a esta iteración.
- Política de retención de audios y mensajes (plazo, borrado, datos personales).
- ¿Transcripción de notas de voz como parte del futuro NLP? Se apoyaría en los adjuntos ya almacenados.

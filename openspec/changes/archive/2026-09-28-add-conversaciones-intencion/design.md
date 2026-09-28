# Design

## Context

La motivación está en `proposal.md` (Why) y los requisitos en `specs/conversaciones`, `specs/historial-mensajes` y `specs/documentacion-api`. Del estado actual importa lo siguiente:

- **Mensajes**: `chatbot/Mensaje` tiene `clienteId` (un `Long` sin relación JPA, porque el cliente es de otro módulo) y la restricción única `(cliente_id, telegram_message_id)`, que sirve para deduplicar. No existe ningún agrupador.
- **Flujo de ingreso**: `ChatbotService.procesar` sigue estos pasos:
  1. upsert del cliente;
  2. deduplicación;
  3. guardar el entrante en una transacción corta. Si esa transacción choca con una restricción única (`DataIntegrityViolationException`), lo interpreta como una reentrega concurrente y descarta el mensaje;
  4. descargar la nota de voz, sin transacción;
  5. responder;
  6. guardar el saliente en una transacción corta.

  Las transacciones se manejan con `TransactionTemplate` para que ninguna quede abierta durante las llamadas a Telegram.
- **`ChatbotService`** solo existe con `telegram.bot.habilitado=true`.
- **Esquema**: lo genera Hibernate con `ddl-auto=update`, sin migraciones. No hay procesos programados (`@EnableScheduling`).
- **API REST**: sigue el estilo de `VentaController`: `PATCH` para transiciones de estado (`/confirmar`, `/cancelar`), errores de negocio con cuerpo `{error}` (400 o 409) y 404 con `{timestamp, status, mensaje}`. Todo está documentado con springdoc, y cada forma de error tiene su record en `common/openapi`.
- **Tests**: unitarios con Mockito, `@WebMvcTest` para los controladores y `contextLoads` contra el PostgreSQL local. No hay base embebida.

## Goals / Non-Goals

**Goals:**
- Que la vigencia de una conversación sea una sola regla de dominio que usen igual el ingreso, las consultas y los endpoints.
- Garantizar "una conversación vigente por cliente" y "una intención pendiente por tipo" incluso con peticiones concurrentes (el webhook puede procesar updates en paralelo).
- Que los errores de la API sean legibles para un LLM: un 400 debe decir qué valores son válidos.
- No cambiar el comportamiento visible del bot, que sigue respondiendo `mensaje recibido`.

**Non-Goals:**
- Un proceso programado de cierre o migraciones con Flyway.
- Paginar `GET /api/conversaciones`, que se agrega cuando haya volumen real.
- Exponer el audio de las notas de voz en la API. El detalle informa el tipo `VOZ` y el caption.

## Decisions

### D1. Ubicación: dentro del módulo `chatbot`

`Conversacion`, `ConversacionIntencion`, sus enums, repositorios, `ConversacionService`, `ConversacionController` y los DTOs van en el paquete `chatbot/`, con la misma organización plana que ya tiene `Mensaje`. Al ser el mismo módulo, `Mensaje → Conversacion` y `ConversacionIntencion → Conversacion/Mensaje` son relaciones JPA reales (`@ManyToOne`), sin violar la regla de módulos. Con `cliente` se sigue colaborando solo por `clienteId`.

`ConversacionService` y el controlador **no** son condicionales a `telegram.bot.habilitado`, porque la API tiene que funcionar con el bot apagado. `ChatbotService`, que sí es condicional, depende de `ConversacionService`, nunca al revés.

Descartado: un módulo `conversacion/` separado. Obligaría a guardar `mensaje.conversacion_id` como un `Long` sin relación JPA y a resolver los mensajes del detalle a través de DTOs entre módulos, y todo eso para dos entidades que solo tienen sentido junto a `Mensaje`.

### D2. Modelo de datos

```
conversacion                                    conversacion_intencion
--------------------------------------------    ---------------------------------------------
id                   bigserial PK               id                bigserial PK
cliente_id           bigint NOT NULL (idx)      conversacion_id   bigint NOT NULL FK (idx)
cliente_abierta      bigint NULL UQ   (D4)      intencion         varchar(32) NOT NULL
estado               varchar(16) NOT NULL       estado            varchar(16) NOT NULL
motivo_cierre        varchar(32) NULL           detalle           varchar(500) NULL
fecha_inicio         timestamptz NOT NULL       mensaje_id        bigint NULL FK
fecha_ultimo_mensaje timestamptz NOT NULL       origen            varchar(16) NOT NULL
fecha_cierre         timestamptz NULL           fecha_deteccion   timestamptz NOT NULL
                                                fecha_resolucion  timestamptz NULL
mensaje
--------------------------------------------
+ conversacion_id    bigint NOT NULL FK (idx)
```

Enums, todos con `@Enumerated(STRING)`:
- `EstadoConversacion`: `ABIERTA`, `CERRADA`.
- `MotivoCierre`: `INACTIVIDAD`, `INTENCION_RESUELTA`, `MANUAL`.
- `Intencion`: `SALUDO`, `CONSULTA_CATALOGO`, `INICIAR_PEDIDO`, `CONSULTAR_ESTADO_PEDIDO`, `OTRA`.
- `EstadoIntencion`: `PENDIENTE`, `RESUELTA`.
- `OrigenClasificacion`: `MANUAL`, `LLM`.

Fechas como `Instant`, igual que en `Mensaje`. `Mensaje.clienteId` se mantiene: lo usan la restricción única de deduplicación y el índice por cliente.

Las intenciones son un enum y no una tabla porque el catálogo está atado al código de las herramientas que las resolverán, y un conjunto cerrado se puede validar y documentar en OpenAPI. Cambiar el catálogo implica desplegar, y eso es aceptable en una prueba de concepto.

### D3. Vigencia: se evalúa al usarla, con reloj y umbral inyectados

```
vigente(c, ahora) = c.estado == ABIERTA && ahora - c.fechaUltimoMensaje < inactividad

estado efectivo (consultas):
  vigente              -> ABIERTA
  CERRADA              -> lo guardado (motivo, fecha_cierre)
  ABIERTA pero vencida -> CERRADA, INACTIVIDAD, fechaUltimoMensaje + inactividad
```

- La regla vive en un solo método de dominio de `Conversacion`. Lo usan:
  - el ingreso, comparando la `fechaTelegram` del mensaje nuevo contra `fechaUltimoMensaje`;
  - los endpoints, comparando `ahora` contra `fechaUltimoMensaje`;
  - el mapeo a DTO, que calcula el estado efectivo.
- Los filtros de `GET /api/conversaciones` repiten la regla en JPQL con un parámetro `limite = ahora - inactividad`:
  - `estado=ABIERTA` se traduce en `estado = ABIERTA AND fechaUltimoMensaje > :limite`;
  - `estado=CERRADA` es la negación.

  `sinClasificar=true` se traduce en `NOT EXISTS` sobre las intenciones.
- **Solo el ingreso persiste el cierre por inactividad.** Las lecturas no escriben. Cuando un endpoint de escritura recibe una conversación vencida, responde 409 sin cerrarla, porque la excepción revierte la transacción de todos modos.
- `fechaUltimoMensaje` se actualiza con `max(actual, fechaTelegram)` en cada mensaje entrante y en cada saliente de una conversación `ABIERTA`. Tomar el máximo cubre las reentregas atrasadas.
- `ConversacionProperties` (`@ConfigurationProperties("conversacion")`, `@Validated`) define `inactividad` como `Duration`, con valor por defecto `2h` y validación de positivo. Siguiendo la estrategia de `configuracion-entorno`, `application.properties` la declara como `conversacion.inactividad=${CONVERSACION_INACTIVIDAD:2h}`. La variable se agrega a `docker-compose.yml` y a `docker/.env.example`, y en desarrollo se puede sobrescribir en `application-local.properties` (por ejemplo `1m` para probar a mano).
- Un bean `Clock` (`Clock.systemUTC()`) se inyecta en `ConversacionService` para que los tests controlen el tiempo sin esperar.

Descartado: el scheduler solo, o el scheduler más la evaluación al usarla. El ingreso tiene que evaluar la vigencia igual, porque entre dos corridas del job queda un hueco, y con la regla aplicada en las consultas el job no aporta nada observable. Queda como Non-Goal.

### D4. Asignación de mensajes y concurrencia

**Una conversación vigente por cliente.** PostgreSQL permite un índice único parcial (`WHERE estado='ABIERTA'`), pero Hibernate no lo genera. En su lugar se usa la columna `cliente_abierta`, con restricción única y nullable:
- vale `cliente_id` mientras la conversación está `ABIERTA`;
- vale `NULL` al cerrarla.

PostgreSQL admite varios `NULL` en una columna única, así que la restricción queda equivalente al índice parcial y Hibernate sí la genera.

```
ChatbotService.procesar(update)
 1. cliente = upsert                                        (sin cambios)
 2. dedupe por (cliente, message_id)                        (sin cambios)
 3. conversacionId = conversacionService.asignarEntrante(cliente.id, fechaTelegram)
      tx propia:
        c = findByClienteAbierta(clienteId)  [PESSIMISTIC_WRITE]
        si c existe y vigente(c, fechaTelegram): actualizar fechaUltimoMensaje
        si c existe y vencida: cerrar INACTIVIDAD (fecha = ultimo + inactividad),
                               cliente_abierta = NULL, flush   <-- ver nota
                               y abrir nueva
        si no existe: abrir nueva (cliente_abierta = clienteId), saveAndFlush
      DataIntegrityViolation (otro hilo abrio a la vez) -> reintentar 1 vez en tx nueva
 4. guardarEntrante(..., conversacionId)  tx corta  (mensaje.conversacion = referencia)
 5. descarga de voz                                         (sin cambios)
 6. respuesta; saliente con la conversacion del entrante,
    y conversacionService.registrarSaliente(conversacionId, fecha) en la misma tx
```

- **Nota sobre el flush**: Hibernate ejecuta los INSERT antes que los UPDATE. Sin un `flush` explícito entre cerrar la conversación vieja y abrir la nueva, el INSERT de la nueva choca con la vieja por `cliente_abierta`.
- **Por qué la asignación va en una transacción aparte (paso 3) y no dentro de `guardarEntrante`**: `guardarEntrante` interpreta cualquier `DataIntegrityViolationException` como reentrega y descarta el mensaje. Una colisión por `cliente_abierta` se confundiría con eso y el mensaje se perdería. El reintento sigue el patrón de `ClienteService.registrarOActualizarDesdeTelegram`.
- **Consecuencia aceptada**: si dos entregas del mismo mensaje compiten, las dos pasan el paso 3 y una se descarta en el paso 4. La conversación queda con `fechaUltimoMensaje` actualizada, lo que es correcto porque el mensaje existe. Si era el primer mensaje del cliente, las dos entregas comparten la conversación abierta por el reintento, así que no quedan conversaciones vacías.

**Operaciones de la API.** Registrar, resolver y cerrar toman la conversación con `@Lock(PESSIMISTIC_WRITE)`. Eso las serializa entre sí y con `asignarEntrante`, porque un cierre manual y un mensaje entrante no pueden cruzarse: o el mensaje entra antes del cierre, o abre una conversación nueva. La regla "una intención `PENDIENTE` por tipo" se verifica en el servicio con la fila bloqueada.

Descartado: resolver esa unicidad con una columna nullable como `cliente_abierta`. Con el bloqueo por conversación alcanza, y no agrega columnas artificiales.

### D5. API REST

| Método y ruta | Éxito | Errores |
|---|---|---|
| `GET /api/conversaciones?clienteId=&estado=&sinClasificar=` | 200, lista sin mensajes | 400 si `estado` es inválido |
| `GET /api/conversaciones/{id}` | 200, con mensajes e intenciones | 404 |
| `POST /api/conversaciones/{id}/intenciones` | 201, la intención | 400, 404, 409 |
| `PATCH /api/conversaciones/{id}/intenciones/{intencionId}/resolver` | 200, la intención | 404, 409 |
| `PATCH /api/conversaciones/{id}/cerrar` | 200, la conversación | 400, 404, 409 |

- **Cuerpos de petición**:
  - `IntencionRequestDTO { intencion, detalle, mensajeId, origen }`;
  - `CierreRequestDTO { motivo }`.

  Los campos enumerados se reciben como `String` y los parsea el servicio. Así un valor inválido (`RECLAMO`) devuelve un 400 `{error}` que lista los valores válidos, en lugar del error genérico de Jackson. Esto importa porque el consumidor final es un LLM. En OpenAPI se documentan con `allowableValues`.
- **Validaciones con Bean Validation**: `intencion` obligatoria y `detalle` de hasta 500 caracteres, con respuesta 400 `{errores}` como el resto de la API.
- **Validaciones en el servicio**: `OTRA` sin detalle, `mensajeId` que no es un entrante de la conversación, `motivo=INACTIVIDAD` y cualquier enum inválido, con respuesta 400 `{error}`.
- **Excepciones nuevas en `common/exception`**, cada una con su handler en `GlobalExceptionHandler` y el mismo cuerpo `{error}` que ya documenta `ErrorSimple`:
  - `SolicitudInvalidaException`, que responde 400;
  - `EstadoConversacionInvalidoException`, que responde 409 y cubre la conversación no vigente, la intención pendiente duplicada, la intención ya resuelta y el cierre por resolución con intenciones pendientes.
- **404**: se reutiliza `RecursoNoEncontradoException`. Una intención que no pertenece a la conversación de la ruta también da 404.
- **OpenAPI**: grupo `TAG_CONVERSACIONES = "Conversaciones"` en `OpenApiConfig`, y la descripción general de la API menciona la clasificación de intenciones.

### D6. `MensajeEntranteDTO` con `conversacionId`

Se agrega `conversacionId` al DTO que recibe `GeneradorRespuesta`. `RespuestaFija` lo ignora, pero el futuro generador basado en LLM lo necesita para consultar y clasificar la conversación. El costo es un campo más.

### D7. Datos existentes: descarte

`mensaje.conversacion_id` es `NOT NULL` desde el inicio porque los datos actuales son de desarrollo (decisión del usuario). Si la tabla `mensaje` tiene filas, Hibernate no puede agregar la columna `NOT NULL`: registra el error en el log y la aplicación arranca sin la columna. Por eso vaciarla es un paso **previo** obligatorio del despliegue (ver Migration Plan). Los archivos de voz también se borran para no dejarlos huérfanos.

## Risks / Trade-offs

- **[Filas `ABIERTA` vencidas en la base]**: su cierre recién se persiste cuando el cliente vuelve a escribir → ninguna lectura de la aplicación las trata como abiertas, porque el estado efectivo se aplica en DTOs y filtros. Quien consulte la base directamente tiene que aplicar la misma regla, y el README lo explica.
- **[La regla de vigencia está duplicada, en Java y en JPQL]** → un test del servicio cubre los dos caminos con el mismo `Clock` fijo y compara resultados en el límite exacto (`ahora - inactividad`).
- **[Olvidar el paso de vaciado]**: la aplicación arranca, pero falla en el primer mensaje → el paso queda en el README y en el Migration Plan, y la verificación manual incluye revisar que exista la columna.
- **[Reloj de Telegram frente al reloj del servidor]**: el ingreso compara fechas de Telegram y las consultas usan `ahora` del servidor → con NTP la diferencia es de segundos, despreciable frente a 2 horas.
- **[Bloqueo pesimista por conversación]**: serializa las operaciones sobre una misma conversación → el volumen por conversación es mínimo (un cliente), y conversaciones distintas no se bloquean entre sí.
- **[Un saliente puede llegar a una conversación cerrada entre la recepción y la respuesta]**: por ejemplo, un cierre manual justo en ese intervalo → el saliente queda en la conversación del entrante que respondió, que es lo que pide el spec, y `fechaUltimoMensaje` no se actualiza porque la conversación ya no está `ABIERTA`.
- **[Enum de intenciones]**: cambiar el catálogo requiere desplegar, y renombrar un valor deja filas huérfanas → aceptable en una prueba de concepto; el catálogo definitivo se decide después.

## Migration Plan

1. Detener la aplicación.
2. Vaciar los datos del bot: `TRUNCATE adjunto, mensaje;` y borrar el contenido de `<almacenamiento.local.directorio>/voz/`. `cliente` se conserva.
3. Desplegar la versión nueva. Hibernate crea `conversacion` y `conversacion_intencion` y agrega `mensaje.conversacion_id`.
4. Verificar en PostgreSQL que `mensaje.conversacion_id` existe y es `NOT NULL`, y que `conversacion.cliente_abierta` tiene restricción única.
5. Enviar un mensaje de prueba al bot y consultar `GET /api/conversaciones?estado=ABIERTA`.

**Rollback**: volver a la versión anterior. La columna `mensaje.conversacion_id NOT NULL` queda en la base (`ddl-auto=update` no la borra) e impide insertar mensajes con la versión vieja. Para volver atrás hay que ejecutar `ALTER TABLE mensaje DROP COLUMN conversacion_id;`. Las tablas nuevas pueden quedarse.

## Open Questions

- ¿Cuándo hace falta paginar `GET /api/conversaciones`? Depende del volumen real y no cambia el contrato de filtros.

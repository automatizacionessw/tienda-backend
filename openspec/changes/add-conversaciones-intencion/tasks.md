# Tasks

## 1. Modelo y configuración

- [x] 1.1 Crear los enums `EstadoConversacion`, `MotivoCierre`, `Intencion`, `EstadoIntencion` y `OrigenClasificacion` en `chatbot/`, y verificar que compila
- [x] 1.2 Crear `ConversacionProperties` (`conversacion.inactividad`, `Duration`, por defecto `2h`, validada como positiva) y un bean `Clock.systemUTC()`. Verificar con un test de `ApplicationContextRunner` que sin la propiedad vale 2 horas y que con `conversacion.inactividad=0s` el arranque falla nombrando la propiedad
- [x] 1.3 Crear la entidad `Conversacion` según design D2: `cliente_abierta` nullable con restricción única, índice por `cliente_id` y fechas `Instant`. Incluir los métodos de dominio de vigencia y estado efectivo (D3). Verificar con tests unitarios la vigencia justo antes y justo en el límite de inactividad, y el estado efectivo de una conversación `ABIERTA` vencida (`CERRADA`, `INACTIVIDAD`, `fechaUltimoMensaje + inactividad`)
- [ ] 1.4 Crear la entidad `ConversacionIntencion` (`@ManyToOne` a `Conversacion` y a `Mensaje` opcional, `detalle` de hasta 500 caracteres) y agregar a `Mensaje` la relación `@ManyToOne(optional = false)` con `Conversacion` más un índice por `conversacion_id`. Verificar contra el PostgreSQL local, con `mensaje` vacía, que Hibernate crea las tablas `conversacion` y `conversacion_intencion`, la columna `mensaje.conversacion_id NOT NULL` y la restricción única de `cliente_abierta`
- [ ] 1.5 Crear `ConversacionRepository` con:
  - la búsqueda por `clienteAbierta` con `PESSIMISTIC_WRITE`;
  - la búsqueda por id con `PESSIMISTIC_WRITE`;
  - la consulta JPQL de listado con filtros de cliente, estado efectivo (parámetro `limite`) y `sinClasificar` (`NOT EXISTS`), ordenada por `fechaUltimoMensaje` descendente.

  Crear también `ConversacionIntencionRepository` y los métodos necesarios en `MensajeRepository` (mensajes de una conversación en orden cronológico). Verificar que la aplicación arranca, lo que confirma que Spring Data resolvió las consultas

## 2. Asignación de mensajes a conversaciones

- [x] 2.1 Implementar `ConversacionService.asignarEntrante(clienteId, fechaTelegram)` según design D4:
  - transacción propia y bloqueo sobre la conversación abierta;
  - si está vencida, cerrarla con `INACTIVIDAD`, poner `cliente_abierta = NULL` y hacer `flush` antes de abrir la nueva;
  - actualizar `fechaUltimoMensaje` con el máximo;
  - reintentar una vez ante `DataIntegrityViolationException`.

  Verificar con tests unitarios (Mockito y `Clock` fijo): primer mensaje abre conversación, mensaje a los 30 minutos reutiliza la conversación, mensaje a las 2 horas y 5 minutos cierra la anterior y abre otra, mensaje después de un cierre `MANUAL` abre otra, y una colisión simulada termina devolviendo la conversación que abrió el otro hilo
- [x] 2.2 Implementar `ConversacionService.registrarSaliente(conversacionId, fecha)`, que actualiza `fechaUltimoMensaje` solo si la conversación está `ABIERTA`. Verificar con tests unitarios los casos de conversación abierta y cerrada
- [x] 2.3 Modificar `ChatbotService.procesar` para usar `asignarEntrante` entre la deduplicación y `guardarEntrante`, asignar la conversación al entrante y al saliente, y llamar a `registrarSaliente` en la transacción del saliente. Agregar `conversacionId` a `MensajeEntranteDTO`. Actualizar `ChatbotServiceTest` y verificar con `./mvnw test` que los casos existentes siguen pasando y que el entrante y el saliente quedan en la misma conversación
- [x] 2.4 Verificar que una `DataIntegrityViolationException` al guardar el entrante (reentrega concurrente) sigue descartando el mensaje sin respuesta, y que una colisión en la asignación **no** descarta el mensaje. Cubrir ambos casos con tests en `ChatbotServiceTest`

## 3. Intenciones y cierre (servicio)

- [x] 3.1 Crear `SolicitudInvalidaException` (400 `{error}`) y `EstadoConversacionInvalidoException` (409 `{error}`) en `common/exception`, con sus handlers en `GlobalExceptionHandler`. Verificar con un test de `@WebMvcTest` sobre un controlador de prueba que cada una produce el código y el cuerpo esperados, y que los handlers existentes no cambian
- [x] 3.2 Implementar `ConversacionService.registrarIntencion`:
  - parsear `intencion` y `origen` desde `String`, con error que lista los valores válidos y `MANUAL` por defecto;
  - exigir detalle si la intención es `OTRA`;
  - validar que `mensajeId` sea un entrante de la conversación;
  - bloquear la conversación y exigir que esté vigente;
  - rechazar una intención `PENDIENTE` duplicada del mismo tipo.

  Verificar con tests unitarios cada escenario del requirement "Registro de intenciones" y "Catálogo de intenciones" de `specs/conversaciones`
- [x] 3.3 Implementar `ConversacionService.resolverIntencion`: 404 si la intención no es de esa conversación, 409 si ya está `RESUELTA` o si la conversación no está vigente, y en el caso válido estado `RESUELTA` con su fecha. Verificar con tests unitarios los escenarios de "Resolución de intenciones" y que la conversación sigue `ABIERTA`
- [x] 3.4 Implementar `ConversacionService.cerrar`:
  - `INTENCION_RESUELTA` exige al menos una intención y todas resueltas;
  - `MANUAL` no tiene condiciones;
  - `INACTIVIDAD` o un valor inválido responden 400;
  - una conversación no vigente responde 409;
  - al cerrar, poner `cliente_abierta = NULL` y guardar la fecha de cierre.

  Verificar con tests unitarios los escenarios de "Cierre explícito de conversaciones" y "Conversaciones no vigentes son inmutables"
- [x] 3.5 Implementar `ConversacionService.listar` y `obtenerDetalle` con mapeo a DTO por estado efectivo (`ConversacionResponseDTO`, `ConversacionDetalleResponseDTO`, `IntencionResponseDTO`, `MensajeResponseDTO`). Verificar con tests unitarios que una conversación vencida se informa como `CERRADA`/`INACTIVIDAD` con la fecha de cierre calculada, y que el detalle ordena los mensajes cronológicamente

## 4. API REST y documentación

- [x] 4.1 Crear `ConversacionController` (`/api/conversaciones`) con las cinco operaciones de design D5 y los DTOs de petición `IntencionRequestDTO` y `CierreRequestDTO`, con Bean Validation. Verificar con `@WebMvcTest`/MockMvc:
  - códigos 200/201/400/404/409 de cada operación;
  - el cuerpo `{error}` que lista los valores válidos ante `intencion=RECLAMO`;
  - el filtro `estado=INVALIDO` responde 400
- [ ] 4.2 Documentar el controlador y los DTOs con springdoc, en español: grupo `Conversaciones` en `OpenApiConfig`, resumen y descripción de cada operación, parámetros de ruta y de consulta, campos con ejemplo, `allowableValues` en los enums y respuestas 400/404/409 con sus records de `common/openapi`. Actualizar la descripción general de la API. Extender `DocumentacionApiHabilitadaTest` para verificar que `/v3/api-docs` contiene las cinco rutas nuevas y que el esquema de registro de intenciones enumera las cinco intenciones
- [ ] 4.3 Verificar con un test de contexto (bot deshabilitado) que `GET /api/conversaciones` responde 200, lo que confirma que `ConversacionService` y el controlador no dependen de `telegram.bot.habilitado`

## 5. Configuración, documentación y verificación integral

- [ ] 5.1 Agregar `conversacion.inactividad=2h` con un comentario a `application.properties.example`, y verificar que la aplicación arranca con el ejemplo copiado
- [x] 5.2 Actualizar el README:
  - modelo de datos: tablas `conversacion` y `conversacion_intencion`, y `mensaje.conversacion_id`;
  - descripción del ciclo de vida y del estado efectivo, incluido que una fila `ABIERTA` en la base puede estar vencida;
  - paso previo obligatorio de despliegue: vaciar `adjunto`/`mensaje` y borrar los audios.

  Verificar revisando el Markdown renderizado
- [ ] 5.3 Con el PostgreSQL local vaciado según el Migration Plan y el bot en modo POLLING:
  - enviar "hola" y "quiero ver el catálogo", y verificar que existe una sola conversación `ABIERTA` con los cuatro mensajes;
  - por Swagger UI, registrar `SALUDO` y `CONSULTA_CATALOGO`, intentar duplicar `CONSULTA_CATALOGO` (409), intentar cerrar con `INTENCION_RESUELTA` (409), resolver ambas y cerrar (200);
  - enviar otro mensaje y verificar que abre una conversación nueva
- [ ] 5.4 Con `conversacion.inactividad=1m` en local, dejar pasar más de un minuto sin escribir y verificar que `GET /api/conversaciones?estado=ABIERTA` ya no la lista, que el detalle la informa como `CERRADA`/`INACTIVIDAD`, que registrar una intención responde 409 y que el siguiente mensaje abre una conversación nueva y persiste el cierre de la anterior en la base
- [ ] 5.5 Ejecutar `./mvnw test` y verificar que toda la suite pasa

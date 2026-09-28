# conversaciones Specification

## Purpose

Agrupa los mensajes entre cada cliente y el bot en conversaciones con un ciclo de vida (apertura, vencimiento por inactividad y cierre explícito) y permite clasificar y resolver sus intenciones a través de una API REST. Esa clasificación es la que después usará el LLM para decidir cuándo y con qué herramientas actuar.

## Requirements

### Requirement: Agrupación de mensajes en conversaciones
Cada mensaje persistido MUST pertenecer a exactamente una conversación, y cada conversación MUST pertenecer a un único cliente. Un cliente MUST tener como máximo una conversación vigente a la vez.
- Un mensaje entrante MUST asignarse a la conversación vigente de su cliente. Si el cliente no tiene ninguna, el sistema MUST abrir una conversación nueva con estado `ABIERTA` y asignarle el mensaje.
- Un mensaje saliente MUST asignarse a la misma conversación que el mensaje entrante al que responde.

Cada conversación registra la fecha de inicio (la del primer mensaje), la fecha de su último mensaje y, cuando corresponde, la fecha y el motivo de cierre.

#### Scenario: Primer mensaje de un cliente
- **WHEN** un cliente sin conversaciones envía "hola" por chat privado
- **THEN** existe una conversación `ABIERTA` de ese cliente cuya fecha de inicio es la fecha de Telegram de "hola"
- **AND** el mensaje entrante y la respuesta del bot pertenecen a esa conversación

#### Scenario: Mensajes dentro de la ventana de actividad
- **WHEN** un cliente con una conversación vigente envía otro mensaje 30 minutos después del último
- **THEN** el mensaje se asigna a esa misma conversación y no se abre ninguna nueva
- **AND** la fecha del último mensaje de la conversación se actualiza

#### Scenario: Mensajes simultáneos de un cliente sin conversación
- **WHEN** llegan a la vez dos mensajes de un cliente que no tiene conversación vigente
- **THEN** ambos mensajes quedan en la misma conversación y el cliente tiene una sola conversación vigente

### Requirement: Vencimiento por inactividad
Una conversación MUST considerarse vigente solo si su estado es `ABIERTA` y todavía no pasó el tiempo de inactividad desde la fecha de su último mensaje, entrante o saliente, según Telegram. El tiempo de inactividad MUST ser configurable y MUST valer 2 horas por defecto.

Una conversación `ABIERTA` que superó ese tiempo MUST tratarse en todas las consultas y operaciones como cerrada: motivo `INACTIVIDAD` y fecha de cierre igual a la fecha del último mensaje más el tiempo de inactividad. Esto vale aunque su cierre todavía no se haya registrado. El siguiente mensaje del cliente MUST abrir una conversación nueva.

#### Scenario: Mensaje después de la inactividad
- **WHEN** un cliente envía un mensaje 2 horas y 5 minutos después del último mensaje de su conversación
- **THEN** la conversación anterior queda cerrada con motivo `INACTIVIDAD` y fecha de cierre igual a su último mensaje más 2 horas
- **AND** el mensaje nuevo pertenece a una conversación nueva `ABIERTA`

#### Scenario: Conversación vencida sin mensajes nuevos
- **WHEN** pasaron 3 horas desde el último mensaje de una conversación y nadie volvió a escribir
- **THEN** al consultarla se informa con estado `CERRADA`, motivo `INACTIVIDAD` y fecha de cierre igual a su último mensaje más 2 horas
- **AND** no aparece entre las conversaciones abiertas

### Requirement: Catálogo de intenciones
El sistema MUST admitir exactamente estas intenciones:
- `SALUDO`
- `CONSULTA_CATALOGO`
- `INICIAR_PEDIDO`
- `CONSULTAR_ESTADO_PEDIDO`
- `OTRA`, para lo que no corresponde a ninguna de las anteriores.

Una intención `OTRA` MUST llevar un detalle en texto libre que describa qué pidió el cliente. En las demás intenciones el detalle es opcional. El detalle admite hasta 500 caracteres.

#### Scenario: Intención fuera del catálogo con detalle
- **WHEN** se registra la intención `OTRA` con el detalle "pregunta por la garantía de un producto"
- **THEN** la intención queda registrada con ese detalle

#### Scenario: Intención fuera del catálogo sin detalle
- **WHEN** se intenta registrar la intención `OTRA` sin detalle
- **THEN** el sistema responde 400 y no registra la intención

#### Scenario: Intención inexistente
- **WHEN** se intenta registrar la intención `RECLAMO`
- **THEN** el sistema responde 400 y no registra la intención

### Requirement: Registro de intenciones
El sistema MUST permitir registrar una intención en una conversación vigente con `POST /api/conversaciones/{id}/intenciones`. La petición indica:
- la intención;
- el detalle;
- opcionalmente, el mensaje entrante de esa conversación que la originó;
- el origen de la clasificación, `MANUAL` o `LLM`, que vale `MANUAL` si no se indica.

La intención registrada MUST quedar en estado `PENDIENTE`, con su fecha de detección, y la respuesta MUST ser 201 con la intención creada.

Una conversación MUST poder tener varias intenciones de tipos distintos, pero MUST NOT tener dos intenciones `PENDIENTE` del mismo tipo. Una intención de un tipo que ya se resolvió en la conversación MUST poder registrarse de nuevo.

#### Scenario: Clasificación de una conversación
- **WHEN** se registra `CONSULTA_CATALOGO` con origen `LLM` en una conversación vigente
- **THEN** el sistema responde 201 y la conversación tiene esa intención `PENDIENTE`, con origen `LLM` y fecha de detección

#### Scenario: Varias intenciones distintas
- **WHEN** una conversación vigente tiene `SALUDO` pendiente y se registra `CONSULTA_CATALOGO`
- **THEN** la conversación tiene ambas intenciones pendientes

#### Scenario: Intención pendiente repetida
- **WHEN** una conversación vigente tiene `CONSULTA_CATALOGO` pendiente y se registra otra vez `CONSULTA_CATALOGO`
- **THEN** el sistema responde 409 y la conversación conserva una sola `CONSULTA_CATALOGO` pendiente

#### Scenario: Misma intención después de resuelta
- **WHEN** una conversación vigente tiene `CONSULTA_CATALOGO` resuelta y se registra `CONSULTA_CATALOGO`
- **THEN** el sistema responde 201 y la conversación tiene una `CONSULTA_CATALOGO` resuelta y otra pendiente

#### Scenario: Mensaje de origen ajeno a la conversación
- **WHEN** se registra una intención indicando un mensaje que no es un mensaje entrante de esa conversación
- **THEN** el sistema responde 400 y no registra la intención

### Requirement: Resolución de intenciones
El sistema MUST permitir marcar como resuelta una intención `PENDIENTE` de una conversación vigente con `PATCH /api/conversaciones/{id}/intenciones/{intencionId}/resolver`. Una intención se resuelve cuando se cumplió lo que el cliente quería. La intención MUST pasar a `RESUELTA` con su fecha de resolución, y la respuesta MUST ser 200 con la intención actualizada. Resolver una intención MUST NOT cerrar la conversación.

#### Scenario: Resolución de una intención pendiente
- **WHEN** se resuelve la intención `CONSULTA_CATALOGO` pendiente de una conversación vigente
- **THEN** el sistema responde 200, la intención queda `RESUELTA` con su fecha de resolución y la conversación sigue `ABIERTA`

#### Scenario: Intención ya resuelta
- **WHEN** se intenta resolver una intención que ya está `RESUELTA`
- **THEN** el sistema responde 409 y la intención conserva su fecha de resolución original

#### Scenario: Intención de otra conversación
- **WHEN** se intenta resolver una intención indicando una conversación a la que no pertenece
- **THEN** el sistema responde 404

### Requirement: Cierre explícito de conversaciones
El sistema MUST permitir cerrar una conversación vigente con `PATCH /api/conversaciones/{id}/cerrar`, indicando uno de dos motivos:
- `INTENCION_RESUELTA`: solo se admite si la conversación tiene al menos una intención y todas están `RESUELTA`. En otro caso el sistema MUST responder 409.
- `MANUAL`: se admite en cualquier caso. Las intenciones pendientes conservan su estado.

La conversación MUST pasar a `CERRADA` con el motivo y la fecha de cierre, y la respuesta MUST ser 200 con la conversación actualizada. El motivo `INACTIVIDAD` MUST NOT poder indicarse en este endpoint: el sistema MUST responder 400. El próximo mensaje del cliente MUST abrir una conversación nueva.

#### Scenario: Cierre tras resolver todo
- **WHEN** una conversación vigente tiene `SALUDO` y `CONSULTA_CATALOGO` resueltas y se cierra con motivo `INTENCION_RESUELTA`
- **THEN** el sistema responde 200 y la conversación queda `CERRADA` con ese motivo y su fecha de cierre

#### Scenario: Cierre por resolución con intenciones pendientes
- **WHEN** una conversación vigente tiene `CONSULTA_CATALOGO` pendiente y se intenta cerrar con motivo `INTENCION_RESUELTA`
- **THEN** el sistema responde 409 y la conversación sigue `ABIERTA`

#### Scenario: Cierre por resolución sin intenciones
- **WHEN** una conversación vigente sin intenciones se intenta cerrar con motivo `INTENCION_RESUELTA`
- **THEN** el sistema responde 409 y la conversación sigue `ABIERTA`

#### Scenario: Cierre manual con intenciones pendientes
- **WHEN** una conversación vigente tiene `OTRA` pendiente y se cierra con motivo `MANUAL`
- **THEN** el sistema responde 200, la conversación queda `CERRADA` con motivo `MANUAL` y la intención sigue `PENDIENTE`

#### Scenario: Mensaje después de un cierre explícito
- **WHEN** un cliente escribe 5 minutos después de que su conversación se cerró con motivo `MANUAL`
- **THEN** el mensaje pertenece a una conversación nueva `ABIERTA`

#### Scenario: Cierre por inactividad solicitado
- **WHEN** se intenta cerrar una conversación vigente con motivo `INACTIVIDAD`
- **THEN** el sistema responde 400 y la conversación sigue `ABIERTA`

### Requirement: Conversaciones no vigentes son inmutables
Sobre una conversación cerrada o vencida por inactividad, el sistema MUST responder 409 a los pedidos para registrar una intención, resolver una intención o cerrar la conversación, y MUST NOT modificar nada. Sobre una conversación inexistente, el sistema MUST responder 404.

#### Scenario: Clasificar una conversación cerrada
- **WHEN** se intenta registrar una intención en una conversación cerrada con motivo `MANUAL`
- **THEN** el sistema responde 409 y la conversación no gana intenciones

#### Scenario: Clasificar una conversación vencida
- **WHEN** se intenta registrar una intención en una conversación cuyo último mensaje fue hace 3 horas
- **THEN** el sistema responde 409

#### Scenario: Conversación inexistente
- **WHEN** se intenta registrar una intención en la conversación 999, que no existe
- **THEN** el sistema responde 404

### Requirement: Consulta de conversaciones
El sistema MUST ofrecer:
- `GET /api/conversaciones`, que lista conversaciones ordenadas de la de último mensaje más reciente a la más antigua. Admite filtrar por cliente, por estado (`ABIERTA` o `CERRADA`) y por conversaciones sin clasificar, es decir, sin ninguna intención registrada.
- `GET /api/conversaciones/{id}`, que devuelve una conversación con sus mensajes ordenados cronológicamente.

Cada conversación informa: identificador, cliente, estado, motivo y fecha de cierre (si corresponde), fecha de inicio, fecha del último mensaje y sus intenciones. Estado, motivo y fecha de cierre son los efectivos según la regla de vencimiento. Cada intención informa: identificador, tipo, estado, detalle, mensaje de origen, origen de la clasificación y fechas de detección y de resolución. Cada mensaje del detalle informa: identificador, dirección, tipo, texto y fecha de Telegram.

#### Scenario: Cola de conversaciones por clasificar
- **WHEN** existen una conversación abierta sin intenciones, otra abierta con `SALUDO` y otra vencida sin intenciones, y se consulta `GET /api/conversaciones?estado=ABIERTA&sinClasificar=true`
- **THEN** la respuesta contiene solo la conversación abierta sin intenciones

#### Scenario: Detalle de una conversación
- **WHEN** se consulta `GET /api/conversaciones/{id}` de una conversación con un mensaje entrante "quiero ver el catálogo" y su respuesta
- **THEN** la respuesta incluye ambos mensajes en orden cronológico con su dirección, tipo, texto y fecha, y las intenciones de la conversación

#### Scenario: Detalle de una conversación inexistente
- **WHEN** se consulta `GET /api/conversaciones/999`, que no existe
- **THEN** el sistema responde 404

### Requirement: API disponible sin el bot
Los endpoints de `/api/conversaciones` MUST estar disponibles aunque la integración con Telegram esté deshabilitada.

#### Scenario: Bot deshabilitado
- **WHEN** la aplicación arranca con el bot de Telegram deshabilitado y se consulta `GET /api/conversaciones`
- **THEN** el sistema responde 200

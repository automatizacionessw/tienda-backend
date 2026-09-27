# Design

## Context

- Stack: Spring Boot 4.1.1 con `spring-boot-starter-webmvc` y Jackson 3, sin Spring Security.
- Hay dos controllers de negocio, `ProductoController` (`/api/productos`) y `VentaController` (`/api/ventas`). Sus DTOs son clases Lombok con validaciones Jakarta.
- `TelegramWebhookController` (`/telegram/webhook`) solo existe con el bot habilitado en modo WEBHOOK.
- `GlobalExceptionHandler` devuelve `Map<String,Object>` con tres formas:
  - 404: `{timestamp, status, mensaje}`
  - 400 por validación: `{timestamp, status, errores{campo: mensaje}}`
  - 400 por stock y 409 por estado de venta: `{error}`
- `application.properties` no se versiona. El archivo de referencia es `application.properties.example`, con comentarios en español y flags `habilitado=false` por defecto.

## Goals / Non-Goals

**Goals:**
- Generar la especificación a partir del código (code-first), para que no se desincronice de los controllers.
- Tener un único punto donde se decide si la documentación está disponible.
- No tocar el comportamiento en tiempo de ejecución de ningún endpoint existente.

**Non-Goals:**
- Unificar las respuestas de error en un DTO común (se decidió dejarlo fuera de alcance).
- Proteger `/swagger-ui.html` con autenticación.
- Personalizar el aspecto de Swagger UI.

## Decisions

### D1. springdoc-openapi 3.x como generador
Se usa `org.springdoc:springdoc-openapi-starter-webmvc-ui` en la línea 3.x (la última publicada es 3.1.1). Es la línea que soporta Spring Boot 4 y Jackson 3. El starter trae el endpoint `/v3/api-docs` y Swagger UI empaquetado. La versión se fija en una propiedad `springdoc.version` del `pom.xml`, igual que `telegrambots.version`.

- *Alternativa: springdoc 2.x.* Está pensado para Boot 3 y Jackson 2. No es compatible con Boot 4.
- *Alternativa: Springfox.* Está abandonado y no funciona con Boot 3 ni 4.
- *Alternativa: un `openapi.yaml` escrito a mano y servido como estático.* Hay que mantenerlo en paralelo con el código y se desincroniza en cuanto cambia un endpoint.

### D2. Interruptor con las propiedades nativas de springdoc (opción B)
Se usan `springdoc.api-docs.enabled` y `springdoc.swagger-ui.enabled`. Con ambas en `false` desaparecen los dos endpoints (404). Se desactiva también la especificación, no solo la UI, porque no hay Spring Security: apagar solo la UI dejaría el mapa completo de la API expuesto en `/v3/api-docs`.

Las dos propiedades se documentan juntas en `application.properties.example`, en `false`, con un comentario que explica que deben tener el mismo valor. Si solo se apaga `api-docs`, la UI carga pero se queda vacía.

- *Alternativa: un flag propio del proyecto (`documentacion.api.habilitada`) que alimente ambas por placeholder.* Se descartó en la fase de exploración. Se prefiere la configuración estándar de springdoc, que cualquiera que conozca la librería reconoce.

### D3. Metadatos generales en un bean `OpenAPI`
Se crea una clase de configuración `common/openapi/OpenApiConfig` con un `@Bean OpenAPI`. El bean define:
- `Info`: título "API Tienda Backend", versión, y una descripción en español del propósito de la API. La descripción explica que las ventas las crea la IA en estado PENDIENTE y que el dueño las confirma o cancela.
- Los `Tag` "Productos" y "Ventas", con su descripción.

Se usa un bean y no `@OpenAPIDefinition`: deja todo en un único lugar, y el bean es ignorado cuando springdoc está desactivado.

### D4. Anotaciones en controllers y DTOs
- Controllers: `@Tag` en la clase; `@Operation(summary, description)` y `@Parameter(description)` para `{id}`; `@ApiResponse` por cada código que el endpoint puede devolver.
- DTOs: `@Schema(description, example)` en la clase y en cada campo. En los de respuesta se indican los valores posibles de `estado` (`PENDIENTE`, `COMPLETADA`, `CANCELADA`, según las constantes de `VentaService`) y el formato de `fecha`.
- Las restricciones de Jakarta Validation (`@NotBlank`, `@Min`, `@NotNull`) ya las traduce springdoc a `required`/`minimum`, así que no se repiten.

### D5. Esquemas de error solo para la documentación
Los handlers devuelven `Map`, y springdoc no puede inferir su forma. Se crean clases (records) en `common/openapi` que se usan **solo** como `schema` dentro de `@ApiResponse(content = @Content(schema = @Schema(implementation = ...)))`:
- `ErrorRecursoNoEncontrado` { timestamp, status, mensaje }
- `ErrorValidacion` { timestamp, status, errores: Map<String,String> }
- `ErrorSimple` { error }

`GlobalExceptionHandler` no se modifica, así que en tiempo de ejecución el JSON sigue siendo exactamente el mismo.

- *Alternativa: que los handlers devuelvan estos records.* Sería un cambio de contrato (orden y tipos de campos, `null` vs ausencia) y está fuera de alcance.

Mapa de errores por operación, según lo que lanzan los servicios:

| Operación | Errores |
|---|---|
| GET/PUT/DELETE `/api/productos/{id}` | 404 (PUT además 400 por validación) |
| POST `/api/productos` | 400 por validación |
| GET `/api/ventas/{id}` | 404 |
| POST `/api/ventas` | 400 por validación, 400 por stock (`ErrorSimple`), 404 por producto inexistente |
| PATCH `/api/ventas/{id}/confirmar` y `/cancelar` | 404, 409 |

La lista exacta se confirma leyendo `ProductoService` y `VentaService` al implementar.

### D6. Exclusión del webhook
Se agrega `@Hidden` (io.swagger.v3.oas.annotations) a nivel de clase en `TelegramWebhookController`. Así sigue excluido aunque el bot esté en modo WEBHOOK.

### D7. Tests del interruptor
Hay dos clases de test, porque cada valor de las propiedades necesita un contexto distinto. Ambas usan `@SpringBootTest` + `@AutoConfigureMockMvc`, con `telegram.bot.habilitado=false` explícito, como `TiendaBackendApplicationTests`:
- `DocumentacionApiHabilitadaTest`: flags en `true` → `GET /v3/api-docs` responde 200 y el JSON contiene `/api/productos` y `/api/ventas`.
- `DocumentacionApiDeshabilitadaTest`: flags en `false` → `GET /v3/api-docs` y `GET /swagger-ui.html` responden 404.

Las propiedades se fijan en el test para no depender del `application.properties` local. El usuario acordó que por ahora estos tests son suficientes.

## Risks / Trade-offs

- **[Riesgo] Springdoc considera habilitada la documentación si las propiedades no están definidas.** Un `application.properties` local copiado de una versión vieja del ejemplo tendría Swagger activo. → Mitigación: el ejemplo trae ambas en `false` con comentario, y el README avisa que en producción hay que declararlas explícitamente.
- **[Riesgo] Incompatibilidad puntual de springdoc 3.x con Boot 4.1.1 o Jackson 3.** → Mitigación: la tarea 1 compila y arranca con la dependencia antes de escribir anotaciones. Si falla, se prueba con otra versión 3.x y la decisión se registra aquí.
- **[Trade-off] Las anotaciones hacen más largos los controllers y DTOs.** Se acepta a cambio de que la documentación viva junto al código.
- **[Trade-off] Los esquemas de error son solo documentales y pueden quedar desalineados si alguien cambia `GlobalExceptionHandler`.** → Mitigación: un comentario en cada record y en el handler que remite al otro.

## Migration Plan

Es un cambio aditivo, sin migraciones de datos. Para desplegar, se agrega la dependencia y cada entorno declara explícitamente las propiedades (en `false` en producción). Para revertir, se ponen las dos propiedades en `false` sin redeploy de código, o se quita la dependencia.

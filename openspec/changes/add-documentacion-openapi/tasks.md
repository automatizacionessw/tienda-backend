# Tasks

## 1. Dependencia y configuración

- [x] 1.1 Agregar la propiedad `springdoc.version` (3.x, última compatible con Boot 4.1.1) y la dependencia `org.springdoc:springdoc-openapi-starter-webmvc-ui` al `pom.xml`. Verificar que `./mvnw clean compile` termina sin errores ni conflictos con Jackson 3 y que, al arrancar localmente con las propiedades en `true`, `http://localhost:8080/v3/api-docs` devuelve JSON. Si hay incompatibilidad, registrar la versión elegida y el motivo en design.md (D1)
- [x] 1.2 Agregar a `src/main/resources/application.properties.example` una sección "Documentación de la API (OpenAPI / Swagger UI)" con `springdoc.api-docs.enabled=false` y `springdoc.swagger-ui.enabled=false`, y un comentario en español: qué exponen (`/v3/api-docs`, `/swagger-ui.html`), que deben ir juntas y con el mismo valor, y que sin la propiedad springdoc las considera habilitadas. Verificar revisando el diff del archivo

## 2. Metadatos y esquemas de error

- [x] 2.1 Crear `common/openapi/OpenApiConfig` con el `@Bean OpenAPI` (título, versión, descripción en español, tags "Productos" y "Ventas" con descripción), según design D3. Verificar arrancando con la documentación habilitada que `info` y `tags` aparecen en `/v3/api-docs`
- [x] 2.2 Crear en `common/openapi` los records documentales `ErrorRecursoNoEncontrado`, `ErrorValidacion` y `ErrorSimple`, con `@Schema` y ejemplos en español, y comentarios cruzados con `GlobalExceptionHandler` (design D5) sin modificar el comportamiento del handler. Verificar que compila y que `git diff` de `GlobalExceptionHandler` solo agrega comentarios

## 3. Documentación de endpoints y DTOs

- [x] 3.1 Anotar `ProductoRequestDTO` y `ProductoResponseDTO` con `@Schema(description, example)` en clase y campos. Verificar en `/v3/api-docs` que `nombre`, `precio` y `stock` tienen descripción y ejemplo
- [x] 3.2 Anotar `VentaRequestDTO`, `DetalleRequestDTO`, `VentaResponseDTO` y `DetalleResponseDTO` con `@Schema`, indicando los valores posibles de `estado` (`PENDIENTE`, `COMPLETADA`, `CANCELADA`) y que `monto`, `precioUnitario` y `subtotal` los calcula el servidor. Verificar en `/v3/api-docs` que todos los campos tienen descripción
- [x] 3.3 Anotar `ProductoController` con `@Tag`, `@Operation`, `@Parameter` y `@ApiResponse` para cada código de éxito y error (tabla de design D5), confirmando los errores posibles contra `ProductoService`. Verificar en Swagger UI que las 5 operaciones muestran resumen en español y respuestas 404/400 con el esquema correcto
- [x] 3.4 Anotar `VentaController` igual que la tarea anterior, reflejando en las descripciones de las operaciones los comentarios existentes (creación por IA/MCP; confirmar y cancelar las hace el dueño). Confirmar los errores contra `VentaService`. Verificar en Swagger UI que `PATCH /{id}/confirmar` y `/{id}/cancelar` documentan 404 y 409, y `POST` documenta 400 (validación y stock) y 404
- [x] 3.5 Agregar `@Hidden` a `TelegramWebhookController`. Verificar arrancando con `telegram.bot.habilitado=true`, `modo=WEBHOOK` (token, url y secret de prueba) y la documentación habilitada que `/v3/api-docs` no contiene `/telegram/webhook`, y que `TelegramWebhookControllerTest` sigue pasando

## 4. Tests del interruptor

- [x] 4.1 Crear `DocumentacionApiHabilitadaTest` (`@SpringBootTest` + `@AutoConfigureMockMvc`, `telegram.bot.habilitado=false` y ambas propiedades de springdoc en `true`) que verifique que `GET /v3/api-docs` responde 200 y el JSON contiene `/api/productos` y `/api/ventas`. Verificar con `./mvnw test -Dtest=DocumentacionApiHabilitadaTest`
- [x] 4.2 Crear `DocumentacionApiDeshabilitadaTest` (mismo setup, propiedades en `false`) que verifique que `GET /v3/api-docs` y `GET /swagger-ui.html` responden 404. Verificar con `./mvnw test -Dtest=DocumentacionApiDeshabilitadaTest`

## 5. Documentación y verificación integral

- [x] 5.1 Actualizar el README: agregar springdoc-openapi a "Tecnologías y dependencias" y una subsección en "Configuración" que explique cómo activar la documentación, las URLs (`/swagger-ui.html`, `/v3/api-docs`) y la recomendación de dejarla en `false` y declarada explícitamente en producción. Verificar revisando el render del Markdown
- [x] 5.2 Ejecutar `./mvnw test` completo y verificar que pasan todos los tests, nuevos y existentes
- [ ] 5.3 Verificación manual con la documentación habilitada: abrir `/swagger-ui.html`, crear un producto, crear una venta con ese producto y confirmarla desde la UI. Comprobar que las respuestas (incluido un 404 con un id inexistente) tienen exactamente el mismo formato que antes del cambio

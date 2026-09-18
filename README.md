[README.md](https://github.com/user-attachments/files/32398803/README.md)
# Ventas MCP Server

Backend en Spring Boot para la automatización del flujo de ventas y validación de stock en tiempo real de una tienda de electrónica, integrado vía Telegram con NLP e IA local a través del **Model Context Protocol (MCP)**.

## Descripción del proyecto

Este backend es el núcleo de negocio y persistencia de datos del sistema. Expone:

- Una **API REST tradicional** para consultar productos y registrar ventas.
- Un **servidor MCP** (vía Spring AI MCP) que expone esas mismas capacidades como *tools* consumibles por una IA local, que actúa como cliente MCP desde un proceso Python separado (bot de Telegram + NLP + IA).

### Flujo general

```
Telegram → Bot (Python) → NLP (Python) → IA local (cliente MCP)
                                                │
                                      llama tools vía MCP
                                                │
                                                ▼
                              Backend Spring Boot (servidor MCP)
                              ├── Endpoints REST tradicionales
                              ├── Lógica de negocio y validaciones
                              └── JPA → PostgreSQL
```

Cuando se registra una venta, el backend notifica al dueño de la tienda mediante una notificación push para que confirme el pago y complete la venta.

## Tecnologías y dependencias

| Tecnología | Uso |
|---|---|
| **Spring Boot** | Framework base del backend |
| **Java 17 / 21** | Lenguaje |
| **Maven** | Gestión de dependencias y build |
| **Spring Web** | Endpoints REST (`/productos`, `/ventas`, etc.) |
| **Spring Data JPA** | ORM para el mapeo de entidades a la base de datos |
| **PostgreSQL Driver** | Conexión a la base de datos PostgreSQL |
| **Validation** | Validaciones de entrada (ej. cantidad ≤ stock disponible) |
| **Spring Boot DevTools** | Recarga en caliente durante el desarrollo |
| **Lombok** | Reducción de código repetitivo en las entidades (getters, setters, constructores) |
| **Spring Boot Actuator** | Endpoints de salud (`/actuator/health`) para monitoreo |
| **Spring AI MCP Server** | Exposición del backend como servidor MCP para la IA local (se agrega manualmente al `pom.xml`) |

## Modelo de datos

El backend gestiona 8 tablas principales:

| Tabla | Propósito |
|---|---|
| `cliente` | Persona que interactúa con el bot de Telegram |
| `usuario` | Dueño/vendedor que recibe notificaciones y confirma pagos |
| `conversacion` | Sesión de chat entre un cliente y el bot |
| `mensaje` | Cada mensaje individual dentro de una conversación (usado también como dataset para entrenar el NLP) |
| `producto` | Catálogo de productos con precio y stock |
| `venta` | Registro de una venta (pendiente, completada, cancelada) |
| `detalle_venta` | Productos y cantidades incluidos en cada venta |
| `notificacion` | Notificaciones push enviadas al dueño (nueva venta, pago pendiente, stock bajo) |

## Requisitos previos

- **JDK 17 o superior** instalado
- **PostgreSQL** corriendo (local o en Docker), con una base de datos creada
- **Maven** (o usar el wrapper `./mvnw` incluido en el proyecto)

## Configuración

Antes de levantar la aplicación, configura la conexión a la base de datos en `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/nombre_de_tu_bd
spring.datasource.username=tu_usuario
spring.datasource.password=tu_password
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

> Asegúrate de tener PostgreSQL activo antes de arrancar la app; si no encuentra la base de datos configurada, el arranque fallará.

## Cómo correrlo

### Desde la terminal

```bash
# Compilar el proyecto
./mvnw clean install

# Ejecutar la aplicación
./mvnw spring-boot:run
```

### Desde IntelliJ

1. Abre el proyecto.
2. Verifica que el plugin de **Lombok** esté instalado y habilitado (`Settings → Plugins`), y que **"Enable annotation processing"** esté activado (`Settings → Build, Execution, Deployment → Compiler → Annotation Processors`).
3. Ejecuta la clase principal `DemoApplication` (o el nombre que tenga tu clase `@SpringBootApplication`).

### Verificar que está corriendo

```
GET http://localhost:8080/actuator/health
```

Debería responder `{"status":"UP"}`.

## Estado del proyecto

🚧 En desarrollo — próximos pasos:

- [ ] Definir entidades JPA para las 8 tablas
- [ ] Implementar endpoints REST de Producto y Venta
- [ ] Configurar el servidor MCP con Spring AI MCP
- [ ] Implementar validación de stock antes de registrar una venta
- [ ] Implementar sistema de notificaciones push al dueño
- [ ] (Futuro) Validación de stock en tiempo real y alertas automáticas

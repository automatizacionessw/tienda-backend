package com.tiendabackend.tiendabackend.common.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Metadatos generales de la especificacion. Si springdoc esta deshabilitado
// (springdoc.api-docs.enabled=false) el bean existe pero nadie lo publica.
@Configuration
public class OpenApiConfig {

    public static final String TAG_PRODUCTOS = "Productos";
    public static final String TAG_VENTAS = "Ventas";
    public static final String TAG_CONVERSACIONES = "Conversaciones";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Tienda Backend")
                        .version("0.0.1")
                        .description("""
                                API REST de la tienda: catálogo de productos, registro de ventas y \
                                clasificación de las conversaciones del bot de Telegram.

                                Las ventas las crea la IA (vía MCP) en estado PENDIENTE, reservando \
                                el stock de cada producto. El dueño las pasa a COMPLETADA cuando \
                                confirma el pago, o a CANCELADA si el cliente no pagó, lo que \
                                libera el stock reservado.

                                Los mensajes de cada cliente se agrupan en conversaciones. Cada \
                                conversación se clasifica con una o varias intenciones, que se \
                                resuelven cuando se cumple lo que el cliente quería; así el LLM solo \
                                actúa con contexto y con las herramientas adecuadas."""))
                .tags(List.of(
                        new Tag().name(TAG_PRODUCTOS)
                                .description("Alta, consulta, modificación y baja lógica de productos del catálogo"),
                        new Tag().name(TAG_VENTAS)
                                .description("Creación de ventas pendientes y su confirmación o cancelación"),
                        new Tag().name(TAG_CONVERSACIONES)
                                .description("Conversaciones del bot: consulta, clasificación de intenciones, "
                                        + "resolución y cierre")));
    }
}

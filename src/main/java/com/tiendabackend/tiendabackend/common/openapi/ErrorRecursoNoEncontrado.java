package com.tiendabackend.tiendabackend.common.openapi;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

// Solo documenta la respuesta que arma GlobalExceptionHandler.manejarNoEncontrado
// (un Map); no se usa en tiempo de ejecucion. Mantener ambos alineados.
@Schema(name = "ErrorRecursoNoEncontrado", description = "Respuesta cuando el recurso solicitado no existe")
public record ErrorRecursoNoEncontrado(
        @Schema(description = "Momento en que se produjo el error", example = "2026-09-27T15:30:00.123")
        LocalDateTime timestamp,
        @Schema(description = "Código de estado HTTP", example = "404")
        Integer status,
        @Schema(description = "Detalle del recurso no encontrado", example = "Producto con id 99 no encontrado")
        String mensaje
) {
}

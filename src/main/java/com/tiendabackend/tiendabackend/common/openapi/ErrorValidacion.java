package com.tiendabackend.tiendabackend.common.openapi;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;

// Solo documenta la respuesta que arma GlobalExceptionHandler.manejarValidacion
// (un Map); no se usa en tiempo de ejecucion. Mantener ambos alineados.
@Schema(name = "ErrorValidacion", description = "Respuesta cuando el cuerpo de la petición no pasa las validaciones")
public record ErrorValidacion(
        @Schema(description = "Momento en que se produjo el error", example = "2026-09-27T15:30:00.123")
        LocalDateTime timestamp,
        @Schema(description = "Código de estado HTTP", example = "400")
        Integer status,
        @Schema(description = "Mensaje de error por cada campo inválido",
                example = "{\"nombre\": \"El nombre es obligatorio\"}")
        Map<String, String> errores
) {
}

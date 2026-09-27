package com.tiendabackend.tiendabackend.common.openapi;

import io.swagger.v3.oas.annotations.media.Schema;

// Solo documenta las respuestas que arma GlobalExceptionHandler para stock
// insuficiente y estado de venta invalido (un Map); no se usa en tiempo de
// ejecucion. Mantener ambos alineados.
@Schema(name = "ErrorSimple", description = "Respuesta de error de negocio con un único mensaje")
public record ErrorSimple(
        @Schema(description = "Descripción del error",
                example = "Solo se puede confirmar una venta en estado PENDIENTE (actual: CANCELADA)")
        String error
) {
}

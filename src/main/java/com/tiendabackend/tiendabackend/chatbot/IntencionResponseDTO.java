package com.tiendabackend.tiendabackend.chatbot;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Intención registrada en una conversación")
public class IntencionResponseDTO {

    @Schema(description = "Identificador de la intención", example = "7")
    private Long id;
    @Schema(description = "Intención del cliente", example = "CONSULTA_CATALOGO")
    private Intencion intencion;
    @Schema(description = "PENDIENTE hasta que se cumple lo que el cliente quería; luego RESUELTA",
            example = "PENDIENTE")
    private EstadoIntencion estado;
    @Schema(description = "Descripción libre de lo que pidió el cliente", example = "Pide el catálogo de auriculares")
    private String detalle;
    @Schema(description = "Mensaje entrante que originó la intención, si se indicó", example = "120")
    private Long mensajeId;
    @Schema(description = "Quién clasificó la conversación", example = "LLM")
    private OrigenClasificacion origen;
    @Schema(description = "Momento en que se registró la intención (UTC)", example = "2026-09-28T14:05:00Z")
    private Instant fechaDeteccion;
    @Schema(description = "Momento en que se resolvió (UTC); null si sigue pendiente", example = "2026-09-28T14:07:30Z")
    private Instant fechaResolucion;

    public static IntencionResponseDTO desdeEntidad(ConversacionIntencion intencion) {
        return new IntencionResponseDTO(
                intencion.getId(),
                intencion.getIntencion(),
                intencion.getEstado(),
                intencion.getDetalle(),
                intencion.getMensaje() != null ? intencion.getMensaje().getId() : null,
                intencion.getOrigen(),
                intencion.getFechaDeteccion(),
                intencion.getFechaResolucion()
        );
    }
}

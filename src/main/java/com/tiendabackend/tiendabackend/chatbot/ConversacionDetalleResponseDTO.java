package com.tiendabackend.tiendabackend.chatbot;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Conversación con sus mensajes en orden cronológico y sus intenciones. Estado, motivo y "
        + "fecha de cierre son los efectivos")
public class ConversacionDetalleResponseDTO {

    @Schema(description = "Identificador de la conversación", example = "15")
    private Long id;
    @Schema(description = "Identificador del cliente", example = "42")
    private Long clienteId;
    @Schema(description = "Estado efectivo de la conversación", example = "ABIERTA")
    private EstadoConversacion estado;
    @Schema(description = "Motivo del cierre; null si está abierta", example = "INTENCION_RESUELTA")
    private MotivoCierre motivoCierre;
    @Schema(description = "Fecha del primer mensaje (UTC)", example = "2026-09-28T14:04:00Z")
    private Instant fechaInicio;
    @Schema(description = "Fecha del último mensaje, entrante o saliente (UTC)", example = "2026-09-28T14:10:00Z")
    private Instant fechaUltimoMensaje;
    @Schema(description = "Fecha de cierre (UTC); null si está abierta", example = "2026-09-28T14:12:00Z")
    private Instant fechaCierre;
    @Schema(description = "Intenciones registradas, en orden de detección")
    private List<IntencionResponseDTO> intenciones;
    @Schema(description = "Mensajes de la conversación en orden cronológico")
    private List<MensajeResponseDTO> mensajes;

    public static ConversacionDetalleResponseDTO desdeEntidad(Conversacion conversacion,
                                                              Conversacion.EstadoEfectivo efectivo,
                                                              List<Mensaje> mensajes) {
        return new ConversacionDetalleResponseDTO(
                conversacion.getId(),
                conversacion.getClienteId(),
                efectivo.estado(),
                efectivo.motivoCierre(),
                conversacion.getFechaInicio(),
                conversacion.getFechaUltimoMensaje(),
                efectivo.fechaCierre(),
                conversacion.getIntenciones().stream().map(IntencionResponseDTO::desdeEntidad).toList(),
                mensajes.stream().map(MensajeResponseDTO::desdeEntidad).toList()
        );
    }
}

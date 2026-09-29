package com.tiendabackend.tiendabackend.chatbot;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Mensaje de una conversación, enviado por el cliente o por el bot")
public class MensajeResponseDTO {

    @Schema(description = "Identificador del mensaje", example = "120")
    private Long id;
    @Schema(description = "ENTRANTE si lo envió el cliente, SALIENTE si lo envió el bot", example = "ENTRANTE")
    private Direccion direccion;
    @Schema(description = "Tipo de contenido. En VOZ y NO_SOPORTADO el texto es el caption, si lo hay",
            example = "TEXTO")
    private TipoMensaje tipo;
    @Schema(description = "Texto del mensaje o caption del adjunto", example = "quiero ver el catálogo")
    private String texto;
    @Schema(description = "Fecha del mensaje según Telegram (UTC)", example = "2026-09-28T14:04:10Z")
    private Instant fechaTelegram;

    public static MensajeResponseDTO desdeEntidad(Mensaje mensaje) {
        return new MensajeResponseDTO(
                mensaje.getId(),
                mensaje.getDireccion(),
                mensaje.getTipo(),
                mensaje.getTexto(),
                mensaje.getFechaTelegram()
        );
    }
}

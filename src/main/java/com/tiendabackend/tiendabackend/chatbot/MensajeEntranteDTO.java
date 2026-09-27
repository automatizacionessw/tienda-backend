package com.tiendabackend.tiendabackend.chatbot;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Lo que ve un GeneradorRespuesta del mensaje recibido, sin depender de la
// entidad ni de las clases de Telegram.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MensajeEntranteDTO {

    private Long mensajeId;
    private Long clienteId;
    private TipoMensaje tipo;
    private String texto;
    private Instant fechaTelegram;

    public static MensajeEntranteDTO desdeEntidad(Mensaje mensaje) {
        return new MensajeEntranteDTO(
                mensaje.getId(),
                mensaje.getClienteId(),
                mensaje.getTipo(),
                mensaje.getTexto(),
                mensaje.getFechaTelegram()
        );
    }
}

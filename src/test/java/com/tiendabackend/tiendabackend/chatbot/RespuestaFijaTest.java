package com.tiendabackend.tiendabackend.chatbot;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RespuestaFijaTest {

    @Test
    void respondeMensajeRecibidoParaCualquierTipo() {
        RespuestaFija respuesta = new RespuestaFija();

        for (TipoMensaje tipo : TipoMensaje.values()) {
            assertThat(respuesta.generar(new MensajeEntranteDTO(1L, 2L, tipo, "hola", Instant.now())))
                    .isEqualTo("mensaje recibido");
        }
    }
}

package com.tiendabackend.tiendabackend.chatbot;

import org.springframework.stereotype.Component;

@Component
public class RespuestaFija implements GeneradorRespuesta {

    static final String TEXTO = "mensaje recibido";

    @Override
    public String generar(MensajeEntranteDTO mensaje) {
        return TEXTO;
    }
}

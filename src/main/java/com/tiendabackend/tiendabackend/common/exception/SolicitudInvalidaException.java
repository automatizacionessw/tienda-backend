package com.tiendabackend.tiendabackend.common.exception;

// Regla de negocio incumplida por los datos de la peticion (400 con {error}).
// El mensaje debe decir que valores son validos: lo lee tambien un LLM.
public class SolicitudInvalidaException extends RuntimeException {
    public SolicitudInvalidaException(String message) {
        super(message);
    }
}

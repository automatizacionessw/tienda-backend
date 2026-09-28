package com.tiendabackend.tiendabackend.common.exception;

// La operacion no es posible en el estado actual de la conversacion o de la
// intencion (409 con {error}): conversacion no vigente, intencion pendiente
// duplicada o ya resuelta, cierre por resolucion con intenciones pendientes.
public class EstadoConversacionInvalidoException extends RuntimeException {
    public EstadoConversacionInvalidoException(String message) {
        super(message);
    }
}

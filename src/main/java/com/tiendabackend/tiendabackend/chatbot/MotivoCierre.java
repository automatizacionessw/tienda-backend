package com.tiendabackend.tiendabackend.chatbot;

// INACTIVIDAD lo registra solo el sistema; por la API se cierra con
// INTENCION_RESUELTA (todo resuelto) o MANUAL (sin condiciones).
public enum MotivoCierre {
    INACTIVIDAD, INTENCION_RESUELTA, MANUAL
}

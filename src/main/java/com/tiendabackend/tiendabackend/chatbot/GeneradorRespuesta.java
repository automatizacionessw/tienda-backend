package com.tiendabackend.tiendabackend.chatbot;

// Punto de extension para la respuesta del bot. Hoy es fija; el futuro
// NLP/IA se conecta reemplazando este bean, sin tocar ingreso ni persistencia.
public interface GeneradorRespuesta {

    String generar(MensajeEntranteDTO mensaje);
}

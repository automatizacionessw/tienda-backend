package com.tiendabackend.tiendabackend.chatbot;

// Catalogo de prueba de concepto. OTRA marca lo que no encaja en el catalogo:
// exige un detalle y es la senal de que el LLM no debe responder.
public enum Intencion {
    SALUDO, CONSULTA_CATALOGO, INICIAR_PEDIDO, CONSULTAR_ESTADO_PEDIDO, OTRA
}

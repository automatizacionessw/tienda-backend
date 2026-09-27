package com.tiendabackend.tiendabackend.chatbot.telegram;

// Expresiones para @ConditionalOnExpression: cada adaptador de ingreso solo
// existe si el bot esta habilitado Y el modo configurado es el suyo.
final class CondicionesTelegram {

    static final String MODO_POLLING = "${telegram.bot.habilitado:false} "
            + "and '${telegram.bot.modo:POLLING}'.equalsIgnoreCase('POLLING')";

    static final String MODO_WEBHOOK = "${telegram.bot.habilitado:false} "
            + "and '${telegram.bot.modo:POLLING}'.equalsIgnoreCase('WEBHOOK')";

    private CondicionesTelegram() {
    }
}

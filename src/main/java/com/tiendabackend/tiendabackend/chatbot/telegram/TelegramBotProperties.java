package com.tiendabackend.tiendabackend.chatbot.telegram;

import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

// telegram.bot.habilitado es false por defecto: un token admite un solo
// consumidor, y el opt-in explicito evita que una maquina de desarrollo le
// "robe" updates al bot productivo o que los tests intenten conectarse.
@ConfigurationProperties(prefix = "telegram.bot")
public record TelegramBotProperties(
        boolean habilitado,
        String token,
        Modo modo,
        Webhook webhook
) {

    // Restriccion de Telegram para secret_token: 1-256 caracteres A-Z a-z 0-9 _ -
    private static final Pattern SECRET_VALIDO = Pattern.compile("[A-Za-z0-9_-]{1,256}");

    public enum Modo {
        POLLING, WEBHOOK
    }

    public record Webhook(String url, String secret) {
    }

    public TelegramBotProperties {
        if (modo == null) {
            modo = Modo.POLLING;
        }
        if (webhook == null) {
            webhook = new Webhook(null, null);
        }
    }

    // Se invoca al crear los beans de Telegram (solo con habilitado=true), de
    // modo que una configuracion incompleta hace fallar el arranque nombrando
    // la propiedad que falta.
    public void validar() {
        if (esVacio(token)) {
            throw new IllegalStateException(
                    "Falta la propiedad telegram.bot.token (token del bot, via TELEGRAM_BOT_TOKEN)");
        }
        if (modo == Modo.WEBHOOK) {
            if (esVacio(webhook.url())) {
                throw new IllegalStateException(
                        "Falta la propiedad telegram.bot.webhook.url (obligatoria en modo WEBHOOK)");
            }
            if (!webhook.url().startsWith("https://")) {
                throw new IllegalStateException(
                        "La propiedad telegram.bot.webhook.url debe ser una URL https:// publica");
            }
            if (esVacio(webhook.secret())) {
                throw new IllegalStateException(
                        "Falta la propiedad telegram.bot.webhook.secret (obligatoria en modo WEBHOOK)");
            }
            if (!SECRET_VALIDO.matcher(webhook.secret()).matches()) {
                throw new IllegalStateException(
                        "La propiedad telegram.bot.webhook.secret solo admite 1-256 caracteres A-Z, a-z, 0-9, _ y -");
            }
        }
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }

    // El token no debe aparecer en logs ni en toString().
    @Override
    public String toString() {
        return "TelegramBotProperties[habilitado=" + habilitado + ", modo=" + modo
                + ", webhook.url=" + webhook.url() + "]";
    }
}

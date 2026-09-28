package com.tiendabackend.tiendabackend.chatbot.telegram;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

// Registra el webhook en Telegram al arrancar. No se elimina al apagar: un
// reinicio no pierde updates (Telegram los retiene y reintenta) y setWebhook
// es idempotente.
@Component
@ConditionalOnExpression(CondicionesTelegram.MODO_WEBHOOK)
public class TelegramWebhookRegistrador {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookRegistrador.class);

    private final TelegramBotProperties properties;
    private final TelegramClient telegramClient;

    public TelegramWebhookRegistrador(TelegramBotProperties properties, TelegramClient telegramClient) {
        this.properties = properties;
        this.telegramClient = telegramClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registrar() throws TelegramApiException {
        telegramClient.execute(SetWebhook.builder()
                .url(properties.webhook().url())
                .secretToken(properties.webhook().secret())
                .allowedUpdates(List.of("message"))
                .build());
        log.info("Bot de Telegram iniciado en modo WEBHOOK ({})", properties.webhook().url());
    }
}

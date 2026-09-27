package com.tiendabackend.tiendabackend.chatbot.telegram;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
@ConditionalOnProperty(prefix = "telegram.bot", name = "habilitado", havingValue = "true")
public class TelegramConfig {

    // Cliente HTTP unico de la Bot API (sendMessage, getFile, descargas,
    // setWebhook/deleteWebhook). No depende de Spring ni de Jackson 3.
    @Bean
    public TelegramClient telegramClient(TelegramBotProperties properties) {
        properties.validar();
        return new OkHttpTelegramClient(properties.token());
    }
}

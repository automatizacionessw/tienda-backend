package com.tiendabackend.tiendabackend.chatbot.telegram;

import com.tiendabackend.tiendabackend.chatbot.ChatbotService;
import jakarta.annotation.PreDestroy;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.DefaultGetUpdatesGenerator;
import org.telegram.telegrambots.longpolling.util.DefaultLongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.TelegramUrl;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

// Ingreso por long polling. Se inicia recien con la aplicacion lista, para no
// recibir updates antes de que el resto del contexto este disponible.
@Component
@ConditionalOnExpression(CondicionesTelegram.MODO_POLLING)
public class TelegramPollingIngreso {

    private static final Logger log = LoggerFactory.getLogger(TelegramPollingIngreso.class);

    private final TelegramBotProperties properties;
    private final TelegramClient telegramClient;
    private final ChatbotService chatbotService;

    private TelegramBotsLongPollingApplication aplicacion;

    public TelegramPollingIngreso(TelegramBotProperties properties,
                                  TelegramClient telegramClient,
                                  ChatbotService chatbotService) {
        this.properties = properties;
        this.telegramClient = telegramClient;
        this.chatbotService = chatbotService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void iniciar() throws TelegramApiException {
        // Con un webhook registrado, Telegram rechaza getUpdates: se elimina
        // primero (sin descartar updates pendientes).
        telegramClient.execute(new DeleteWebhook(false));

        aplicacion = new TelegramBotsLongPollingApplication();
        aplicacion.registerBot(
                properties.token(),
                () -> TelegramUrl.DEFAULT_URL,
                new DefaultGetUpdatesGenerator(List.of("message")),
                new Consumidor());
        log.info("Bot de Telegram iniciado en modo POLLING");
    }

    @PreDestroy
    public void detener() {
        if (aplicacion == null) {
            return;
        }
        try {
            aplicacion.close();
        } catch (Exception e) {
            log.warn("Error al detener el polling de Telegram: {}", e.toString());
        }
    }

    // Procesa un update por vez en un hilo propio. Un error en un update se
    // registra y no impide procesar los siguientes.
    private class Consumidor extends DefaultLongPollingUpdateConsumer {

        @Override
        public void consume(Update update) {
            try {
                chatbotService.procesar(update);
            } catch (Exception e) {
                log.error("Error procesando el update {} de Telegram", update.getUpdateId(), e);
            }
        }
    }
}

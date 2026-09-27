package com.tiendabackend.tiendabackend.chatbot.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.tiendabackend.tiendabackend.chatbot.ChatbotService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.generics.TelegramClient;

// El runner no publica ApplicationReadyEvent: ningun adaptador llega a
// llamar a Telegram, solo se verifica que beans existen y la validacion.
class TelegramConfigTest {

    private static final String TOKEN = "telegram.bot.token=123456:ABC-token-de-prueba";

    @Configuration
    @EnableConfigurationProperties(TelegramBotProperties.class)
    static class Base {
        @Bean
        ChatbotService chatbotService() {
            return mock(ChatbotService.class);
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Base.class, TelegramConfig.class, TelegramPollingIngreso.class,
                    TelegramWebhookController.class, TelegramWebhookRegistrador.class);

    @Test
    void deshabilitadoPorDefectoArrancaSinTokenNiBeansDeTelegram() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(TelegramClient.class);
            assertThat(context).doesNotHaveBean(TelegramPollingIngreso.class);
            assertThat(context).doesNotHaveBean(TelegramWebhookController.class);
            assertThat(context).doesNotHaveBean(TelegramWebhookRegistrador.class);
        });
    }

    @Test
    void deshabilitadoExplicitoIgnoraElModo() {
        runner.withPropertyValues("telegram.bot.habilitado=false", "telegram.bot.modo=WEBHOOK")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(TelegramClient.class);
                    assertThat(context).doesNotHaveBean(TelegramWebhookController.class);
                });
    }

    @Test
    void habilitadoSinTokenFallaNombrandoLaPropiedad() {
        runner.withPropertyValues("telegram.bot.habilitado=true")
                .run(context -> assertThat(context).getFailure()
                        .rootCause().hasMessageContaining("telegram.bot.token"));
    }

    @Test
    void webhookSinUrlFallaNombrandoLaPropiedad() {
        runner.withPropertyValues("telegram.bot.habilitado=true", TOKEN, "telegram.bot.modo=WEBHOOK",
                        "telegram.bot.webhook.secret=secreto_123")
                .run(context -> assertThat(context).getFailure()
                        .rootCause().hasMessageContaining("telegram.bot.webhook.url"));
    }

    @Test
    void webhookSinSecretFallaNombrandoLaPropiedad() {
        runner.withPropertyValues("telegram.bot.habilitado=true", TOKEN, "telegram.bot.modo=WEBHOOK",
                        "telegram.bot.webhook.url=https://bot.ejemplo.com/telegram/webhook")
                .run(context -> assertThat(context).getFailure()
                        .rootCause().hasMessageContaining("telegram.bot.webhook.secret"));
    }

    @Test
    void webhookConSecretInvalidoFalla() {
        runner.withPropertyValues("telegram.bot.habilitado=true", TOKEN, "telegram.bot.modo=WEBHOOK",
                        "telegram.bot.webhook.url=https://bot.ejemplo.com/telegram/webhook",
                        "telegram.bot.webhook.secret=no valido!")
                .run(context -> assertThat(context).getFailure()
                        .rootCause().hasMessageContaining("telegram.bot.webhook.secret"));
    }

    @Test
    void modoPollingCreaSoloElIngresoPorPolling() {
        runner.withPropertyValues("telegram.bot.habilitado=true", TOKEN)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TelegramClient.class);
                    assertThat(context).hasSingleBean(TelegramPollingIngreso.class);
                    assertThat(context).doesNotHaveBean(TelegramWebhookController.class);
                    assertThat(context).doesNotHaveBean(TelegramWebhookRegistrador.class);
                });
    }

    @Test
    void modoWebhookCreaSoloElIngresoPorWebhook() {
        runner.withPropertyValues("telegram.bot.habilitado=true", TOKEN, "telegram.bot.modo=webhook",
                        "telegram.bot.webhook.url=https://bot.ejemplo.com/telegram/webhook",
                        "telegram.bot.webhook.secret=secreto_123")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TelegramClient.class);
                    assertThat(context).hasSingleBean(TelegramWebhookController.class);
                    assertThat(context).hasSingleBean(TelegramWebhookRegistrador.class);
                    assertThat(context).doesNotHaveBean(TelegramPollingIngreso.class);
                });
    }

    @Test
    void toStringNoExponeElToken() {
        TelegramBotProperties properties = new TelegramBotProperties(true, "123456:SECRETO", null, null);

        assertThat(properties.toString()).doesNotContain("SECRETO");
        assertThat(properties.modo()).isEqualTo(TelegramBotProperties.Modo.POLLING);
    }
}

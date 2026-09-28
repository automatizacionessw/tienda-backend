package com.tiendabackend.tiendabackend.chatbot.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tiendabackend.tiendabackend.chatbot.ChatbotService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.telegram.telegrambots.meta.api.objects.Update;

@WebMvcTest(controllers = TelegramWebhookController.class, properties = {
        "telegram.bot.habilitado=true",
        "telegram.bot.modo=WEBHOOK",
        "telegram.bot.token=123456:ABC",
        "telegram.bot.webhook.url=https://bot.ejemplo.com/telegram/webhook",
        "telegram.bot.webhook.secret=secreto_123"})
class TelegramWebhookControllerTest {

    static final String UPDATE_TEXTO = """
            {"update_id": 1001,
             "message": {"message_id": 10, "date": 1790000000, "text": "hola",
                         "from": {"id": 5551234567, "is_bot": false, "first_name": "Ana"},
                         "chat": {"id": 5551234567, "first_name": "Ana", "type": "private"}}}
            """;

    // @WebMvcTest no registra las @ConfigurationProperties escaneadas.
    @TestConfiguration
    @EnableConfigurationProperties(TelegramBotProperties.class)
    static class Propiedades {
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ChatbotService chatbotService;

    @Test
    void sinHeaderDeSecretResponde401SinProcesar() throws Exception {
        mockMvc.perform(post("/telegram/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_TEXTO))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(chatbotService);
    }

    @Test
    void secretIncorrectoResponde401SinProcesar() throws Exception {
        mockMvc.perform(post("/telegram/webhook")
                        .header(TelegramWebhookController.HEADER_SECRET, "otro_secreto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_TEXTO))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(chatbotService);
    }

    @Test
    void secretCorrectoProcesaElUpdateYResponde200() throws Exception {
        mockMvc.perform(post("/telegram/webhook")
                        .header(TelegramWebhookController.HEADER_SECRET, "secreto_123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_TEXTO))
                .andExpect(status().isOk());

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(chatbotService).procesar(update.capture());
        assertThat(update.getValue().getUpdateId()).isEqualTo(1001);
        assertThat(update.getValue().getMessage().getText()).isEqualTo("hola");
    }

    @Test
    void errorAlProcesarIgualResponde200() throws Exception {
        doThrow(new RuntimeException("fallo interno")).when(chatbotService).procesar(any());

        mockMvc.perform(post("/telegram/webhook")
                        .header(TelegramWebhookController.HEADER_SECRET, "secreto_123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_TEXTO))
                .andExpect(status().isOk());
    }

    @Test
    void cuerpoInvalidoTrasAutenticarResponde200SinProcesar() throws Exception {
        mockMvc.perform(post("/telegram/webhook")
                        .header(TelegramWebhookController.HEADER_SECRET, "secreto_123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{no es json"))
                .andExpect(status().isOk());

        verifyNoInteractions(chatbotService);
    }
}

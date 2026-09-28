package com.tiendabackend.tiendabackend.chatbot.telegram;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tiendabackend.tiendabackend.chatbot.ChatbotService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// En modo POLLING el endpoint de webhook no se registra.
@WebMvcTest(controllers = TelegramWebhookController.class, properties = {
        "telegram.bot.habilitado=true",
        "telegram.bot.modo=POLLING",
        "telegram.bot.token=123456:ABC"})
class TelegramWebhookModoPollingTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ChatbotService chatbotService;

    @Test
    void elEndpointDeWebhookResponde404() throws Exception {
        mockMvc.perform(post("/telegram/webhook")
                        .header(TelegramWebhookController.HEADER_SECRET, "secreto_123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TelegramWebhookControllerTest.UPDATE_TEXTO))
                .andExpect(status().isNotFound());
    }
}

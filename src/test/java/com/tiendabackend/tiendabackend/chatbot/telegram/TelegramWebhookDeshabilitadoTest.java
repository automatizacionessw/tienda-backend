package com.tiendabackend.tiendabackend.chatbot.telegram;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// Con el bot deshabilitado no hay endpoint ni se requiere token. Se fija
// explicitamente para no depender del application.properties local.
@WebMvcTest(controllers = TelegramWebhookController.class, properties = {
        "telegram.bot.habilitado=false",
        "telegram.bot.modo=WEBHOOK"})
class TelegramWebhookDeshabilitadoTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void elEndpointDeWebhookResponde404() throws Exception {
        mockMvc.perform(post("/telegram/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TelegramWebhookControllerTest.UPDATE_TEXTO))
                .andExpect(status().isNotFound());
    }
}

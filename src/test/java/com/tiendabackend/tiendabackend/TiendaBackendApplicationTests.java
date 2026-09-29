package com.tiendabackend.tiendabackend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

// El bot se deshabilita explicitamente: el application.properties local puede
// tenerlo habilitado, y el test no debe conectarse a Telegram.
@SpringBootTest(properties = "telegram.bot.habilitado=false")
@AutoConfigureMockMvc
class TiendaBackendApplicationTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    // La API de conversaciones no depende de telegram.bot.habilitado.
    @Test
    void laApiDeConversacionesFuncionaConElBotDeshabilitado() throws Exception {
        mockMvc.perform(get("/api/conversaciones"))
                .andExpect(status().isOk());
    }
}

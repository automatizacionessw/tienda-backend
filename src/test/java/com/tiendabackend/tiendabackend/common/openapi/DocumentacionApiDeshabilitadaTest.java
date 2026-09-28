package com.tiendabackend.tiendabackend.common.openapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

// Con las dos propiedades en false no se publica ni la especificacion ni la
// interfaz. Se fijan en el test para no depender del application.properties local.
@SpringBootTest(properties = {
        "telegram.bot.habilitado=false",
        "springdoc.api-docs.enabled=false",
        "springdoc.swagger-ui.enabled=false"})
@AutoConfigureMockMvc
class DocumentacionApiDeshabilitadaTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void laEspecificacionOpenApiResponde404() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }

    @Test
    void laInterfazSwaggerUiResponde404() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isNotFound());
    }
}

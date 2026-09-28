package com.tiendabackend.tiendabackend.common.openapi;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

// Las propiedades se fijan en el test para no depender del
// application.properties local; el bot se deshabilita para no conectarse.
@SpringBootTest(properties = {
        "telegram.bot.habilitado=false",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"})
@AutoConfigureMockMvc
class DocumentacionApiHabilitadaTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void laEspecificacionOpenApiSePublicaConLasRutasDeNegocio() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value(startsWith("3.")))
                .andExpect(jsonPath("$.paths['/api/productos']").exists())
                .andExpect(jsonPath("$.paths['/api/ventas']").exists());
    }

    @Test
    void laEspecificacionIncluyeLasRutasDeConversaciones() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/conversaciones'].get").exists())
                .andExpect(jsonPath("$.paths['/api/conversaciones/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/conversaciones/{id}/intenciones'].post").exists())
                .andExpect(jsonPath("$.paths['/api/conversaciones/{id}/intenciones/{intencionId}/resolver'].patch")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/conversaciones/{id}/cerrar'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/conversaciones/{id}/intenciones'].post.responses['409']")
                        .exists());
    }

    @Test
    void elRegistroDeIntencionesEnumeraElCatalogo() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.IntencionRequestDTO.properties.intencion.enum",
                        contains("SALUDO", "CONSULTA_CATALOGO", "INICIAR_PEDIDO", "CONSULTAR_ESTADO_PEDIDO", "OTRA")))
                .andExpect(jsonPath("$.components.schemas.IntencionRequestDTO.properties.intencion.description",
                        containsString("OTRA")));
    }

    // Contraparte de DocumentacionApiDeshabilitadaTest: habilitada, la ruta
    // de la interfaz existe (redirige a la pagina de Swagger UI).
    @Test
    void laInterfazSwaggerUiEstaDisponible() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }
}

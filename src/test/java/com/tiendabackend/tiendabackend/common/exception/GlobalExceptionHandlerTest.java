package com.tiendabackend.tiendabackend.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    @RestController
    static class ControladorDePrueba {

        @GetMapping("/solicitud-invalida")
        void solicitudInvalida() {
            throw new SolicitudInvalidaException("Valor inválido para intencion: 'RECLAMO'");
        }

        @GetMapping("/conversacion-cerrada")
        void conversacionCerrada() {
            throw new EstadoConversacionInvalidoException("La conversación 15 está cerrada (MANUAL)");
        }

        @GetMapping("/venta-invalida")
        void ventaInvalida() {
            throw new EstadoVentaInvalidoException("Solo se puede confirmar una venta en estado PENDIENTE");
        }

        @GetMapping("/no-encontrado")
        void noEncontrado() {
            throw new RecursoNoEncontradoException("Conversación", 999L);
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ControladorDePrueba())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void solicitudInvalidaResponde400ConError() throws Exception {
        mockMvc.perform(get("/solicitud-invalida"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Valor inválido para intencion: 'RECLAMO'"));
    }

    @Test
    void estadoDeConversacionInvalidoResponde409ConError() throws Exception {
        mockMvc.perform(get("/conversacion-cerrada"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("La conversación 15 está cerrada (MANUAL)"));
    }

    @Test
    void losHandlersExistentesNoCambian() throws Exception {
        mockMvc.perform(get("/venta-invalida"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
        mockMvc.perform(get("/no-encontrado"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensaje").value("Conversación con id 999 no encontrado"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}

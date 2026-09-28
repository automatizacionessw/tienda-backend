package com.tiendabackend.tiendabackend.chatbot;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

// Controlador + servicio reales; solo la persistencia esta simulada, para
// verificar de punta a punta codigos de estado y cuerpos de error.
@WebMvcTest(controllers = ConversacionController.class, properties = "telegram.bot.habilitado=false")
@Import(ConversacionService.class)
class ConversacionControllerTest {

    private static final Instant AHORA = Instant.parse("2026-09-28T14:00:00Z");

    @TestConfiguration
    static class Config {
        @Bean
        Clock clock() {
            return Clock.fixed(AHORA, ZoneOffset.UTC);
        }

        @Bean
        ConversacionProperties conversacionProperties() {
            return new ConversacionProperties(Duration.ofHours(2));
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ConversacionRepository conversacionRepository;
    @MockitoBean
    ConversacionIntencionRepository intencionRepository;
    @MockitoBean
    MensajeRepository mensajeRepository;
    @MockitoBean
    PlatformTransactionManager transactionManager;

    private Conversacion conversacion;

    @BeforeEach
    void setUp() {
        conversacion = Conversacion.abrir(42L, AHORA.minusSeconds(600));
        conversacion.setId(15L);
        conversacion.setFechaUltimoMensaje(AHORA.minusSeconds(60));
        when(conversacionRepository.findByIdParaActualizar(15L)).thenReturn(Optional.of(conversacion));
        when(conversacionRepository.findById(15L)).thenReturn(Optional.of(conversacion));
        when(conversacionRepository.save(any(Conversacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(intencionRepository.save(any(ConversacionIntencion.class))).thenAnswer(inv -> {
            ConversacionIntencion i = inv.getArgument(0);
            if (i.getId() == null) {
                i.setId(7L);
            }
            return i;
        });
    }

    private ConversacionIntencion agregarIntencion(Intencion tipo, EstadoIntencion estado) {
        ConversacionIntencion i = new ConversacionIntencion();
        i.setId(7L);
        i.setConversacion(conversacion);
        i.setIntencion(tipo);
        i.setEstado(estado);
        i.setOrigen(OrigenClasificacion.MANUAL);
        i.setFechaDeteccion(AHORA.minusSeconds(30));
        conversacion.getIntenciones().add(i);
        return i;
    }


    @Test
    void listarDevuelve200() throws Exception {
        when(conversacionRepository.buscar(anyBoolean(), anyLong(), anyBoolean(), anyBoolean(), anyBoolean(),
                any(), any())).thenReturn(List.of(conversacion));

        mockMvc.perform(get("/api/conversaciones").param("estado", "ABIERTA").param("sinClasificar", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(15))
                .andExpect(jsonPath("$[0].estado").value("ABIERTA"))
                .andExpect(jsonPath("$[0].intenciones").isArray());
    }

    @Test
    void listarConEstadoInvalidoDevuelve400() throws Exception {
        mockMvc.perform(get("/api/conversaciones").param("estado", "INVALIDO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("ABIERTA, CERRADA")));
    }

    @Test
    void detalleDevuelve200ConMensajes() throws Exception {
        Mensaje m = new Mensaje();
        m.setId(120L);
        m.setConversacion(conversacion);
        m.setDireccion(Direccion.ENTRANTE);
        m.setTipo(TipoMensaje.TEXTO);
        m.setTexto("quiero ver el catálogo");
        m.setFechaTelegram(AHORA.minusSeconds(60));
        when(mensajeRepository.findByConversacionIdOrderByFechaTelegramAscIdAsc(15L)).thenReturn(List.of(m));

        mockMvc.perform(get("/api/conversaciones/15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensajes[0].texto").value("quiero ver el catálogo"))
                .andExpect(jsonPath("$.mensajes[0].direccion").value("ENTRANTE"));
    }

    @Test
    void detalleDeInexistenteDevuelve404() throws Exception {
        when(conversacionRepository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/conversaciones/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void registrarIntencionDevuelve201() throws Exception {
        mockMvc.perform(post("/api/conversaciones/15/intenciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intencion\": \"CONSULTA_CATALOGO\", \"origen\": \"LLM\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.intencion").value("CONSULTA_CATALOGO"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.origen").value("LLM"));
    }

    @Test
    void intencionInexistenteDevuelve400ConLosValoresValidos() throws Exception {
        mockMvc.perform(post("/api/conversaciones/15/intenciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intencion\": \"RECLAMO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString(
                        "SALUDO, CONSULTA_CATALOGO, INICIAR_PEDIDO, CONSULTAR_ESTADO_PEDIDO, OTRA")));
    }

    @Test
    void intencionAusenteDevuelve400PorValidacion() throws Exception {
        mockMvc.perform(post("/api/conversaciones/15/intenciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"detalle\": \"algo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.intencion").exists());
    }

    @Test
    void otraSinDetalleDevuelve400() throws Exception {
        mockMvc.perform(post("/api/conversaciones/15/intenciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intencion\": \"OTRA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("OTRA")));
    }

    @Test
    void intencionEnConversacionInexistenteDevuelve404() throws Exception {
        when(conversacionRepository.findByIdParaActualizar(999L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/conversaciones/999/intenciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intencion\": \"SALUDO\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void intencionPendienteRepetidaDevuelve409() throws Exception {
        agregarIntencion(Intencion.SALUDO, EstadoIntencion.PENDIENTE);

        mockMvc.perform(post("/api/conversaciones/15/intenciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intencion\": \"SALUDO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void resolverDevuelve200() throws Exception {
        agregarIntencion(Intencion.SALUDO, EstadoIntencion.PENDIENTE);

        mockMvc.perform(patch("/api/conversaciones/15/intenciones/7/resolver"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RESUELTA"));
    }

    @Test
    void resolverIntencionAjenaDevuelve404() throws Exception {
        mockMvc.perform(patch("/api/conversaciones/15/intenciones/77/resolver"))
                .andExpect(status().isNotFound());
    }

    @Test
    void resolverYaResueltaDevuelve409() throws Exception {
        agregarIntencion(Intencion.SALUDO, EstadoIntencion.RESUELTA);

        mockMvc.perform(patch("/api/conversaciones/15/intenciones/7/resolver"))
                .andExpect(status().isConflict());
    }

    @Test
    void cerrarDevuelve200() throws Exception {
        agregarIntencion(Intencion.SALUDO, EstadoIntencion.RESUELTA);

        mockMvc.perform(patch("/api/conversaciones/15/cerrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"INTENCION_RESUELTA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADA"))
                .andExpect(jsonPath("$.motivoCierre").value("INTENCION_RESUELTA"));
    }

    @Test
    void cerrarPorInactividadDevuelve400() throws Exception {
        mockMvc.perform(patch("/api/conversaciones/15/cerrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"INACTIVIDAD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("INTENCION_RESUELTA, MANUAL")));
    }

    @Test
    void cerrarConPendientesDevuelve409() throws Exception {
        agregarIntencion(Intencion.CONSULTA_CATALOGO, EstadoIntencion.PENDIENTE);

        mockMvc.perform(patch("/api/conversaciones/15/cerrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"INTENCION_RESUELTA\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void cerrarInexistenteDevuelve404() throws Exception {
        when(conversacionRepository.findByIdParaActualizar(999L)).thenReturn(Optional.empty());

        mockMvc.perform(patch("/api/conversaciones/999/cerrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"MANUAL\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void operarSobreUnaVencidaDevuelve409() throws Exception {
        conversacion.setFechaUltimoMensaje(AHORA.minus(Duration.ofHours(3)));

        mockMvc.perform(post("/api/conversaciones/15/intenciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intencion\": \"SALUDO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("INACTIVIDAD")));
    }
}

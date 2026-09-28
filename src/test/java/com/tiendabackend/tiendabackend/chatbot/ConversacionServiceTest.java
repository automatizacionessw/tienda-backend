package com.tiendabackend.tiendabackend.chatbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tiendabackend.tiendabackend.common.exception.EstadoConversacionInvalidoException;
import com.tiendabackend.tiendabackend.common.exception.RecursoNoEncontradoException;
import com.tiendabackend.tiendabackend.common.exception.SolicitudInvalidaException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ConversacionServiceTest {

    private static final long CLIENTE_ID = 42L;
    private static final long CONVERSACION_ID = 15L;
    private static final Duration INACTIVIDAD = Duration.ofHours(2);
    private static final Instant AHORA = Instant.parse("2026-09-28T14:00:00Z");

    private ConversacionRepository conversacionRepository;
    private ConversacionIntencionRepository intencionRepository;
    private MensajeRepository mensajeRepository;
    private ConversacionService service;

    // Conversaciones pasadas a saveAndFlush, en orden (con copia del estado).
    private final List<String> flushes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        conversacionRepository = mock(ConversacionRepository.class);
        intencionRepository = mock(ConversacionIntencionRepository.class);
        mensajeRepository = mock(MensajeRepository.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenAnswer(inv -> new SimpleTransactionStatus());

        when(conversacionRepository.save(any(Conversacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(conversacionRepository.saveAndFlush(any(Conversacion.class))).thenAnswer(inv -> {
            Conversacion c = inv.getArgument(0);
            flushes.add(c.getEstado() + "/" + c.getClienteAbierta());
            if (c.getId() == null) {
                c.setId(99L);
            }
            return c;
        });
        when(intencionRepository.save(any(ConversacionIntencion.class))).thenAnswer(inv -> {
            ConversacionIntencion i = inv.getArgument(0);
            if (i.getId() == null) {
                i.setId(500L + i.getConversacion().getIntenciones().size());
            }
            return i;
        });

        service = new ConversacionService(conversacionRepository, intencionRepository, mensajeRepository,
                new ConversacionProperties(INACTIVIDAD), Clock.fixed(AHORA, ZoneOffset.UTC), transactionManager);
    }

    private static Conversacion conversacion(Instant ultimoMensaje) {
        Conversacion c = Conversacion.abrir(CLIENTE_ID, ultimoMensaje.minusSeconds(60));
        c.setId(CONVERSACION_ID);
        c.setFechaUltimoMensaje(ultimoMensaje);
        return c;
    }

    private Conversacion vigenteBloqueada() {
        Conversacion c = conversacion(AHORA.minusSeconds(300));
        when(conversacionRepository.findByIdParaActualizar(CONVERSACION_ID)).thenReturn(Optional.of(c));
        return c;
    }

    private static ConversacionIntencion intencion(Conversacion c, long id, Intencion tipo, EstadoIntencion estado) {
        ConversacionIntencion i = new ConversacionIntencion();
        i.setId(id);
        i.setConversacion(c);
        i.setIntencion(tipo);
        i.setEstado(estado);
        i.setOrigen(OrigenClasificacion.MANUAL);
        i.setFechaDeteccion(AHORA.minusSeconds(100));
        c.getIntenciones().add(i);
        return i;
    }

    private static Mensaje mensaje(long id, Conversacion c, Direccion direccion) {
        Mensaje m = new Mensaje();
        m.setId(id);
        m.setConversacion(c);
        m.setDireccion(direccion);
        m.setTipo(TipoMensaje.TEXTO);
        return m;
    }

    private static IntencionRequestDTO pedido(String intencion) {
        return new IntencionRequestDTO(intencion, null, null, null);
    }

    @Nested
    class AsignacionDeMensajes {

        @Test
        void primerMensajeAbreUnaConversacion() {
            when(conversacionRepository.findByClienteAbierta(CLIENTE_ID)).thenReturn(Optional.empty());

            Conversacion c = service.asignarEntrante(CLIENTE_ID, AHORA);

            assertThat(c.getEstado()).isEqualTo(EstadoConversacion.ABIERTA);
            assertThat(c.getClienteAbierta()).isEqualTo(CLIENTE_ID);
            assertThat(c.getFechaInicio()).isEqualTo(AHORA);
            assertThat(flushes).containsExactly("ABIERTA/42");
        }

        @Test
        void mensajeA30MinutosReutilizaLaConversacion() {
            Conversacion existente = conversacion(AHORA);
            when(conversacionRepository.findByClienteAbierta(CLIENTE_ID)).thenReturn(Optional.of(existente));
            Instant fecha = AHORA.plus(Duration.ofMinutes(30));

            Conversacion c = service.asignarEntrante(CLIENTE_ID, fecha);

            assertThat(c).isSameAs(existente);
            assertThat(c.getFechaUltimoMensaje()).isEqualTo(fecha);
            assertThat(flushes).isEmpty();
        }

        @Test
        void mensajeTrasLaInactividadCierraLaAnteriorYAbreOtra() {
            Conversacion anterior = conversacion(AHORA);
            when(conversacionRepository.findByClienteAbierta(CLIENTE_ID)).thenReturn(Optional.of(anterior));
            Instant fecha = AHORA.plus(Duration.ofMinutes(125));

            Conversacion nueva = service.asignarEntrante(CLIENTE_ID, fecha);

            assertThat(anterior.getEstado()).isEqualTo(EstadoConversacion.CERRADA);
            assertThat(anterior.getMotivoCierre()).isEqualTo(MotivoCierre.INACTIVIDAD);
            assertThat(anterior.getFechaCierre()).isEqualTo(AHORA.plus(INACTIVIDAD));
            assertThat(nueva).isNotSameAs(anterior);
            assertThat(nueva.getEstado()).isEqualTo(EstadoConversacion.ABIERTA);
            assertThat(nueva.getFechaInicio()).isEqualTo(fecha);
            // La vieja se libera (flush) ANTES de insertar la nueva.
            assertThat(flushes).containsExactly("CERRADA/null", "ABIERTA/42");
        }

        @Test
        void mensajeTrasUnCierreManualAbreOtra() {
            // Una conversacion cerrada ya no es "la abierta" del cliente.
            when(conversacionRepository.findByClienteAbierta(CLIENTE_ID)).thenReturn(Optional.empty());

            Conversacion nueva = service.asignarEntrante(CLIENTE_ID, AHORA);

            assertThat(nueva.getEstado()).isEqualTo(EstadoConversacion.ABIERTA);
        }

        @Test
        void colisionAlAbrirReintentaYDevuelveLaDelOtroHilo() {
            Conversacion delOtroHilo = conversacion(AHORA);
            when(conversacionRepository.findByClienteAbierta(CLIENTE_ID))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(delOtroHilo));
            when(conversacionRepository.saveAndFlush(any(Conversacion.class)))
                    .thenThrow(new DataIntegrityViolationException("cliente_abierta"));

            Conversacion c = service.asignarEntrante(CLIENTE_ID, AHORA.plusSeconds(1));

            assertThat(c).isSameAs(delOtroHilo);
        }

        @Test
        void salienteMueveLaActividadDeUnaAbierta() {
            Conversacion c = conversacion(AHORA);
            when(conversacionRepository.findByIdParaActualizar(CONVERSACION_ID)).thenReturn(Optional.of(c));

            service.registrarSaliente(CONVERSACION_ID, AHORA.plusSeconds(5));

            assertThat(c.getFechaUltimoMensaje()).isEqualTo(AHORA.plusSeconds(5));
        }

        @Test
        void salienteNoTocaUnaCerrada() {
            Conversacion c = conversacion(AHORA);
            c.cerrar(MotivoCierre.MANUAL, AHORA.plusSeconds(1));
            when(conversacionRepository.findByIdParaActualizar(CONVERSACION_ID)).thenReturn(Optional.of(c));

            service.registrarSaliente(CONVERSACION_ID, AHORA.plusSeconds(5));

            assertThat(c.getFechaUltimoMensaje()).isEqualTo(AHORA);
            verify(conversacionRepository, never()).save(any());
        }
    }

    @Nested
    class RegistroDeIntenciones {

        @Test
        void registraUnaIntencionPendiente() {
            Conversacion c = vigenteBloqueada();

            IntencionResponseDTO r = service.registrarIntencion(CONVERSACION_ID,
                    new IntencionRequestDTO("CONSULTA_CATALOGO", null, null, "LLM"));

            assertThat(r.getIntencion()).isEqualTo(Intencion.CONSULTA_CATALOGO);
            assertThat(r.getEstado()).isEqualTo(EstadoIntencion.PENDIENTE);
            assertThat(r.getOrigen()).isEqualTo(OrigenClasificacion.LLM);
            assertThat(r.getFechaDeteccion()).isEqualTo(AHORA);
            assertThat(c.getIntenciones()).hasSize(1);
        }

        @Test
        void sinOrigenSeAsumeManual() {
            vigenteBloqueada();

            assertThat(service.registrarIntencion(CONVERSACION_ID, pedido("SALUDO")).getOrigen())
                    .isEqualTo(OrigenClasificacion.MANUAL);
        }

        @Test
        void admiteVariasIntencionesDistintas() {
            Conversacion c = vigenteBloqueada();
            intencion(c, 1L, Intencion.SALUDO, EstadoIntencion.PENDIENTE);

            service.registrarIntencion(CONVERSACION_ID, pedido("CONSULTA_CATALOGO"));

            assertThat(c.getIntenciones()).extracting(ConversacionIntencion::getIntencion)
                    .containsExactly(Intencion.SALUDO, Intencion.CONSULTA_CATALOGO);
        }

        @Test
        void rechazaUnaPendienteRepetida() {
            Conversacion c = vigenteBloqueada();
            intencion(c, 1L, Intencion.CONSULTA_CATALOGO, EstadoIntencion.PENDIENTE);

            assertThatThrownBy(() -> service.registrarIntencion(CONVERSACION_ID, pedido("CONSULTA_CATALOGO")))
                    .isInstanceOf(EstadoConversacionInvalidoException.class)
                    .hasMessageContaining("CONSULTA_CATALOGO");
            assertThat(c.getIntenciones()).hasSize(1);
        }

        @Test
        void admiteLaMismaIntencionDespuesDeResuelta() {
            Conversacion c = vigenteBloqueada();
            intencion(c, 1L, Intencion.CONSULTA_CATALOGO, EstadoIntencion.RESUELTA);

            service.registrarIntencion(CONVERSACION_ID, pedido("CONSULTA_CATALOGO"));

            assertThat(c.getIntenciones()).extracting(ConversacionIntencion::getEstado)
                    .containsExactly(EstadoIntencion.RESUELTA, EstadoIntencion.PENDIENTE);
        }

        @Test
        void otraConDetalleSeRegistra() {
            vigenteBloqueada();

            IntencionResponseDTO r = service.registrarIntencion(CONVERSACION_ID,
                    new IntencionRequestDTO("OTRA", "pregunta por la garantía de un producto", null, null));

            assertThat(r.getDetalle()).isEqualTo("pregunta por la garantía de un producto");
        }

        @Test
        void otraSinDetalleSeRechaza() {
            vigenteBloqueada();

            assertThatThrownBy(() -> service.registrarIntencion(CONVERSACION_ID,
                    new IntencionRequestDTO("OTRA", "  ", null, null)))
                    .isInstanceOf(SolicitudInvalidaException.class)
                    .hasMessageContaining("OTRA");
            verify(intencionRepository, never()).save(any());
        }

        @Test
        void intencionInexistenteListaLosValoresValidos() {
            assertThatThrownBy(() -> service.registrarIntencion(CONVERSACION_ID, pedido("RECLAMO")))
                    .isInstanceOf(SolicitudInvalidaException.class)
                    .hasMessageContaining("RECLAMO")
                    .hasMessageContaining("SALUDO, CONSULTA_CATALOGO, INICIAR_PEDIDO, CONSULTAR_ESTADO_PEDIDO, OTRA");
        }

        @Test
        void origenInvalidoSeRechaza() {
            assertThatThrownBy(() -> service.registrarIntencion(CONVERSACION_ID,
                    new IntencionRequestDTO("SALUDO", null, null, "ROBOT")))
                    .isInstanceOf(SolicitudInvalidaException.class)
                    .hasMessageContaining("MANUAL, LLM");
        }

        @Test
        void mensajeDeOrigenEntranteDeLaConversacionSeAsocia() {
            Conversacion c = vigenteBloqueada();
            when(mensajeRepository.findById(120L)).thenReturn(Optional.of(mensaje(120L, c, Direccion.ENTRANTE)));

            IntencionResponseDTO r = service.registrarIntencion(CONVERSACION_ID,
                    new IntencionRequestDTO("SALUDO", null, 120L, null));

            assertThat(r.getMensajeId()).isEqualTo(120L);
        }

        @Test
        void mensajeDeOtraConversacionSeRechaza() {
            vigenteBloqueada();
            Conversacion otra = conversacion(AHORA);
            otra.setId(16L);
            when(mensajeRepository.findById(120L)).thenReturn(Optional.of(mensaje(120L, otra, Direccion.ENTRANTE)));

            assertThatThrownBy(() -> service.registrarIntencion(CONVERSACION_ID,
                    new IntencionRequestDTO("SALUDO", null, 120L, null)))
                    .isInstanceOf(SolicitudInvalidaException.class);
        }

        @Test
        void mensajeSalienteSeRechaza() {
            Conversacion c = vigenteBloqueada();
            when(mensajeRepository.findById(121L)).thenReturn(Optional.of(mensaje(121L, c, Direccion.SALIENTE)));

            assertThatThrownBy(() -> service.registrarIntencion(CONVERSACION_ID,
                    new IntencionRequestDTO("SALUDO", null, 121L, null)))
                    .isInstanceOf(SolicitudInvalidaException.class);
        }

        @Test
        void conversacionInexistenteResponde404() {
            when(conversacionRepository.findByIdParaActualizar(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.registrarIntencion(999L, pedido("SALUDO")))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }

        @Test
        void conversacionCerradaNoAdmiteIntenciones() {
            Conversacion c = vigenteBloqueada();
            c.cerrar(MotivoCierre.MANUAL, AHORA.minusSeconds(10));

            assertThatThrownBy(() -> service.registrarIntencion(CONVERSACION_ID, pedido("SALUDO")))
                    .isInstanceOf(EstadoConversacionInvalidoException.class);
            assertThat(c.getIntenciones()).isEmpty();
        }

        @Test
        void conversacionVencidaNoAdmiteIntencionesNiSePersisteSuCierre() {
            Conversacion c = conversacion(AHORA.minus(Duration.ofHours(3)));
            when(conversacionRepository.findByIdParaActualizar(CONVERSACION_ID)).thenReturn(Optional.of(c));

            assertThatThrownBy(() -> service.registrarIntencion(CONVERSACION_ID, pedido("SALUDO")))
                    .isInstanceOf(EstadoConversacionInvalidoException.class)
                    .hasMessageContaining("INACTIVIDAD");
            assertThat(c.getEstado()).isEqualTo(EstadoConversacion.ABIERTA);
        }
    }

    @Nested
    class ResolucionDeIntenciones {

        @Test
        void resuelveUnaPendienteSinCerrarLaConversacion() {
            Conversacion c = vigenteBloqueada();
            intencion(c, 1L, Intencion.CONSULTA_CATALOGO, EstadoIntencion.PENDIENTE);

            IntencionResponseDTO r = service.resolverIntencion(CONVERSACION_ID, 1L);

            assertThat(r.getEstado()).isEqualTo(EstadoIntencion.RESUELTA);
            assertThat(r.getFechaResolucion()).isEqualTo(AHORA);
            assertThat(c.getEstado()).isEqualTo(EstadoConversacion.ABIERTA);
        }

        @Test
        void yaResueltaResponde409YConservaSuFecha() {
            Conversacion c = vigenteBloqueada();
            ConversacionIntencion i = intencion(c, 1L, Intencion.SALUDO, EstadoIntencion.RESUELTA);
            Instant original = AHORA.minusSeconds(50);
            i.setFechaResolucion(original);

            assertThatThrownBy(() -> service.resolverIntencion(CONVERSACION_ID, 1L))
                    .isInstanceOf(EstadoConversacionInvalidoException.class);
            assertThat(i.getFechaResolucion()).isEqualTo(original);
        }

        @Test
        void intencionDeOtraConversacionResponde404() {
            vigenteBloqueada();

            assertThatThrownBy(() -> service.resolverIntencion(CONVERSACION_ID, 77L))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }

        @Test
        void conversacionCerradaNoAdmiteResoluciones() {
            Conversacion c = vigenteBloqueada();
            intencion(c, 1L, Intencion.SALUDO, EstadoIntencion.PENDIENTE);
            c.cerrar(MotivoCierre.MANUAL, AHORA.minusSeconds(10));

            assertThatThrownBy(() -> service.resolverIntencion(CONVERSACION_ID, 1L))
                    .isInstanceOf(EstadoConversacionInvalidoException.class);
        }
    }

    @Nested
    class Cierre {

        @Test
        void cierraPorResolucionConTodoResuelto() {
            Conversacion c = vigenteBloqueada();
            intencion(c, 1L, Intencion.SALUDO, EstadoIntencion.RESUELTA);
            intencion(c, 2L, Intencion.CONSULTA_CATALOGO, EstadoIntencion.RESUELTA);

            ConversacionResponseDTO r = service.cerrar(CONVERSACION_ID, new CierreRequestDTO("INTENCION_RESUELTA"));

            assertThat(r.getEstado()).isEqualTo(EstadoConversacion.CERRADA);
            assertThat(r.getMotivoCierre()).isEqualTo(MotivoCierre.INTENCION_RESUELTA);
            assertThat(r.getFechaCierre()).isEqualTo(AHORA);
            assertThat(c.getClienteAbierta()).isNull();
        }

        @Test
        void porResolucionConPendientesResponde409() {
            Conversacion c = vigenteBloqueada();
            intencion(c, 1L, Intencion.CONSULTA_CATALOGO, EstadoIntencion.PENDIENTE);

            assertThatThrownBy(() -> service.cerrar(CONVERSACION_ID, new CierreRequestDTO("INTENCION_RESUELTA")))
                    .isInstanceOf(EstadoConversacionInvalidoException.class)
                    .hasMessageContaining("CONSULTA_CATALOGO");
            assertThat(c.getEstado()).isEqualTo(EstadoConversacion.ABIERTA);
        }

        @Test
        void porResolucionSinIntencionesResponde409() {
            Conversacion c = vigenteBloqueada();

            assertThatThrownBy(() -> service.cerrar(CONVERSACION_ID, new CierreRequestDTO("INTENCION_RESUELTA")))
                    .isInstanceOf(EstadoConversacionInvalidoException.class);
            assertThat(c.getEstado()).isEqualTo(EstadoConversacion.ABIERTA);
        }

        @Test
        void manualCierraConPendientesYLasConserva() {
            Conversacion c = vigenteBloqueada();
            ConversacionIntencion otra = intencion(c, 1L, Intencion.OTRA, EstadoIntencion.PENDIENTE);

            ConversacionResponseDTO r = service.cerrar(CONVERSACION_ID, new CierreRequestDTO("MANUAL"));

            assertThat(r.getEstado()).isEqualTo(EstadoConversacion.CERRADA);
            assertThat(r.getMotivoCierre()).isEqualTo(MotivoCierre.MANUAL);
            assertThat(otra.getEstado()).isEqualTo(EstadoIntencion.PENDIENTE);
        }

        @Test
        void inactividadNoSePuedePedir() {
            Conversacion c = vigenteBloqueada();

            assertThatThrownBy(() -> service.cerrar(CONVERSACION_ID, new CierreRequestDTO("INACTIVIDAD")))
                    .isInstanceOf(SolicitudInvalidaException.class)
                    .hasMessageContaining("INTENCION_RESUELTA, MANUAL");
            assertThat(c.getEstado()).isEqualTo(EstadoConversacion.ABIERTA);
        }

        @Test
        void conversacionCerradaNoSeVuelveACerrar() {
            Conversacion c = vigenteBloqueada();
            Instant cierre = AHORA.minusSeconds(10);
            c.cerrar(MotivoCierre.INTENCION_RESUELTA, cierre);

            assertThatThrownBy(() -> service.cerrar(CONVERSACION_ID, new CierreRequestDTO("MANUAL")))
                    .isInstanceOf(EstadoConversacionInvalidoException.class);
            assertThat(c.getMotivoCierre()).isEqualTo(MotivoCierre.INTENCION_RESUELTA);
            assertThat(c.getFechaCierre()).isEqualTo(cierre);
        }
    }

    @Nested
    class Consultas {

        @Test
        void listarAplicaLosFiltrosConElLimiteDeInactividad() {
            Conversacion c = conversacion(AHORA.minusSeconds(60));
            when(conversacionRepository.buscar(anyBoolean(), anyLong(), anyBoolean(), anyBoolean(), anyBoolean(),
                    any(), any())).thenReturn(List.of(c));

            List<ConversacionResponseDTO> r = service.listar(null, "abierta", true);

            verify(conversacionRepository).buscar(false, 0L, true, false, true,
                    EstadoConversacion.ABIERTA, AHORA.minus(INACTIVIDAD));
            assertThat(r).singleElement().satisfies(dto -> assertThat(dto.getEstado())
                    .isEqualTo(EstadoConversacion.ABIERTA));
        }

        @Test
        void listarPorClienteYCerradas() {
            service.listar(CLIENTE_ID, "CERRADA", false);

            verify(conversacionRepository).buscar(eq(true), eq(CLIENTE_ID), eq(false), eq(true), eq(false),
                    eq(EstadoConversacion.ABIERTA), eq(AHORA.minus(INACTIVIDAD)));
        }

        @Test
        void listarConEstadoInvalidoResponde400() {
            assertThatThrownBy(() -> service.listar(null, "INVALIDO", false))
                    .isInstanceOf(SolicitudInvalidaException.class)
                    .hasMessageContaining("ABIERTA, CERRADA");
        }

        @Test
        void unaVencidaSeInformaCerradaPorInactividad() {
            Conversacion vencida = conversacion(AHORA.minus(Duration.ofHours(3)));
            when(conversacionRepository.buscar(anyBoolean(), anyLong(), anyBoolean(), anyBoolean(), anyBoolean(),
                    any(), any())).thenReturn(List.of(vencida));

            ConversacionResponseDTO dto = service.listar(null, null, false).get(0);

            assertThat(dto.getEstado()).isEqualTo(EstadoConversacion.CERRADA);
            assertThat(dto.getMotivoCierre()).isEqualTo(MotivoCierre.INACTIVIDAD);
            assertThat(dto.getFechaCierre()).isEqualTo(AHORA.minus(Duration.ofHours(1)));
        }

        @Test
        void detalleIncluyeMensajesEIntenciones() {
            Conversacion c = conversacion(AHORA.minusSeconds(60));
            intencion(c, 1L, Intencion.CONSULTA_CATALOGO, EstadoIntencion.PENDIENTE);
            Mensaje entrante = mensaje(120L, c, Direccion.ENTRANTE);
            entrante.setTexto("quiero ver el catálogo");
            Mensaje saliente = mensaje(121L, c, Direccion.SALIENTE);
            when(conversacionRepository.findById(CONVERSACION_ID)).thenReturn(Optional.of(c));
            when(mensajeRepository.findByConversacionIdOrderByFechaTelegramAscIdAsc(CONVERSACION_ID))
                    .thenReturn(List.of(entrante, saliente));

            ConversacionDetalleResponseDTO dto = service.obtenerDetalle(CONVERSACION_ID);

            assertThat(dto.getMensajes()).extracting(MensajeResponseDTO::getId).containsExactly(120L, 121L);
            assertThat(dto.getMensajes().get(0).getTexto()).isEqualTo("quiero ver el catálogo");
            assertThat(dto.getIntenciones()).singleElement()
                    .satisfies(i -> assertThat(i.getIntencion()).isEqualTo(Intencion.CONSULTA_CATALOGO));
        }

        @Test
        void detalleDeInexistenteResponde404() {
            when(conversacionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.obtenerDetalle(999L))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }
    }
}

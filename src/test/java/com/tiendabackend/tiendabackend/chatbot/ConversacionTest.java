package com.tiendabackend.tiendabackend.chatbot;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ConversacionTest {

    private static final Duration INACTIVIDAD = Duration.ofHours(2);
    private static final Instant ULTIMO = Instant.parse("2026-09-28T10:00:00Z");

    private static Conversacion abierta() {
        return Conversacion.abrir(42L, ULTIMO);
    }

    @Test
    void abrirDejaLaConversacionAbiertaYMarcadaComoLaAbiertaDelCliente() {
        Conversacion c = abierta();

        assertThat(c.getEstado()).isEqualTo(EstadoConversacion.ABIERTA);
        assertThat(c.getClienteAbierta()).isEqualTo(42L);
        assertThat(c.getFechaInicio()).isEqualTo(ULTIMO);
        assertThat(c.getFechaUltimoMensaje()).isEqualTo(ULTIMO);
    }

    @Test
    void justoAntesDelLimiteSigueVigente() {
        assertThat(abierta().estaVigente(ULTIMO.plus(INACTIVIDAD).minusMillis(1), INACTIVIDAD)).isTrue();
    }

    @Test
    void justoEnElLimiteYaEstaVencida() {
        assertThat(abierta().estaVigente(ULTIMO.plus(INACTIVIDAD), INACTIVIDAD)).isFalse();
    }

    @Test
    void unaCerradaNoEstaVigenteAunqueNoHayaPasadoElTiempo() {
        Conversacion c = abierta();
        c.cerrar(MotivoCierre.MANUAL, ULTIMO.plusSeconds(60));

        assertThat(c.estaVigente(ULTIMO.plusSeconds(120), INACTIVIDAD)).isFalse();
        assertThat(c.getClienteAbierta()).isNull();
    }

    @Test
    void abiertaVencidaSeInformaCerradaPorInactividad() {
        Conversacion.EstadoEfectivo efectivo = abierta().estadoEfectivo(ULTIMO.plus(Duration.ofHours(3)), INACTIVIDAD);

        assertThat(efectivo.estado()).isEqualTo(EstadoConversacion.CERRADA);
        assertThat(efectivo.motivoCierre()).isEqualTo(MotivoCierre.INACTIVIDAD);
        assertThat(efectivo.fechaCierre()).isEqualTo(ULTIMO.plus(INACTIVIDAD));
    }

    @Test
    void abiertaVigenteSeInformaTalCual() {
        Conversacion.EstadoEfectivo efectivo = abierta().estadoEfectivo(ULTIMO.plusSeconds(60), INACTIVIDAD);

        assertThat(efectivo.estado()).isEqualTo(EstadoConversacion.ABIERTA);
        assertThat(efectivo.motivoCierre()).isNull();
        assertThat(efectivo.fechaCierre()).isNull();
    }

    @Test
    void cerradaSeInformaConLoGuardado() {
        Conversacion c = abierta();
        Instant cierre = ULTIMO.plusSeconds(300);
        c.cerrar(MotivoCierre.INTENCION_RESUELTA, cierre);

        Conversacion.EstadoEfectivo efectivo = c.estadoEfectivo(ULTIMO.plus(Duration.ofHours(5)), INACTIVIDAD);

        assertThat(efectivo.estado()).isEqualTo(EstadoConversacion.CERRADA);
        assertThat(efectivo.motivoCierre()).isEqualTo(MotivoCierre.INTENCION_RESUELTA);
        assertThat(efectivo.fechaCierre()).isEqualTo(cierre);
    }

    @Test
    void cerrarPorInactividadUsaElVencimientoComoFecha() {
        Conversacion c = abierta();
        c.cerrarPorInactividad(INACTIVIDAD);

        assertThat(c.getEstado()).isEqualTo(EstadoConversacion.CERRADA);
        assertThat(c.getMotivoCierre()).isEqualTo(MotivoCierre.INACTIVIDAD);
        assertThat(c.getFechaCierre()).isEqualTo(ULTIMO.plus(INACTIVIDAD));
        assertThat(c.getClienteAbierta()).isNull();
    }

    @Test
    void laActividadAtrasadaNoRetrocedeLaFecha() {
        Conversacion c = abierta();
        c.registrarActividad(ULTIMO.plusSeconds(60));
        c.registrarActividad(ULTIMO.plusSeconds(10));

        assertThat(c.getFechaUltimoMensaje()).isEqualTo(ULTIMO.plusSeconds(60));
    }
}

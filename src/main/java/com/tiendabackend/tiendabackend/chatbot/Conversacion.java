package com.tiendabackend.tiendabackend.chatbot;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(
        name = "conversacion",
        indexes = @Index(name = "ix_conversacion_cliente", columnList = "cliente_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Conversacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Cliente es de OTRO modulo: solo su id, sin @ManyToOne (regla 3).
    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    // Vale cliente_id mientras la conversacion esta ABIERTA y null al cerrarla.
    // Con la UQ equivale a un indice unico parcial (que Hibernate no genera):
    // PostgreSQL admite varios null, asi que garantiza una sola ABIERTA por cliente.
    @Column(name = "cliente_abierta", unique = true)
    private Long clienteAbierta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EstadoConversacion estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_cierre", length = 32)
    private MotivoCierre motivoCierre;

    @Column(name = "fecha_inicio", nullable = false)
    private Instant fechaInicio;

    // Fecha de Telegram del ultimo mensaje (entrante o saliente): de ella
    // depende la vigencia.
    @Column(name = "fecha_ultimo_mensaje", nullable = false)
    private Instant fechaUltimoMensaje;

    @Column(name = "fecha_cierre")
    private Instant fechaCierre;

    @OneToMany(mappedBy = "conversacion", cascade = CascadeType.ALL)
    @OrderBy("fechaDeteccion ASC, id ASC")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<ConversacionIntencion> intenciones = new ArrayList<>();

    public static Conversacion abrir(Long clienteId, Instant fecha) {
        Conversacion conversacion = new Conversacion();
        conversacion.setClienteId(clienteId);
        conversacion.setClienteAbierta(clienteId);
        conversacion.setEstado(EstadoConversacion.ABIERTA);
        conversacion.setFechaInicio(fecha);
        conversacion.setFechaUltimoMensaje(fecha);
        return conversacion;
    }

    // Regla unica de vigencia: ABIERTA y sin superar la inactividad desde el
    // ultimo mensaje. Justo en el limite ya se considera vencida. Las
    // consultas JPQL de ConversacionRepository replican esta regla.
    public boolean estaVigente(Instant instante, Duration inactividad) {
        return estado == EstadoConversacion.ABIERTA
                && instante.isBefore(vencimiento(inactividad));
    }

    public Instant vencimiento(Duration inactividad) {
        return fechaUltimoMensaje.plus(inactividad);
    }

    // Una ABIERTA vencida se informa como cerrada por inactividad aunque su
    // cierre todavia no se haya persistido (solo el ingreso lo persiste).
    public EstadoEfectivo estadoEfectivo(Instant ahora, Duration inactividad) {
        if (estado == EstadoConversacion.ABIERTA && !estaVigente(ahora, inactividad)) {
            return new EstadoEfectivo(EstadoConversacion.CERRADA, MotivoCierre.INACTIVIDAD, vencimiento(inactividad));
        }
        return new EstadoEfectivo(estado, motivoCierre, fechaCierre);
    }

    public void cerrar(MotivoCierre motivo, Instant fecha) {
        estado = EstadoConversacion.CERRADA;
        motivoCierre = motivo;
        fechaCierre = fecha;
        clienteAbierta = null;
    }

    public void cerrarPorInactividad(Duration inactividad) {
        cerrar(MotivoCierre.INACTIVIDAD, vencimiento(inactividad));
    }

    // Se toma el maximo: una reentrega atrasada no hace retroceder la fecha.
    public void registrarActividad(Instant fecha) {
        if (fecha.isAfter(fechaUltimoMensaje)) {
            fechaUltimoMensaje = fecha;
        }
    }

    public record EstadoEfectivo(EstadoConversacion estado, MotivoCierre motivoCierre, Instant fechaCierre) {
    }
}

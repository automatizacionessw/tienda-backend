package com.tiendabackend.tiendabackend.chatbot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

// La regla "una sola PENDIENTE por tipo en cada conversacion" no tiene
// restriccion en BD: la garantiza ConversacionService con la conversacion bloqueada.
@Entity
@Table(
        name = "conversacion_intencion",
        indexes = @Index(name = "ix_conversacion_intencion_conversacion", columnList = "conversacion_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversacionIntencion {

    public static final int DETALLE_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversacion_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Conversacion conversacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Intencion intencion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EstadoIntencion estado;

    // Obligatorio para OTRA: describe que pidio el cliente.
    @Column(length = DETALLE_MAX)
    private String detalle;

    // Mensaje entrante que la origino, si se indico.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mensaje_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Mensaje mensaje;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrigenClasificacion origen;

    @Column(name = "fecha_deteccion", nullable = false)
    private Instant fechaDeteccion;

    @Column(name = "fecha_resolucion")
    private Instant fechaResolucion;
}

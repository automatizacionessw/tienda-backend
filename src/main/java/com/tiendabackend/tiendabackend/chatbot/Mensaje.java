package com.tiendabackend.tiendabackend.chatbot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "mensaje",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mensaje_cliente_telegram_message",
                columnNames = {"cliente_id", "telegram_message_id"}),
        indexes = @Index(name = "ix_mensaje_cliente", columnList = "cliente_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Mensaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Cliente pertenece a OTRO modulo: se guarda solo su id, sin @ManyToOne,
    // para no cruzar Entities entre modulos (regla 3). Por eso Hibernate no
    // genera FK; el flujo garantiza que el cliente existe antes del mensaje.
    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    // message_id es unico por chat y compartido por ambas direcciones; junto
    // con cliente_id (chat privado) identifica al mensaje y deduplica reentregas.
    @Column(name = "telegram_message_id", nullable = false)
    private Long telegramMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Direccion direccion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TipoMensaje tipo;

    // Texto del mensaje o caption del adjunto (Telegram: max 4096 / 1024).
    @Column(length = 4096)
    private String texto;

    @Column(name = "fecha_telegram", nullable = false)
    private Instant fechaTelegram;

    @Column(name = "fecha_registro", nullable = false)
    private Instant fechaRegistro;
}

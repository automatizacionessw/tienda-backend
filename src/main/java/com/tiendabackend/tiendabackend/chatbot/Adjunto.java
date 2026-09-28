package com.tiendabackend.tiendabackend.chatbot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "adjunto")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Adjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Mensaje y Adjunto son del MISMO modulo, asi que la relacion JPA directa
    // respeta la arquitectura. Hoy es 1:1 (una nota de voz por mensaje).
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mensaje_id", nullable = false, unique = true)
    private Mensaje mensaje;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TipoAdjunto tipo;

    @Column(name = "telegram_file_id", nullable = false)
    private String telegramFileId;

    @Column(name = "telegram_file_unique_id", nullable = false)
    private String telegramFileUniqueId;

    @Column(name = "mime_type", length = 128)
    private String mimeType;

    @Column(name = "tamano_bytes")
    private Long tamanoBytes;

    @Column(name = "duracion_seg")
    private Integer duracionSeg;

    // Clave opaca del AlmacenamientoArchivos; null mientras esta PENDIENTE o si fallo.
    @Column(name = "clave_almacenamiento", length = 512)
    private String claveAlmacenamiento;

    // PENDIENTE es transitorio: si el proceso muere durante la descarga, el
    // adjunto queda visible como pendiente en lugar de desaparecer.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EstadoAdjunto estado;

    @Column(name = "fecha_registro", nullable = false)
    private Instant fechaRegistro;
}

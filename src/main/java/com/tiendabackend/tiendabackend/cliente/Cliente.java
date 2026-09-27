package com.tiendabackend.tiendabackend.cliente;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cliente")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Identidad estable del cliente: el username y el nombre pueden cambiar,
    // el id de usuario de Telegram no. Los ids de Telegram superan 32 bits.
    @Column(name = "telegram_user_id", nullable = false, unique = true)
    private Long telegramUserId;

    @Column(length = 64)
    private String username;

    @Column(nullable = false, length = 128)
    private String nombre;

    @Column(length = 128)
    private String apellido;

    @Column(length = 16)
    private String idioma;

    @Column(name = "fecha_alta", nullable = false)
    private Instant fechaAlta;

    @Column(name = "fecha_actualizacion")
    private Instant fechaActualizacion;
}

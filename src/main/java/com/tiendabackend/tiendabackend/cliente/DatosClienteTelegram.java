package com.tiendabackend.tiendabackend.cliente;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Datos de perfil que llegan de Telegram. Es un DTO propio del modulo cliente
// para que este no dependa de las clases de la libreria de Telegram.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DatosClienteTelegram {

    private Long telegramUserId;
    private String username;
    private String nombre;
    private String apellido;
    private String idioma;
}

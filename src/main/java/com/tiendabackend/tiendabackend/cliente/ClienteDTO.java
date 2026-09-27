package com.tiendabackend.tiendabackend.cliente;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClienteDTO {

    private Long id;
    private Long telegramUserId;
    private String username;
    private String nombre;
    private String apellido;
    private String idioma;
    private Instant fechaAlta;

    public static ClienteDTO desdeEntidad(Cliente cliente) {
        return new ClienteDTO(
                cliente.getId(),
                cliente.getTelegramUserId(),
                cliente.getUsername(),
                cliente.getNombre(),
                cliente.getApellido(),
                cliente.getIdioma(),
                cliente.getFechaAlta()
        );
    }
}

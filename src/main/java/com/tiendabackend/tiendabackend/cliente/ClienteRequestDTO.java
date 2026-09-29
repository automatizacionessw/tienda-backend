package com.tiendabackend.tiendabackend.cliente;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClienteRequestDTO {

    @Schema(description = "ID de usuario de Telegram", example = "123456789")
    @NotNull(message = "El id de usuario de Telegram es obligatorio")
    private Long telegramUserId;

    @Schema(description = "Nombre de usuario en Telegram", example = "juanperez")
    private String username;

    @Schema(description = "Nombre del cliente", example = "Juan")
    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @Schema(description = "Apellido del cliente", example = "Pérez")
    private String apellido;

    @Schema(description = "Código de idioma", example = "es")
    private String idioma;
}

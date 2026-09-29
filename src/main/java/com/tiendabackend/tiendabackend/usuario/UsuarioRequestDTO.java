package com.tiendabackend.tiendabackend.usuario;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioRequestDTO {

    @Schema(description = "Nombre de usuario único", example = "admin_dueno")
    @NotBlank(message = "El nombre de usuario es obligatorio")
    private String username;

    @Schema(description = "Nombre completo del dueño/usuario", example = "Carlos Morales")
    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @Schema(description = "Correo electrónico institucional o personal", example = "carlos@tienda.com")
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato de email es inválido")
    private String email;

    @Schema(description = "Teléfono de contacto", example = "+59177123456")
    private String telefono;

    @Schema(description = "Rol en el sistema", example = "DUEÑO")
    @NotBlank(message = "El rol es obligatorio")
    private String rol;
}

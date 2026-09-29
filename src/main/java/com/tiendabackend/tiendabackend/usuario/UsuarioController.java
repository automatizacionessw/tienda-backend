package com.tiendabackend.tiendabackend.usuario;

import com.tiendabackend.tiendabackend.common.openapi.ErrorRecursoNoEncontrado;
import com.tiendabackend.tiendabackend.common.openapi.ErrorValidacion;
import com.tiendabackend.tiendabackend.common.openapi.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
@Tag(name = OpenApiConfig.TAG_USUARIOS)
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Operation(summary = "Listar todos los usuarios/dueños",
            description = "Devuelve todos los usuarios registrados (dueños, vendedores, administradores).")
    @ApiResponse(responseCode = "200", description = "Lista de usuarios (puede estar vacía)")
    @GetMapping
    public ResponseEntity<List<UsuarioDTO>> listarUsuarios() {
        return ResponseEntity.ok(usuarioService.listarTodo());
    }

    @Operation(summary = "Obtener un usuario por ID",
            description = "Devuelve la información detallada de un usuario específico.")
    @ApiResponse(responseCode = "200", description = "Usuario encontrado")
    @ApiResponse(responseCode = "404", description = "El usuario no existe",
            content = @Content(schema = @Schema(implementation = ErrorRecursoNoEncontrado.class)))
    @GetMapping("/{id}")
    public ResponseEntity<UsuarioDTO> obtenerUsuario(
            @Parameter(description = "Identificador del usuario", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(usuarioService.obtenerPorId(id));
    }

    @Operation(summary = "Crear un nuevo usuario/dueño",
            description = "Registra un usuario administrador o dueño en el sistema.")
    @ApiResponse(responseCode = "201", description = "Usuario creado exitosamente")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o duplicados",
            content = @Content(schema = @Schema(implementation = ErrorValidacion.class)))
    @PostMapping
    public ResponseEntity<UsuarioDTO> crearUsuario(@Valid @RequestBody UsuarioRequestDTO datos) {
        UsuarioDTO usuarioCreado = usuarioService.crearUsuario(datos);
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioCreado);
    }

    @Operation(summary = "Actualizar un usuario/dueño",
            description = "Modifica los datos personales, email o rol de un usuario.")
    @ApiResponse(responseCode = "200", description = "Usuario actualizado exitosamente")
    @ApiResponse(responseCode = "400", description = "Datos inválidos",
            content = @Content(schema = @Schema(implementation = ErrorValidacion.class)))
    @ApiResponse(responseCode = "404", description = "El usuario no existe",
            content = @Content(schema = @Schema(implementation = ErrorRecursoNoEncontrado.class)))
    @PutMapping("/{id}")
    public ResponseEntity<UsuarioDTO> actualizarUsuario(
            @Parameter(description = "Identificador del usuario", example = "1") @PathVariable Long id,
            @Valid @RequestBody UsuarioRequestDTO datos) {
        return ResponseEntity.ok(usuarioService.actualizarUsuario(id, datos));
    }

    @Operation(summary = "Eliminar un usuario/dueño",
            description = "Elimina un usuario del sistema.")
    @ApiResponse(responseCode = "204", description = "Usuario eliminado exitosamente")
    @ApiResponse(responseCode = "404", description = "El usuario no existe",
            content = @Content(schema = @Schema(implementation = ErrorRecursoNoEncontrado.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarUsuario(
            @Parameter(description = "Identificador del usuario", example = "1") @PathVariable Long id) {
        usuarioService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}

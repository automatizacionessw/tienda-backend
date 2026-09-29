package com.tiendabackend.tiendabackend.cliente;

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
@RequestMapping("/api/clientes")
@Tag(name = OpenApiConfig.TAG_CLIENTES)
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @Operation(summary = "Listar todos los clientes",
            description = "Devuelve todos los clientes registrados en la base de datos.")
    @ApiResponse(responseCode = "200", description = "Lista de clientes (puede estar vacía)")
    @GetMapping
    public ResponseEntity<List<ClienteDTO>> listarClientes() {
        return ResponseEntity.ok(clienteService.listarTodo());
    }

    @Operation(summary = "Obtener un cliente por ID",
            description = "Devuelve la información de un cliente específico según su ID primario.")
    @ApiResponse(responseCode = "200", description = "Cliente encontrado")
    @ApiResponse(responseCode = "404", description = "El cliente no existe",
            content = @Content(schema = @Schema(implementation = ErrorRecursoNoEncontrado.class)))
    @GetMapping("/{id}")
    public ResponseEntity<ClienteDTO> obtenerCliente(
            @Parameter(description = "Identificador del cliente", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(clienteService.obtenerPorId(id));
    }

    @Operation(summary = "Crear un cliente manualmente",
            description = "Registra un nuevo cliente manualmente desde el panel de administración.")
    @ApiResponse(responseCode = "201", description = "Cliente creado exitosamente")
    @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ErrorValidacion.class)))
    @PostMapping
    public ResponseEntity<ClienteDTO> crearCliente(@Valid @RequestBody ClienteRequestDTO datos) {
        ClienteDTO clienteCreado = clienteService.crearCliente(datos);
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteCreado);
    }

    @Operation(summary = "Actualizar datos de un cliente",
            description = "Actualiza los datos personales o de Telegram de un cliente existente.")
    @ApiResponse(responseCode = "200", description = "Cliente actualizado exitosamente")
    @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ErrorValidacion.class)))
    @ApiResponse(responseCode = "404", description = "El cliente no existe",
            content = @Content(schema = @Schema(implementation = ErrorRecursoNoEncontrado.class)))
    @PutMapping("/{id}")
    public ResponseEntity<ClienteDTO> actualizarCliente(
            @Parameter(description = "Identificador del cliente", example = "1") @PathVariable Long id,
            @Valid @RequestBody ClienteRequestDTO datos) {
        return ResponseEntity.ok(clienteService.actualizarCliente(id, datos));
    }

    @Operation(summary = "Eliminar un cliente",
            description = "Elimina físicamente el registro de un cliente de la base de datos.")
    @ApiResponse(responseCode = "204", description = "Cliente eliminado exitosamente")
    @ApiResponse(responseCode = "404", description = "El cliente no existe",
            content = @Content(schema = @Schema(implementation = ErrorRecursoNoEncontrado.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarCliente(
            @Parameter(description = "Identificador del cliente", example = "1") @PathVariable Long id) {
        clienteService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}

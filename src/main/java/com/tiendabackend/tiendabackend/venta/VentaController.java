package com.tiendabackend.tiendabackend.venta;

import com.tiendabackend.tiendabackend.common.openapi.ErrorRecursoNoEncontrado;
import com.tiendabackend.tiendabackend.common.openapi.ErrorSimple;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ventas")
@Tag(name = OpenApiConfig.TAG_VENTAS)
public class VentaController {

    private final VentaService ventaService;

    public VentaController(VentaService ventaService) {
        this.ventaService = ventaService;
    }

    @Operation(summary = "Listar ventas",
            description = "Devuelve todas las ventas registradas, en cualquier estado, con sus detalles.")
    @ApiResponse(responseCode = "200", description = "Lista de ventas (puede estar vacía)")
    @GetMapping
    public ResponseEntity<List<VentaResponseDTO>> listarVentas(){
        return ResponseEntity.ok(this.ventaService.listarTodo());
    }

    @Operation(summary = "Obtener una venta",
            description = "Devuelve una venta por su identificador, con sus detalles.")
    @ApiResponse(responseCode = "200", description = "Venta encontrada")
    @ApiResponse(responseCode = "404", description = "La venta no existe",
            content = @Content(schema = @Schema(implementation = ErrorRecursoNoEncontrado.class)))
    @GetMapping("/{id}")
    public ResponseEntity<VentaResponseDTO> obtenerVenta(
            @Parameter(description = "Identificador de la venta", example = "10") @PathVariable Long id){
        return ResponseEntity.ok(this.ventaService.obtenerPorId(id));
    }

    // Lo llama la IA/MCP: arma la venta en estado PENDIENTE.
    @Operation(summary = "Crear una venta pendiente",
            description = "La usa la IA (vía MCP) para registrar un pedido. La venta queda en estado PENDIENTE "
                    + "y el stock de cada producto se reserva (descuenta) en ese momento. El precio unitario "
                    + "y el monto los calcula el servidor a partir del precio actual de cada producto.")
    @ApiResponse(responseCode = "201", description = "Venta creada en estado PENDIENTE")
    @ApiResponse(responseCode = "400", description = "Datos inválidos (sin detalles, producto o cantidad ausentes, "
            + "cantidad menor a 1) con cuerpo `errores`, o stock insuficiente con cuerpo `error`",
            content = @Content(schema = @Schema(oneOf = {ErrorValidacion.class, ErrorSimple.class})))
    @ApiResponse(responseCode = "404", description = "Algún producto no existe o fue dado de baja",
            content = @Content(schema = @Schema(implementation = ErrorRecursoNoEncontrado.class)))
    @PostMapping
    public ResponseEntity<VentaResponseDTO> crearVenta(@Valid @RequestBody VentaRequestDTO datos){
        VentaResponseDTO ventaCreada = this.ventaService.crearVenta(datos);
        return ResponseEntity.status(HttpStatus.CREATED).body(ventaCreada);
    }

    // Lo llama el dueño (fuera del MCP), cuando confirma que ya recibio el pago.
    @Operation(summary = "Confirmar una venta",
            description = "La usa el dueño cuando confirma que recibió el pago: pasa la venta de PENDIENTE "
                    + "a COMPLETADA. No modifica el stock, que ya se reservó al crearla.")
    @ApiResponse(responseCode = "200", description = "Venta confirmada (estado COMPLETADA)")
    @ApiResponse(responseCode = "404", description = "La venta no existe",
            content = @Content(schema = @Schema(implementation = ErrorRecursoNoEncontrado.class)))
    @ApiResponse(responseCode = "409", description = "La venta no está en estado PENDIENTE",
            content = @Content(schema = @Schema(implementation = ErrorSimple.class)))
    @PatchMapping("/{id}/confirmar")
    public ResponseEntity<VentaResponseDTO> confirmarVenta(
            @Parameter(description = "Identificador de la venta", example = "10") @PathVariable Long id){
        return ResponseEntity.ok(this.ventaService.confirmarVenta(id));
    }

    // Lo llama el dueño cuando el cliente no pago: libera el stock reservado.
    @Operation(summary = "Cancelar una venta",
            description = "La usa el dueño cuando el cliente no pagó: pasa la venta de PENDIENTE a CANCELADA "
                    + "y devuelve al stock las unidades reservadas de cada producto.")
    @ApiResponse(responseCode = "200", description = "Venta cancelada (estado CANCELADA) y stock repuesto")
    @ApiResponse(responseCode = "404", description = "La venta no existe",
            content = @Content(schema = @Schema(implementation = ErrorRecursoNoEncontrado.class)))
    @ApiResponse(responseCode = "409", description = "La venta no está en estado PENDIENTE",
            content = @Content(schema = @Schema(implementation = ErrorSimple.class)))
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<VentaResponseDTO> cancelarVenta(
            @Parameter(description = "Identificador de la venta", example = "10") @PathVariable Long id){
        return ResponseEntity.ok(this.ventaService.cancelarVenta(id));
    }

}

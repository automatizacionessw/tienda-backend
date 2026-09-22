package com.tiendabackend.tiendabackend.venta;

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
public class VentaController {

    private final VentaService ventaService;

    public VentaController(VentaService ventaService) {
        this.ventaService = ventaService;
    }

    @GetMapping
    public ResponseEntity<List<VentaResponseDTO>> listarVentas(){
        return ResponseEntity.ok(this.ventaService.listarTodo());
    }

    @GetMapping("/{id}")
    public ResponseEntity<VentaResponseDTO> obtenerVenta(@PathVariable Long id){
        return ResponseEntity.ok(this.ventaService.obtenerPorId(id));
    }

    // Lo llama la IA/MCP: arma la venta en estado PENDIENTE.
    @PostMapping
    public ResponseEntity<VentaResponseDTO> crearVenta(@Valid @RequestBody VentaRequestDTO datos){
        VentaResponseDTO ventaCreada = this.ventaService.crearVenta(datos);
        return ResponseEntity.status(HttpStatus.CREATED).body(ventaCreada);
    }

    // Lo llama el dueño (fuera del MCP), cuando confirma que ya recibio el pago.
    @PatchMapping("/{id}/confirmar")
    public ResponseEntity<VentaResponseDTO> confirmarVenta(@PathVariable Long id){
        return ResponseEntity.ok(this.ventaService.confirmarVenta(id));
    }

    // Lo llama el dueño cuando el cliente no pago: libera el stock reservado.
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<VentaResponseDTO> cancelarVenta(@PathVariable Long id){
        return ResponseEntity.ok(this.ventaService.cancelarVenta(id));
    }

}
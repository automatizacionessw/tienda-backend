package com.tiendabackend.tiendabackend.venta;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Venta registrada con sus detalles")
public class VentaResponseDTO {

    @Schema(description = "Identificador de la venta", example = "10")
    private Long id;
    @Schema(description = "Fecha y hora de creación (hora local del servidor, ISO-8601 sin zona)",
            example = "2026-09-27T15:30:00.123")
    private LocalDateTime fecha;
    @Schema(description = "Estado de la venta", example = "PENDIENTE",
            allowableValues = {"PENDIENTE", "COMPLETADA", "CANCELADA"})
    private String estado;
    @Schema(description = "Total de la venta: suma de los subtotales, calculada por el servidor", example = "179.80")
    private Double monto;
    @Schema(description = "Indicador de registro activo", example = "true")
    private Boolean activo;
    @Schema(description = "Líneas de la venta")
    private List<DetalleResponseDTO> detalles;

    public static VentaResponseDTO desdeEntidad(Venta venta) {
        List<DetalleResponseDTO> detalles = venta.getDetalles().stream()
                .map(DetalleResponseDTO::desdeEntidad)
                .toList();

        return new VentaResponseDTO(
                venta.getId(),
                venta.getFecha(),
                venta.getEstado(),
                venta.getMonto(),
                venta.getActivo(),
                detalles
        );
    }
}
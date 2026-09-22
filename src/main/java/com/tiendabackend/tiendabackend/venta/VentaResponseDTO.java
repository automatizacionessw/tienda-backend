package com.tiendabackend.tiendabackend.venta;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VentaResponseDTO {

    private Long id;
    private LocalDateTime fecha;
    private String estado;
    private Double monto;
    private Boolean activo;
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
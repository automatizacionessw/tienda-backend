package com.tiendabackend.tiendabackend.venta;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetalleResponseDTO {

    private Long id;
    private Long productoId;
    private String nombreProducto;
    private Integer cantidad;
    private Double precioUnitario;
    private Double subtotal;

    // Ahora lee productoId y nombreProducto navegando la relacion JPA
    // (detalle.getProducto()...). Esto requiere que la sesion de Hibernate
    // siga abierta al momento de mapear (ver @Transactional en VentaService).
    public static DetalleResponseDTO desdeEntidad(Detalle detalle) {
        return new DetalleResponseDTO(
                detalle.getId(),
                detalle.getProducto().getId(),
                detalle.getProducto().getNombre(),
                detalle.getCantidad(),
                detalle.getPrecioUnitario(),
                detalle.getCantidad() * detalle.getPrecioUnitario()
        );
    }
}
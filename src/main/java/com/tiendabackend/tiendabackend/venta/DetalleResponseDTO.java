package com.tiendabackend.tiendabackend.venta;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Línea de una venta registrada")
public class DetalleResponseDTO {

    @Schema(description = "Identificador del detalle", example = "25")
    private Long id;
    @Schema(description = "Identificador del producto vendido", example = "1")
    private Long productoId;
    @Schema(description = "Nombre del producto vendido", example = "Camiseta básica talla M")
    private String nombreProducto;
    @Schema(description = "Unidades vendidas", example = "2")
    private Integer cantidad;
    @Schema(description = "Precio del producto al momento de la venta, fijado por el servidor", example = "89.90")
    private Double precioUnitario;
    @Schema(description = "cantidad x precioUnitario, calculado por el servidor", example = "179.80")
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
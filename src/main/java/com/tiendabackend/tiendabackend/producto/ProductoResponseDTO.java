package com.tiendabackend.tiendabackend.producto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Producto del catálogo")
public class ProductoResponseDTO {

    @Schema(description = "Identificador del producto", example = "1")
    private Long id;
    @Schema(description = "Nombre del producto", example = "Camiseta básica talla M")
    private String nombre;
    @Schema(description = "Precio unitario de venta", example = "89.90")
    private Double precio;
    @Schema(description = "Unidades disponibles, ya descontadas las reservadas por ventas pendientes", example = "25")
    private Integer stock;
    @Schema(description = "true si el producto está activo; los dados de baja no se devuelven", example = "true")
    private Boolean estado;

    public static ProductoResponseDTO desdeEntidad(Producto producto){
        return new ProductoResponseDTO(
                producto.getId(),
                producto.getNombre(),
                producto.getPrecio(),
                producto.getStock(),
                producto.getEstado()
        );
    }
}
package com.tiendabackend.tiendabackend.venta;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos para crear una venta. La fecha, el estado y el monto los calcula el servidor")
public class VentaRequestDTO {

    @Schema(description = "Productos y cantidades que componen la venta")
    @NotEmpty(message = "La venta debe tener al menos un detalle")
    @Valid
    private List<DetalleRequestDTO> detalles;

    // fecha, estado, monto y activo NO se piden: fecha y estado los fija el
    // Service, y monto se calcula sumando cantidad*precioUnitario de cada
    // detalle (igual que en Producto, donde id/estado tampoco vienen del
    // cliente).
}
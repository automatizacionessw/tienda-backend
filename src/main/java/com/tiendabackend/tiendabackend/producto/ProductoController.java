package com.tiendabackend.tiendabackend.producto;

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
@RequestMapping("/api/productos")
@Tag(name = OpenApiConfig.TAG_PRODUCTOS)
public class ProductoController {
    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @Operation(summary = "Listar productos activos",
            description = "Devuelve todos los productos del catálogo que no fueron dados de baja.")
    @ApiResponse(responseCode = "200", description = "Lista de productos activos (puede estar vacía)")
    @GetMapping
    public ResponseEntity<List<ProductoResponseDTO>> listarProductos(){
        return ResponseEntity.ok(this.productoService.listarTodo());
    }

    @Operation(summary = "Obtener un producto",
            description = "Devuelve un producto activo por su identificador.")
    @ApiResponse(responseCode = "200", description = "Producto encontrado")
    @ApiResponse(responseCode = "404", description = "El producto no existe o fue dado de baja",
            content = @Content(schema = @Schema(implementation = ErrorRecursoNoEncontrado.class)))
    @GetMapping("/{id}")
    public ResponseEntity<ProductoResponseDTO> obtenerProducto(
            @Parameter(description = "Identificador del producto", example = "1") @PathVariable Long id){
        return ResponseEntity.ok(this.productoService.obtenerPorId(id));
    }

    @Operation(summary = "Crear un producto",
            description = "Registra un producto nuevo en el catálogo, activo desde su creación.")
    @ApiResponse(responseCode = "201", description = "Producto creado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos: nombre vacío, o precio o stock ausentes o negativos",
            content = @Content(schema = @Schema(implementation = ErrorValidacion.class)))
    @PostMapping
    public ResponseEntity<ProductoResponseDTO> crearProducto(@Valid@RequestBody ProductoRequestDTO datos){
        ProductoResponseDTO productoCreado = this.productoService.crearProducto(datos);
        return ResponseEntity.status(HttpStatus.CREATED).body(productoCreado);
    }

    @Operation(summary = "Actualizar un producto",
            description = "Reemplaza el nombre, el precio y el stock de un producto activo.")
    @ApiResponse(responseCode = "200", description = "Producto actualizado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos: nombre vacío, o precio o stock ausentes o negativos",
            content = @Content(schema = @Schema(implementation = ErrorValidacion.class)))
    @ApiResponse(responseCode = "404", description = "El producto no existe o fue dado de baja",
            content = @Content(schema = @Schema(implementation = ErrorRecursoNoEncontrado.class)))
    @PutMapping("/{id}")
    public ResponseEntity<ProductoResponseDTO> actualizarProducto(
            @Parameter(description = "Identificador del producto", example = "1") @PathVariable Long id,
            @Valid @RequestBody ProductoRequestDTO datos ){
        return ResponseEntity.ok(this.productoService.actualizarProducto(id,datos));
    }

    @Operation(summary = "Dar de baja un producto",
            description = "Baja lógica: el producto deja de listarse y de poder venderse, pero se conserva "
                    + "en las ventas que ya lo incluyen.")
    @ApiResponse(responseCode = "204", description = "Producto dado de baja")
    @ApiResponse(responseCode = "404", description = "El producto no existe o ya fue dado de baja",
            content = @Content(schema = @Schema(implementation = ErrorRecursoNoEncontrado.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarProducto(
            @Parameter(description = "Identificador del producto", example = "1") @PathVariable Long id){
        this.productoService.eliminar(id);
        return ResponseEntity.noContent().build();

    }

}


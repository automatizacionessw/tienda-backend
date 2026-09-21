package com.tiendabackend.tiendabackend.producto;

import com.tiendabackend.tiendabackend.common.exception.RecursoNoEncontradoException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProductoService {
    private final ProductoRepository productoRepository;

    public ProductoService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    public List<ProductoResponseDTO> listarTodo(){
        return this.productoRepository.findByEstadoTrue().stream()
                .map(ProductoResponseDTO::desdeEntidad).toList();
    }

    public ProductoResponseDTO obtenerPorId(long id){
        Producto producto = buscarOFallar(id);
        return ProductoResponseDTO.desdeEntidad(producto);
    }

    public ProductoResponseDTO crearProducto(ProductoRequestDTO datos){
        Producto producto = new Producto();
                producto.setNombre(datos.getNombre());
                producto.setPrecio(datos.getPrecio());
                producto.setStock(datos.getStock());
                producto.setEstado(true);

        Producto guardado = this.productoRepository.save(producto);
        return  ProductoResponseDTO.desdeEntidad(guardado);
    }

    public ProductoResponseDTO actualizarProducto(Long id , ProductoRequestDTO datos){
        Producto producto = buscarOFallar(id);
        producto.setNombre(datos.getNombre());
        producto.setPrecio(datos.getPrecio());
        producto.setStock(datos.getStock());

        Producto actualizado = this.productoRepository.save(producto);
        return ProductoResponseDTO.desdeEntidad(actualizado);
    }

    public void eliminar(Long id){
        Producto producto = buscarOFallar(id);
        producto.setEstado(false);
        productoRepository.save(producto);
    }

    private Producto buscarOFallar(Long id){
        return this.productoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto",id));
    }
}
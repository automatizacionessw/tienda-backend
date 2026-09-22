package com.tiendabackend.tiendabackend.producto;

import com.tiendabackend.tiendabackend.common.exception.RecursoNoEncontradoException;
import java.util.List;

import com.tiendabackend.tiendabackend.common.exception.StockInsuficienteException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Service
public class ProductoService {
    private final ProductoRepository productoRepository;

    public ProductoService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductoResponseDTO> listarTodo(){
        return this.productoRepository.findByEstadoTrue().stream()
                .map(ProductoResponseDTO::desdeEntidad).toList();
    }

    @Transactional(readOnly = true)
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
    @Transactional
    public ProductoResponseDTO actualizarProducto(Long id , ProductoRequestDTO datos){
        Producto producto = buscarOFallar(id);
        producto.setNombre(datos.getNombre());
        producto.setPrecio(datos.getPrecio());
        producto.setStock(datos.getStock());

        Producto actualizado = this.productoRepository.save(producto);
        return ProductoResponseDTO.desdeEntidad(actualizado);
    }
    @Transactional
    public void eliminar(Long id){
        Producto producto = buscarOFallar(id);
        producto.setEstado(false);
        productoRepository.save(producto);
    }

    @Transactional
    public void descontarStock(Long idProducto, Integer cantidad){
        Producto producto = buscarOFallar(idProducto);
        if (cantidad > producto.getStock()){
            throw new StockInsuficienteException(producto.getNombre(), producto.getStock(), cantidad);
        }
        Integer stockDescontado = producto.getStock()-cantidad;
        producto.setStock(stockDescontado);
        this.productoRepository.save(producto);
    }

    @Transactional
    public void reponerStock(Long idProducto, Integer cantidad){
        Producto producto = buscarOFallar(idProducto);
        producto.setStock(producto.getStock() + cantidad);
        this.productoRepository.save(producto);
    }

    private Producto buscarOFallar(Long id){
        Producto producto = this.productoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto",id));
        if (Boolean.FALSE.equals(producto.getEstado())){
            throw new RecursoNoEncontradoException("Producto", id);
        }
        return producto;
    }
}
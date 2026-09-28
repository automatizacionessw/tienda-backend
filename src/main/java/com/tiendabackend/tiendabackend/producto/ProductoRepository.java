package com.tiendabackend.tiendabackend.producto;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoRepository extends JpaRepository<Producto,Long>{

    // busqueda parcial e insensible a mayusculas : asi el bot puede
    // encontrar "audifonos xyz" aunque el cliente escriba Audifonos xyz

    List<Producto> findByNombreContainingIgnoreCase(String nombre);

    List<Producto> findByEstadoTrue();
}

package com.tiendabackend.tiendabackend.venta;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "venta")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime fecha;


    @Column(nullable = false)
    private String estado;

    @Column(nullable = false)
    private Double monto;

    @Column(nullable = false)
    private Boolean activo = true;

    // Venta y Detalle son del MISMO modulo, asi que la relacion JPA directa
    // entre ellas si respeta la arquitectura (la regla 3 solo prohibe cruzar
    // Entities/Repositories ENTRE modulos distintos).
    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Detalle> detalles = new ArrayList<>();
}
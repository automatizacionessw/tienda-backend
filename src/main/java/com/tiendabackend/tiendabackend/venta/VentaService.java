package com.tiendabackend.tiendabackend.venta;

import com.tiendabackend.tiendabackend.common.exception.EstadoVentaInvalidoException;
import com.tiendabackend.tiendabackend.common.exception.RecursoNoEncontradoException;
import com.tiendabackend.tiendabackend.producto.Producto;
import com.tiendabackend.tiendabackend.producto.ProductoResponseDTO;
import com.tiendabackend.tiendabackend.producto.ProductoService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VentaService {

    private static final String ESTADO_PENDIENTE = "PENDIENTE";
    private static final String ESTADO_COMPLETADA = "COMPLETADA";
    private static final String ESTADO_CANCELADA = "CANCELADA";

    private final VentaRepository ventaRepository;
    private final ProductoService productoService;

    @PersistenceContext
    private EntityManager entityManager;

    public VentaService(VentaRepository ventaRepository, ProductoService productoService) {
        this.ventaRepository = ventaRepository;
        this.productoService = productoService;
    }

    @Transactional(readOnly = true)
    public List<VentaResponseDTO> listarTodo(){
        return this.ventaRepository.findAll().stream()
                .map(VentaResponseDTO::desdeEntidad).toList();
    }

    @Transactional(readOnly = true)
    public VentaResponseDTO obtenerPorId(Long id){
        Venta venta = buscarOFallar(id);
        return VentaResponseDTO.desdeEntidad(venta);
    }

    // La venta nace PENDIENTE: la IA/MCP arma el pedido y descuenta (reserva)
    // el stock, pero el pago todavia no ocurrio. El dueño confirma o cancela
    // despues, fuera del alcance del MCP.
    @Transactional
    public VentaResponseDTO crearVenta(VentaRequestDTO datos){
        Venta venta = new Venta();
        venta.setFecha(LocalDateTime.now());
        venta.setEstado(ESTADO_PENDIENTE);
        venta.setActivo(true);
        venta.setMonto(0.0);

        double montoTotal = 0.0;

        for (DetalleRequestDTO detalleDatos : datos.getDetalles()) {
            ProductoResponseDTO producto = productoService.obtenerPorId(detalleDatos.getProductoId());

            // Se reserva el stock ya en este momento (aunque la venta quede
            // pendiente), para que dos ventas pendientes no puedan vender lo
            // mismo dos veces mientras se espera el pago de la primera.
            productoService.descontarStock(detalleDatos.getProductoId(), detalleDatos.getCantidad());

            Producto productoRef = entityManager.getReference(Producto.class, detalleDatos.getProductoId());

            Detalle detalle = new Detalle();
            detalle.setProducto(productoRef);
            detalle.setCantidad(detalleDatos.getCantidad());
            detalle.setPrecioUnitario(producto.getPrecio());
            detalle.setVenta(venta);

            venta.getDetalles().add(detalle);

            montoTotal += detalleDatos.getCantidad() * producto.getPrecio();
        }

        venta.setMonto(montoTotal);

        Venta guardada = this.ventaRepository.save(venta);
        return VentaResponseDTO.desdeEntidad(guardada);
    }

    // La usa el dueño del negocio (fuera del MCP) cuando confirma que el
    // cliente ya pago. No toca stock: ya se descontó/reservó al crear la venta.
    @Transactional
    public VentaResponseDTO confirmarVenta(Long id){
        Venta venta = buscarOFallar(id);

        if (!ESTADO_PENDIENTE.equals(venta.getEstado())) {
            throw new EstadoVentaInvalidoException(
                    "Solo se puede confirmar una venta en estado PENDIENTE (actual: " + venta.getEstado() + ")");
        }

        venta.setEstado(ESTADO_COMPLETADA);
        Venta actualizada = this.ventaRepository.save(venta);
        return VentaResponseDTO.desdeEntidad(actualizada);
    }

    // El cliente no pago / se arrepintio: se cancela la venta y se devuelve
    // el stock reservado a cada producto involucrado.
    @Transactional
    public VentaResponseDTO cancelarVenta(Long id){
        Venta venta = buscarOFallar(id);

        if (!ESTADO_PENDIENTE.equals(venta.getEstado())) {
            throw new EstadoVentaInvalidoException(
                    "Solo se puede cancelar una venta en estado PENDIENTE (actual: " + venta.getEstado() + ")");
        }

        for (Detalle detalle : venta.getDetalles()) {
            productoService.reponerStock(detalle.getProducto().getId(), detalle.getCantidad());
        }

        venta.setEstado(ESTADO_CANCELADA);
        Venta actualizada = this.ventaRepository.save(venta);
        return VentaResponseDTO.desdeEntidad(actualizada);
    }

    private Venta buscarOFallar(Long id){
        return this.ventaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Venta", id));
    }
}
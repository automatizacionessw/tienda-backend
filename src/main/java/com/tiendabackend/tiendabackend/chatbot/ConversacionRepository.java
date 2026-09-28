package com.tiendabackend.tiendabackend.chatbot;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversacionRepository extends JpaRepository<Conversacion, Long> {

    // Conversacion ABIERTA del cliente (puede estar vencida). El bloqueo
    // serializa la asignacion de mensajes con las operaciones de la API.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Conversacion> findByClienteAbierta(Long clienteAbierta);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversacion c where c.id = :id")
    Optional<Conversacion> findByIdParaActualizar(@Param("id") Long id);

    // Replica en JPQL la regla de Conversacion.estaVigente con
    // limite = ahora - inactividad: vigente si ABIERTA y ultimo mensaje > limite.
    // Se usan banderas en vez de parametros null para no depender de la
    // inferencia de tipos de parametros null en PostgreSQL.
    @Query("""
            select c from Conversacion c
            where (:filtrarCliente = false or c.clienteId = :clienteId)
              and (:soloVigentes = false
                   or (c.estado = :abierta and c.fechaUltimoMensaje > :limite))
              and (:soloNoVigentes = false
                   or c.estado <> :abierta or c.fechaUltimoMensaje <= :limite)
              and (:sinClasificar = false
                   or not exists (select i.id from ConversacionIntencion i where i.conversacion = c))
            order by c.fechaUltimoMensaje desc, c.id desc
            """)
    List<Conversacion> buscar(@Param("filtrarCliente") boolean filtrarCliente,
                              @Param("clienteId") long clienteId,
                              @Param("soloVigentes") boolean soloVigentes,
                              @Param("soloNoVigentes") boolean soloNoVigentes,
                              @Param("sinClasificar") boolean sinClasificar,
                              @Param("abierta") EstadoConversacion abierta,
                              @Param("limite") Instant limite);
}

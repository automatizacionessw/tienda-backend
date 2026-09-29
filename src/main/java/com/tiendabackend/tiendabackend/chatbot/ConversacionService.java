package com.tiendabackend.tiendabackend.chatbot;

import com.tiendabackend.tiendabackend.common.exception.EstadoConversacionInvalidoException;
import com.tiendabackend.tiendabackend.common.exception.RecursoNoEncontradoException;
import com.tiendabackend.tiendabackend.common.exception.SolicitudInvalidaException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// Ciclo de vida de las conversaciones y sus intenciones. No depende del bot:
// la API REST lo usa aunque telegram.bot.habilitado sea false.
@Service
public class ConversacionService {

    private static final List<MotivoCierre> MOTIVOS_CIERRE_API =
            List.of(MotivoCierre.INTENCION_RESUELTA, MotivoCierre.MANUAL);

    private final ConversacionRepository conversacionRepository;
    private final ConversacionIntencionRepository intencionRepository;
    private final MensajeRepository mensajeRepository;
    private final Duration inactividad;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public ConversacionService(ConversacionRepository conversacionRepository,
                               ConversacionIntencionRepository intencionRepository,
                               MensajeRepository mensajeRepository,
                               ConversacionProperties properties,
                               Clock clock,
                               PlatformTransactionManager transactionManager) {
        this.conversacionRepository = conversacionRepository;
        this.intencionRepository = intencionRepository;
        this.mensajeRepository = mensajeRepository;
        this.inactividad = properties.inactividad();
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // Conversacion vigente del cliente para un mensaje entrante, o una nueva.
    // Si dos mensajes de un cliente sin conversacion llegan a la vez, ambos
    // intentan abrir una y uno choca con la UQ de cliente_abierta: ese
    // reintenta una vez, en una transaccion nueva, y encuentra la del otro.
    // Por eso se usa TransactionTemplate: el reintento ocurre FUERA de la fallida.
    public Conversacion asignarEntrante(Long clienteId, Instant fechaMensaje) {
        try {
            return transactionTemplate.execute(estado -> asignar(clienteId, fechaMensaje));
        } catch (DataIntegrityViolationException colision) {
            return transactionTemplate.execute(estado -> asignar(clienteId, fechaMensaje));
        }
    }

    private Conversacion asignar(Long clienteId, Instant fechaMensaje) {
        Conversacion abierta = conversacionRepository.findByClienteAbierta(clienteId).orElse(null);
        if (abierta != null) {
            if (abierta.estaVigente(fechaMensaje, inactividad)) {
                abierta.registrarActividad(fechaMensaje);
                return conversacionRepository.save(abierta);
            }
            abierta.cerrarPorInactividad(inactividad);
            // Hibernate ejecuta los INSERT antes que los UPDATE: sin este flush
            // la nueva chocaria con la vieja en cliente_abierta.
            conversacionRepository.saveAndFlush(abierta);
        }
        return conversacionRepository.saveAndFlush(Conversacion.abrir(clienteId, fechaMensaje));
    }

    // Se invoca dentro de la transaccion que guarda el saliente. Solo mueve la
    // actividad de una conversacion ABIERTA (pudo cerrarse mientras se respondia).
    @Transactional
    public void registrarSaliente(Long conversacionId, Instant fechaMensaje) {
        conversacionRepository.findByIdParaActualizar(conversacionId)
                .filter(c -> c.getEstado() == EstadoConversacion.ABIERTA)
                .ifPresent(c -> {
                    c.registrarActividad(fechaMensaje);
                    conversacionRepository.save(c);
                });
    }

    @Transactional(readOnly = true)
    public List<ConversacionResponseDTO> listar(Long clienteId, String estado, boolean sinClasificar) {
        EstadoConversacion filtro = estado == null || estado.isBlank()
                ? null
                : parsear(EstadoConversacion.class, estado, "estado", List.of(EstadoConversacion.values()));
        Instant ahora = clock.instant();
        return conversacionRepository.buscar(
                        clienteId != null,
                        clienteId != null ? clienteId : 0L,
                        filtro == EstadoConversacion.ABIERTA,
                        filtro == EstadoConversacion.CERRADA,
                        sinClasificar,
                        EstadoConversacion.ABIERTA,
                        ahora.minus(inactividad))
                .stream()
                .map(c -> ConversacionResponseDTO.desdeEntidad(c, c.estadoEfectivo(ahora, inactividad)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversacionDetalleResponseDTO obtenerDetalle(Long id) {
        Conversacion conversacion = conversacionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Conversación", id));
        List<Mensaje> mensajes = mensajeRepository.findByConversacionIdOrderByFechaTelegramAscIdAsc(id);
        return ConversacionDetalleResponseDTO.desdeEntidad(
                conversacion, conversacion.estadoEfectivo(clock.instant(), inactividad), mensajes);
    }

    @Transactional
    public IntencionResponseDTO registrarIntencion(Long conversacionId, IntencionRequestDTO datos) {
        Intencion intencion = parsear(Intencion.class, datos.getIntencion(), "intencion",
                List.of(Intencion.values()));
        OrigenClasificacion origen = datos.getOrigen() == null || datos.getOrigen().isBlank()
                ? OrigenClasificacion.MANUAL
                : parsear(OrigenClasificacion.class, datos.getOrigen(), "origen",
                        List.of(OrigenClasificacion.values()));
        String detalle = datos.getDetalle() == null || datos.getDetalle().isBlank() ? null : datos.getDetalle().trim();
        if (detalle != null && detalle.length() > ConversacionIntencion.DETALLE_MAX) {
            throw new SolicitudInvalidaException(
                    "El detalle admite hasta " + ConversacionIntencion.DETALLE_MAX + " caracteres");
        }
        if (intencion == Intencion.OTRA && detalle == null) {
            throw new SolicitudInvalidaException(
                    "La intención OTRA requiere un detalle que describa qué pidió el cliente");
        }

        Conversacion conversacion = vigenteParaActualizar(conversacionId);

        Mensaje mensaje = null;
        if (datos.getMensajeId() != null) {
            mensaje = mensajeRepository.findById(datos.getMensajeId())
                    .filter(m -> m.getDireccion() == Direccion.ENTRANTE
                            && conversacionId.equals(m.getConversacion().getId()))
                    .orElseThrow(() -> new SolicitudInvalidaException("El mensaje " + datos.getMensajeId()
                            + " no es un mensaje entrante de la conversación " + conversacionId));
        }

        boolean pendienteDelMismoTipo = conversacion.getIntenciones().stream()
                .anyMatch(i -> i.getIntencion() == intencion && i.getEstado() == EstadoIntencion.PENDIENTE);
        if (pendienteDelMismoTipo) {
            throw new EstadoConversacionInvalidoException("La conversación " + conversacionId
                    + " ya tiene una intención " + intencion + " pendiente; resuélvala antes de registrarla otra vez");
        }

        ConversacionIntencion nueva = new ConversacionIntencion();
        nueva.setConversacion(conversacion);
        nueva.setIntencion(intencion);
        nueva.setEstado(EstadoIntencion.PENDIENTE);
        nueva.setDetalle(detalle);
        nueva.setMensaje(mensaje);
        nueva.setOrigen(origen);
        nueva.setFechaDeteccion(clock.instant());
        ConversacionIntencion guardada = intencionRepository.save(nueva);
        conversacion.getIntenciones().add(guardada);
        return IntencionResponseDTO.desdeEntidad(guardada);
    }

    @Transactional
    public IntencionResponseDTO resolverIntencion(Long conversacionId, Long intencionId) {
        Conversacion conversacion = vigenteParaActualizar(conversacionId);
        ConversacionIntencion intencion = conversacion.getIntenciones().stream()
                .filter(i -> i.getId().equals(intencionId))
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("Intención", intencionId));
        if (intencion.getEstado() == EstadoIntencion.RESUELTA) {
            throw new EstadoConversacionInvalidoException("La intención " + intencionId + " ya está RESUELTA");
        }
        intencion.setEstado(EstadoIntencion.RESUELTA);
        intencion.setFechaResolucion(clock.instant());
        return IntencionResponseDTO.desdeEntidad(intencionRepository.save(intencion));
    }

    @Transactional
    public ConversacionResponseDTO cerrar(Long conversacionId, CierreRequestDTO datos) {
        MotivoCierre motivo = parsear(MotivoCierre.class, datos.getMotivo(), "motivo", MOTIVOS_CIERRE_API);
        Conversacion conversacion = vigenteParaActualizar(conversacionId);

        if (motivo == MotivoCierre.INTENCION_RESUELTA) {
            if (conversacion.getIntenciones().isEmpty()) {
                throw new EstadoConversacionInvalidoException("La conversación " + conversacionId
                        + " no tiene intenciones registradas; no se puede cerrar con INTENCION_RESUELTA");
            }
            String pendientes = conversacion.getIntenciones().stream()
                    .filter(i -> i.getEstado() == EstadoIntencion.PENDIENTE)
                    .map(i -> i.getIntencion() + " (id " + i.getId() + ")")
                    .collect(Collectors.joining(", "));
            if (!pendientes.isEmpty()) {
                throw new EstadoConversacionInvalidoException("La conversación " + conversacionId
                        + " tiene intenciones pendientes: " + pendientes
                        + "; resuélvalas o cierre con MANUAL");
            }
        }

        Instant ahora = clock.instant();
        conversacion.cerrar(motivo, ahora);
        Conversacion guardada = conversacionRepository.save(conversacion);
        return ConversacionResponseDTO.desdeEntidad(guardada, guardada.estadoEfectivo(ahora, inactividad));
    }

    // Toma la conversacion con bloqueo (serializa las operaciones de la API
    // entre si y con asignarEntrante) y exige que siga vigente. Una vencida
    // responde 409 sin persistir su cierre: solo el ingreso lo persiste.
    private Conversacion vigenteParaActualizar(Long conversacionId) {
        Conversacion conversacion = conversacionRepository.findByIdParaActualizar(conversacionId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Conversación", conversacionId));
        if (!conversacion.estaVigente(clock.instant(), inactividad)) {
            Conversacion.EstadoEfectivo efectivo = conversacion.estadoEfectivo(clock.instant(), inactividad);
            throw new EstadoConversacionInvalidoException("La conversación " + conversacionId
                    + " está cerrada (" + efectivo.motivoCierre() + ") y no admite cambios");
        }
        return conversacion;
    }

    private static <E extends Enum<E>> E parsear(Class<E> tipo, String valor, String campo, List<E> permitidos) {
        String permitidosTexto = permitidos.stream().map(Enum::name).collect(Collectors.joining(", "));
        if (valor == null || valor.isBlank()) {
            throw new SolicitudInvalidaException("Falta " + campo + ". Valores permitidos: " + permitidosTexto);
        }
        String normalizado = valor.trim().toUpperCase();
        return Arrays.stream(tipo.getEnumConstants())
                .filter(permitidos::contains)
                .filter(e -> e.name().equals(normalizado))
                .findFirst()
                .orElseThrow(() -> new SolicitudInvalidaException("Valor inválido para " + campo + ": '"
                        + valor + "'. Valores permitidos: " + permitidosTexto));
    }
}

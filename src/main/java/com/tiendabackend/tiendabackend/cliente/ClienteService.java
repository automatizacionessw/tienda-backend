package com.tiendabackend.tiendabackend.cliente;

import com.tiendabackend.tiendabackend.common.exception.RecursoNoEncontradoException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final TransactionTemplate transactionTemplate;

    public ClienteService(ClienteRepository clienteRepository, PlatformTransactionManager transactionManager) {
        this.clienteRepository = clienteRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public List<ClienteDTO> listarTodo() {
        return clienteRepository.findAll().stream()
                .map(ClienteDTO::desdeEntidad)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ClienteDTO obtenerPorId(Long id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", id));
        return ClienteDTO.desdeEntidad(cliente);
    }

    @Transactional
    public ClienteDTO crearCliente(ClienteRequestDTO dto) {
        Instant ahora = Instant.now();
        Cliente cliente = new Cliente();
        cliente.setTelegramUserId(dto.getTelegramUserId());
        cliente.setUsername(dto.getUsername());
        cliente.setNombre(dto.getNombre());
        cliente.setApellido(dto.getApellido());
        cliente.setIdioma(dto.getIdioma() != null ? dto.getIdioma() : "es");
        cliente.setFechaAlta(ahora);
        cliente.setFechaActualizacion(ahora);

        Cliente guardado = clienteRepository.save(cliente);
        return ClienteDTO.desdeEntidad(guardado);
    }

    @Transactional
    public ClienteDTO actualizarCliente(Long id, ClienteRequestDTO dto) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente", id));

        cliente.setTelegramUserId(dto.getTelegramUserId());
        cliente.setUsername(dto.getUsername());
        cliente.setNombre(dto.getNombre());
        cliente.setApellido(dto.getApellido());
        cliente.setIdioma(dto.getIdioma());
        cliente.setFechaActualizacion(Instant.now());

        Cliente guardado = clienteRepository.save(cliente);
        return ClienteDTO.desdeEntidad(guardado);
    }

    @Transactional
    public void eliminar(Long id) {
        if (!clienteRepository.existsById(id)) {
            throw new RecursoNoEncontradoException("Cliente", id);
        }
        clienteRepository.deleteById(id);
    }

    // Alta o actualizacion del cliente a partir de su perfil de Telegram.
    public ClienteDTO registrarOActualizarDesdeTelegram(DatosClienteTelegram datos) {
        if (datos == null || datos.getTelegramUserId() == null) {
            throw new IllegalArgumentException("El id de usuario de Telegram es obligatorio");
        }
        try {
            return transactionTemplate.execute(estado -> upsert(datos));
        } catch (DataIntegrityViolationException colision) {
            return transactionTemplate.execute(estado -> upsert(datos));
        }
    }

    private ClienteDTO upsert(DatosClienteTelegram datos) {
        Instant ahora = Instant.now();
        Cliente cliente = clienteRepository.findByTelegramUserId(datos.getTelegramUserId())
                .orElseGet(() -> {
                    Cliente nuevo = new Cliente();
                    nuevo.setTelegramUserId(datos.getTelegramUserId());
                    nuevo.setFechaAlta(ahora);
                    return nuevo;
                });

        cliente.setUsername(datos.getUsername());
        cliente.setNombre(datos.getNombre() != null ? datos.getNombre() : "");
        cliente.setApellido(datos.getApellido());
        cliente.setIdioma(datos.getIdioma());
        cliente.setFechaActualizacion(ahora);

        Cliente guardado = clienteRepository.saveAndFlush(cliente);
        return ClienteDTO.desdeEntidad(guardado);
    }
}

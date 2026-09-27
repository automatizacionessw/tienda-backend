package com.tiendabackend.tiendabackend.cliente;

import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final TransactionTemplate transactionTemplate;

    public ClienteService(ClienteRepository clienteRepository, PlatformTransactionManager transactionManager) {
        this.clienteRepository = clienteRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // Alta o actualizacion del cliente a partir de su perfil de Telegram.
    // Si dos mensajes de un usuario nuevo se procesan a la vez, ambos intentan
    // el INSERT y uno choca con la UQ de telegram_user_id: ese reintenta una
    // vez, ya en una transaccion nueva, y encuentra el cliente recien creado.
    // Por eso las transacciones se manejan con TransactionTemplate y no con
    // @Transactional: el reintento tiene que ocurrir FUERA de la fallida.
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

        // saveAndFlush fuerza el INSERT dentro de la transaccion, para que una
        // colision con la UQ se detecte aqui y no recien en el commit.
        Cliente guardado = clienteRepository.saveAndFlush(cliente);
        return ClienteDTO.desdeEntidad(guardado);
    }
}

package com.tiendabackend.tiendabackend.cliente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ClienteServiceTest {

    private ClienteRepository clienteRepository;
    private PlatformTransactionManager transactionManager;
    private ClienteService clienteService;

    @BeforeEach
    void setUp() {
        clienteRepository = mock(ClienteRepository.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenAnswer(inv -> new SimpleTransactionStatus());
        when(clienteRepository.saveAndFlush(any(Cliente.class))).thenAnswer(inv -> {
            Cliente c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(1L);
            }
            return c;
        });
        clienteService = new ClienteService(clienteRepository, transactionManager);
    }

    private static DatosClienteTelegram datos(String username) {
        return new DatosClienteTelegram(5551234567L, username, "Ana", "Pérez", "es");
    }

    @Test
    void registraUnUsuarioNuevo() {
        when(clienteRepository.findByTelegramUserId(5551234567L)).thenReturn(Optional.empty());

        ClienteDTO cliente = clienteService.registrarOActualizarDesdeTelegram(datos("ana_x"));

        assertThat(cliente.getId()).isEqualTo(1L);
        assertThat(cliente.getTelegramUserId()).isEqualTo(5551234567L);
        assertThat(cliente.getUsername()).isEqualTo("ana_x");
        assertThat(cliente.getNombre()).isEqualTo("Ana");
        assertThat(cliente.getApellido()).isEqualTo("Pérez");
        assertThat(cliente.getIdioma()).isEqualTo("es");
        assertThat(cliente.getFechaAlta()).isNotNull();
    }

    @Test
    void actualizaElUsernameConservandoIdentidadYFechaDeAlta() {
        Instant altaOriginal = Instant.parse("2026-01-01T10:00:00Z");
        Cliente existente = new Cliente(7L, 5551234567L, "juan_old", "Ana", "Pérez", "es", altaOriginal, altaOriginal);
        when(clienteRepository.findByTelegramUserId(5551234567L)).thenReturn(Optional.of(existente));

        ClienteDTO cliente = clienteService.registrarOActualizarDesdeTelegram(datos("juan_new"));

        assertThat(cliente.getId()).isEqualTo(7L);
        assertThat(cliente.getUsername()).isEqualTo("juan_new");
        assertThat(cliente.getFechaAlta()).isEqualTo(altaOriginal);
        ArgumentCaptor<Cliente> guardado = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteRepository).saveAndFlush(guardado.capture());
        assertThat(guardado.getValue().getFechaActualizacion()).isAfter(altaOriginal);
    }

    @Test
    void colisionConcurrenteReintentaYDevuelveElClienteExistente() {
        Instant alta = Instant.parse("2026-09-26T12:00:00Z");
        Cliente creadoPorOtroHilo = new Cliente(9L, 5551234567L, "ana_x", "Ana", "Pérez", "es", alta, alta);
        when(clienteRepository.findByTelegramUserId(5551234567L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(creadoPorOtroHilo));
        when(clienteRepository.saveAndFlush(any(Cliente.class)))
                .thenThrow(new DataIntegrityViolationException("uk_cliente_telegram_user_id"))
                .thenAnswer(inv -> inv.getArgument(0));

        ClienteDTO cliente = clienteService.registrarOActualizarDesdeTelegram(datos("ana_x"));

        assertThat(cliente.getId()).isEqualTo(9L);
        assertThat(cliente.getFechaAlta()).isEqualTo(alta);
        verify(clienteRepository, times(2)).saveAndFlush(any(Cliente.class));
        // la primera transaccion se revierte y el reintento abre una nueva
        verify(transactionManager).rollback(any());
        verify(transactionManager, times(2)).getTransaction(any());
    }

    @Test
    void rechazaDatosSinIdDeUsuario() {
        assertThatThrownBy(() -> clienteService.registrarOActualizarDesdeTelegram(
                new DatosClienteTelegram(null, "x", "Ana", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

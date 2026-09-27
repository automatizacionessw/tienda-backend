package com.tiendabackend.tiendabackend.cliente;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByTelegramUserId(Long telegramUserId);
}

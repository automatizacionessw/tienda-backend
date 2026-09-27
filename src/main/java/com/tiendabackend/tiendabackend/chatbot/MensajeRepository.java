package com.tiendabackend.tiendabackend.chatbot;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MensajeRepository extends JpaRepository<Mensaje, Long> {

    boolean existsByClienteIdAndTelegramMessageId(Long clienteId, Long telegramMessageId);
}

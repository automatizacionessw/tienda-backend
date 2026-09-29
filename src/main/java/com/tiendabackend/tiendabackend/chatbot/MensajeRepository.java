package com.tiendabackend.tiendabackend.chatbot;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MensajeRepository extends JpaRepository<Mensaje, Long> {

    boolean existsByClienteIdAndTelegramMessageId(Long clienteId, Long telegramMessageId);

    List<Mensaje> findByConversacionIdOrderByFechaTelegramAscIdAsc(Long conversacionId);
}

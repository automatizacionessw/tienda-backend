package com.tiendabackend.tiendabackend.chatbot;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConversacionConfig {

    // Reloj inyectable: la vigencia depende de "ahora" y los tests lo fijan.
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

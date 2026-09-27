package com.tiendabackend.tiendabackend.chatbot.telegram;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.telegram.telegrambots.meta.api.objects.Update;

// Los modelos de telegrambots usan Jackson 2 (com.fasterxml), mientras que
// Spring MVC en Boot 4 usa Jackson 3 (tools.jackson), que ignora sus
// deserializadores propios. Por eso el JSON de Telegram se parsea con un
// ObjectMapper Jackson 2 privado, que NO se registra como bean de Spring.
public final class TelegramUpdateParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TelegramUpdateParser() {
    }

    public static Update parsear(String json) throws JsonProcessingException {
        return OBJECT_MAPPER.readValue(json, Update.class);
    }
}

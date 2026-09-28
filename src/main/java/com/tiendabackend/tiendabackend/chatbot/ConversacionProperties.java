package com.tiendabackend.tiendabackend.chatbot;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Tiempo sin mensajes tras el cual una conversacion deja de estar vigente.
// Configurable (p. ej. conversacion.inactividad=1m para probar a mano).
@ConfigurationProperties(prefix = "conversacion")
public record ConversacionProperties(Duration inactividad) {

    public static final Duration INACTIVIDAD_POR_DEFECTO = Duration.ofHours(2);

    public ConversacionProperties {
        if (inactividad == null) {
            inactividad = INACTIVIDAD_POR_DEFECTO;
        }
        if (inactividad.isZero() || inactividad.isNegative()) {
            throw new IllegalStateException(
                    "La propiedad conversacion.inactividad debe ser una duracion positiva (actual: " + inactividad + ")");
        }
    }
}

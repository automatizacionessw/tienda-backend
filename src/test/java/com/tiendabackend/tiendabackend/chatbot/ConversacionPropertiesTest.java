package com.tiendabackend.tiendabackend.chatbot;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ConversacionPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(ConversacionProperties.class)
    static class Base {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Base.class);

    @Test
    void sinLaPropiedadLaInactividadEsDeDosHoras() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ConversacionProperties.class).inactividad())
                    .isEqualTo(Duration.ofHours(2));
        });
    }

    @Test
    void laInactividadSePuedeConfigurar() {
        runner.withPropertyValues("conversacion.inactividad=1m")
                .run(context -> assertThat(context.getBean(ConversacionProperties.class).inactividad())
                        .isEqualTo(Duration.ofMinutes(1)));
    }

    @Test
    void inactividadCeroFallaNombrandoLaPropiedad() {
        runner.withPropertyValues("conversacion.inactividad=0s")
                .run(context -> assertThat(context).getFailure()
                        .rootCause().hasMessageContaining("conversacion.inactividad"));
    }
}

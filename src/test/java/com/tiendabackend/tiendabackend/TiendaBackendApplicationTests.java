package com.tiendabackend.tiendabackend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// El bot se deshabilita explicitamente: el application.properties local puede
// tenerlo habilitado, y el test no debe conectarse a Telegram.
@SpringBootTest(properties = "telegram.bot.habilitado=false")
class TiendaBackendApplicationTests {

    @Test
    void contextLoads() {
    }

}

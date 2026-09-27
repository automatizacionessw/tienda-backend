package com.tiendabackend.tiendabackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TiendaBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(TiendaBackendApplication.class, args);
    }

}

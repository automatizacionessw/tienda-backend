package com.tiendabackend.tiendabackend.common.almacenamiento;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "almacenamiento.local")
public record AlmacenamientoLocalProperties(String directorio) {

    public AlmacenamientoLocalProperties {
        if (directorio == null || directorio.isBlank()) {
            directorio = "./data/archivos";
        }
    }
}

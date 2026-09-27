package com.tiendabackend.tiendabackend.common.almacenamiento;

public class ArchivoNoEncontradoException extends RuntimeException {

    public ArchivoNoEncontradoException(String clave) {
        super("No se encontro el archivo con clave: " + clave);
    }
}

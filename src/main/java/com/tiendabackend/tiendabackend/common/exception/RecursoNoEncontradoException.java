package com.tiendabackend.tiendabackend.common.exception;

public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String recurso, Long id) {

      super(recurso + " con id "+ id +" no encontrado");
    }
}

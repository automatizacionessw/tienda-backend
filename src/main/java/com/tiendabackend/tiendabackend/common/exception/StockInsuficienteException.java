package com.tiendabackend.tiendabackend.common.exception;

public class StockInsuficienteException extends RuntimeException {

    public StockInsuficienteException(String nombreProducto, Integer stockDisponible, Integer cantidadSolicitada) {

        super("Stock insuficiente para el producto '" + nombreProducto + "': disponible "
                + stockDisponible + ", solicitado " + cantidadSolicitada);
    }
}

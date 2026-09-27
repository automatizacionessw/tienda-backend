package com.tiendabackend.tiendabackend.common.almacenamiento;

import java.io.InputStream;

// Abstraccion del medio donde se guardan los archivos binarios (hoy disco
// local; a futuro S3/MinIO). Quien guarda recibe una clave opaca y relativa,
// que es lo unico que se persiste en la BD: cambiar de implementacion no
// obliga a migrar datos.
public interface AlmacenamientoArchivos {

    /**
     * Guarda el contenido y devuelve su clave.
     *
     * @param categoria agrupacion logica (ej. "voz"), primer segmento de la clave
     * @param contenido stream a guardar; no se cierra
     * @param extension extension sin punto (ej. "ogg")
     */
    String guardar(String categoria, InputStream contenido, String extension);

    /**
     * Abre el contenido guardado bajo la clave. El llamador debe cerrar el stream.
     *
     * @throws ArchivoNoEncontradoException si la clave no existe
     */
    InputStream abrir(String clave);
}

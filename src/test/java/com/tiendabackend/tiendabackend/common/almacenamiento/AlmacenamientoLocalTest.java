package com.tiendabackend.tiendabackend.common.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AlmacenamientoLocalTest {

    @TempDir
    Path tempDir;

    private AlmacenamientoLocal crear(Path directorio) {
        AlmacenamientoLocal almacenamiento =
                new AlmacenamientoLocal(new AlmacenamientoLocalProperties(directorio.toString()));
        almacenamiento.inicializar();
        return almacenamiento;
    }

    private static InputStream stream(String texto) {
        return new ByteArrayInputStream(texto.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void guardaYRecuperaElMismoContenido() throws Exception {
        AlmacenamientoLocal almacenamiento = crear(tempDir);
        byte[] audio = {0x4F, 0x67, 0x67, 0x53, 0x00, (byte) 0xFF};

        String clave = almacenamiento.guardar("voz", new ByteArrayInputStream(audio), "ogg");

        assertThat(clave).matches("voz/\\d{4}/\\d{2}/\\d{2}/[0-9a-f-]{36}\\.ogg");
        try (InputStream leido = almacenamiento.abrir(clave)) {
            assertThat(leido.readAllBytes()).isEqualTo(audio);
        }
        assertThat(tempDir.resolve(clave)).exists();
    }

    @Test
    void dosArchivosConElMismoNombreObtienenClavesDistintas() throws Exception {
        AlmacenamientoLocal almacenamiento = crear(tempDir);

        String clave1 = almacenamiento.guardar("voz", stream("primero"), "ogg");
        String clave2 = almacenamiento.guardar("voz", stream("segundo"), "ogg");

        assertThat(clave1).isNotEqualTo(clave2);
        try (InputStream a = almacenamiento.abrir(clave1); InputStream b = almacenamiento.abrir(clave2)) {
            assertThat(new String(a.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("primero");
            assertThat(new String(b.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("segundo");
        }
    }

    @Test
    void noDejaTemporalesTrasGuardar() throws Exception {
        AlmacenamientoLocal almacenamiento = crear(tempDir);

        String clave = almacenamiento.guardar("voz", stream("x"), "ogg");

        try (var archivos = Files.list(tempDir.resolve(clave).getParent())) {
            assertThat(archivos).hasSize(1);
        }
    }

    @Test
    void claveInexistenteLanzaNoEncontrado() {
        AlmacenamientoLocal almacenamiento = crear(tempDir);

        assertThatThrownBy(() -> almacenamiento.abrir("voz/2026/01/01/no-existe.ogg"))
                .isInstanceOf(ArchivoNoEncontradoException.class)
                .hasMessageContaining("voz/2026/01/01/no-existe.ogg");
    }

    @Test
    void claveConRecorridoDeDirectoriosEsRechazada() throws Exception {
        Path base = Files.createDirectories(tempDir.resolve("base"));
        Files.writeString(tempDir.resolve("secreto.txt"), "no debe leerse");
        AlmacenamientoLocal almacenamiento = crear(base);

        assertThatThrownBy(() -> almacenamiento.abrir("../secreto.txt"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> almacenamiento.abrir("voz/../../secreto.txt"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> almacenamiento.abrir(tempDir.resolve("secreto.txt").toString()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void categoriaOExtensionInvalidasSonRechazadas() {
        AlmacenamientoLocal almacenamiento = crear(tempDir);

        assertThatThrownBy(() -> almacenamiento.guardar("../fuera", stream("x"), "ogg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> almacenamiento.guardar("voz", stream("x"), "../ogg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void creaElDirectorioBaseSiNoExiste() throws Exception {
        Path inexistente = tempDir.resolve("no/existe/aun");

        AlmacenamientoLocal almacenamiento = crear(inexistente);
        String clave = almacenamiento.guardar("voz", stream("x"), "ogg");

        assertThat(inexistente).isDirectory();
        assertThat(inexistente.resolve(clave)).exists();
    }

    @Test
    void directorioQueNoPuedeCrearseFallaIndicandoLaRuta() throws Exception {
        // Un archivo comun ocupando la ruta hace imposible crear el directorio
        // (equivalente portable a un directorio sin permisos de escritura).
        Path ocupado = Files.writeString(tempDir.resolve("ocupado"), "soy un archivo");
        Path directorio = ocupado.resolve("archivos");
        AlmacenamientoLocal almacenamiento =
                new AlmacenamientoLocal(new AlmacenamientoLocalProperties(directorio.toString()));

        assertThatThrownBy(almacenamiento::inicializar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(directorio.toAbsolutePath().normalize().toString());
    }

    @Test
    void directorioPorDefectoCuandoNoSeConfigura() {
        assertThat(new AlmacenamientoLocalProperties(null).directorio()).isEqualTo("./data/archivos");
        assertThat(new AlmacenamientoLocalProperties(" ").directorio()).isEqualTo("./data/archivos");
    }
}

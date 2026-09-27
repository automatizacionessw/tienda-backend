package com.tiendabackend.tiendabackend.common.almacenamiento;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

// Guarda los archivos bajo un directorio base configurable. La clave tiene la
// forma <categoria>/<yyyy>/<MM>/<dd>/<uuid>.<ext>: relativa, sin datos del
// cliente y utilizable tal cual como object key si se migra a S3/MinIO.
@Component
public class AlmacenamientoLocal implements AlmacenamientoArchivos {

    private static final Pattern CATEGORIA_VALIDA = Pattern.compile("[a-z0-9-]+");
    private static final Pattern EXTENSION_VALIDA = Pattern.compile("[a-z0-9]{1,10}");

    private final Path base;

    public AlmacenamientoLocal(AlmacenamientoLocalProperties properties) {
        this.base = Paths.get(properties.directorio()).toAbsolutePath().normalize();
    }

    // Se valida al arrancar para que un directorio mal configurado haga fallar
    // el inicio, y no la primera nota de voz que llegue.
    @PostConstruct
    public void inicializar() {
        try {
            Files.createDirectories(base);
            Path prueba = Files.createTempFile(base, ".escritura-", ".tmp");
            Files.delete(prueba);
        } catch (IOException | SecurityException e) {
            throw new IllegalStateException(
                    "El directorio de almacenamiento no existe, no puede crearse o no es escribible: " + base, e);
        }
    }

    @Override
    public String guardar(String categoria, InputStream contenido, String extension) {
        if (categoria == null || !CATEGORIA_VALIDA.matcher(categoria).matches()) {
            throw new IllegalArgumentException("Categoria invalida: " + categoria);
        }
        if (extension == null || !EXTENSION_VALIDA.matcher(extension).matches()) {
            throw new IllegalArgumentException("Extension invalida: " + extension);
        }

        LocalDate hoy = LocalDate.now(ZoneOffset.UTC);
        String clave = String.format("%s/%04d/%02d/%02d/%s.%s",
                categoria, hoy.getYear(), hoy.getMonthValue(), hoy.getDayOfMonth(), UUID.randomUUID(), extension);
        Path destino = resolver(clave);

        Path temporal = null;
        try {
            Files.createDirectories(destino.getParent());
            // Se escribe a un temporal en el mismo directorio y se mueve de forma
            // atomica: nunca queda un archivo a medio escribir bajo la clave final.
            temporal = Files.createTempFile(destino.getParent(), ".subida-", ".part");
            Files.copy(contenido, temporal, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE);
            return clave;
        } catch (IOException e) {
            borrarSilenciosamente(temporal);
            throw new UncheckedIOException("No se pudo guardar el archivo " + clave, e);
        }
    }

    @Override
    public InputStream abrir(String clave) {
        Path archivo = resolver(clave);
        if (!Files.isRegularFile(archivo)) {
            throw new ArchivoNoEncontradoException(clave);
        }
        try {
            return Files.newInputStream(archivo);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo abrir el archivo " + clave, e);
        }
    }

    // Toda clave se resuelve dentro de base: se rechazan rutas absolutas y
    // recorridos con "..", que podrian leer o escribir fuera del directorio.
    private Path resolver(String clave) {
        if (clave == null || clave.isBlank()) {
            throw new IllegalArgumentException("La clave no puede estar vacia");
        }
        Path ruta = base.resolve(clave).normalize();
        if (!ruta.startsWith(base) || ruta.equals(base)) {
            throw new IllegalArgumentException("La clave apunta fuera del directorio de almacenamiento: " + clave);
        }
        return ruta;
    }

    private static void borrarSilenciosamente(Path ruta) {
        if (ruta == null) {
            return;
        }
        try {
            Files.deleteIfExists(ruta);
        } catch (IOException ignorada) {
            // El temporal huerfano no afecta a ninguna clave valida.
        }
    }
}

package pe.edu.siscom;

import java.nio.file.*;
import java.util.*;
import java.io.*;

public final class Configuracion {
    public static final Properties valores = new Properties();
    static {
        String archivo = System.getenv("SISCOM_CONFIG");
        if (archivo == null) archivo = "config/local.properties";
        if (Files.exists(Paths.get(archivo))) {
            try (InputStream entrada = Files.newInputStream(Paths.get(archivo))) { valores.load(entrada); }
            catch (IOException e) { throw new IllegalStateException("No se pudo leer la configuracion", e); }
        }
    }
    public static String obtener(String clave, String defecto) {
        String externo = System.getenv("SISCOM_" + clave.toUpperCase(Locale.ROOT).replace('.', '_'));
        return externo == null ? valores.getProperty(clave, defecto) : externo;
    }
    public static String url() { return obtener("db.url", "jdbc:h2:./datos/siscom;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=15000"); }
    public static boolean demo() { return url().startsWith("jdbc:h2:"); }
    private Configuracion() { }
}

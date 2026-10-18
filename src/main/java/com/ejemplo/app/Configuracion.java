package com.ejemplo.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Guarda entre sesiones las carpetas añadidas a mano y los juegos descartados.
 *
 * El fichero {@code gamemgr.properties} se coloca siempre junto a la aplicación:
 * <ul>
 *     <li>Si es un <b>.exe</b> (jpackage), en la carpeta del ejecutable.</li>
 *     <li>Si es un <b>.jar</b>, junto al propio .jar.</li>
 *     <li>En desarrollo (IDE / mvn), en la carpeta del proyecto.</li>
 * </ul>
 * Si esa ubicación no se puede escribir, se usa la carpeta del usuario como respaldo.
 */
public class Configuracion {

    private static final String CLAVE_CARPETA = "carpeta.";
    private static final String CLAVE_DESCARTADO = "descartado.";

    private final Path archivo;
    private final List<String> carpetas = new ArrayList<>();
    private final Set<String> descartados = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    public Configuracion() {
        archivo = carpetaBase().resolve("gamemgr.properties");
        cargar();
    }

    /**
     * Decide dónde vive el fichero: junto a la app si se puede escribir,
     * y si no, en la carpeta del usuario.
     */
    private static Path carpetaBase() {
        Path juntoApp = carpetaDeLaApp();
        if (juntoApp != null && Files.isWritable(juntoApp)) {
            return juntoApp;
        }
        return Paths.get(System.getProperty("user.home"), "GameMgr");
    }

    /** Carpeta donde está la app (exe, jar) o del proyecto en desarrollo. */
    private static Path carpetaDeLaApp() {
        // 1) App empaquetada como .exe con jpackage: el lanzador define esta propiedad.
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath != null && !appPath.isBlank()) {
            Path padre = Paths.get(appPath).getParent();
            if (padre != null && Files.isDirectory(padre)) {
                return padre;
            }
        }

        // 2) Ejecutable .jar: junto al propio .jar.
        try {
            URL ubicacion = Configuracion.class.getProtectionDomain().getCodeSource().getLocation();
            Path ruta = Paths.get(ubicacion.toURI());
            if (Files.isRegularFile(ruta)) {           // Es un .jar, no una carpeta de clases.
                Path padre = ruta.getParent();
                if (padre != null) {
                    return padre;
                }
            }
        } catch (Exception e) {
            // Sin información de ubicación: se continúa con el respaldo.
        }

        // 3) Desarrollo (IDE / mvn javafx:run): carpeta del proyecto.
        return Paths.get(System.getProperty("user.dir"));
    }

    // ------------------------------------------------------------------
    // Lectura / escritura
    // ------------------------------------------------------------------

    private void cargar() {
        if (!Files.isRegularFile(archivo)) {
            return;
        }
        Properties propiedades = new Properties();
        try (InputStream in = Files.newInputStream(archivo)) {
            propiedades.load(in);
        } catch (IOException e) {
            return;
        }

        int i = 0;
        while (propiedades.getProperty(CLAVE_CARPETA + i) != null) {
            carpetas.add(propiedades.getProperty(CLAVE_CARPETA + i++));
        }
        i = 0;
        while (propiedades.getProperty(CLAVE_DESCARTADO + i) != null) {
            descartados.add(propiedades.getProperty(CLAVE_DESCARTADO + i++));
        }
    }

    private void guardar() {
        Properties propiedades = new Properties();
        int i = 0;
        for (String carpeta : carpetas) {
            propiedades.setProperty(CLAVE_CARPETA + i++, carpeta);
        }
        i = 0;
        for (String descartado : descartados) {
            propiedades.setProperty(CLAVE_DESCARTADO + i++, descartado);
        }
        try {
            Files.createDirectories(archivo.getParent());
            try (OutputStream out = Files.newOutputStream(archivo)) {
                propiedades.store(out, "Configuracion de GameMgr");
            }
        } catch (IOException e) {
            // Si no se puede guardar, la app sigue funcionando en memoria.
        }
    }

    // ------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------

    public List<String> carpetas() {
        return List.copyOf(carpetas);
    }

    public void agregarCarpeta(String ruta) {
        if (!carpetas.contains(ruta)) {
            carpetas.add(ruta);
            guardar();
        }
    }

    public boolean estaDescartado(String nombre) {
        return descartados.contains(nombre);
    }

    public void descartar(String nombre) {
        descartados.add(nombre);
        guardar();
    }

    /** Nº de juegos descartados actualmente. */
    public int totalDescartados() {
        return descartados.size();
    }

    /** Vuelve a permitir que los juegos descartados aparezcan al escanear. */
    public void restaurarDescartados() {
        descartados.clear();
        guardar();
    }
}

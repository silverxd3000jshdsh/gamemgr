package com.ejemplo.app;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utilidades para localizar el ejecutable principal de un juego a partir
 * de su carpeta de instalación o del valor "DisplayIcon" del registro.
 */
public final class Ejecutables {

    private Ejecutables() {
    }

    /**
     * Devuelve el .exe más representativo de una ruta.
     * Si la ruta ya es un fichero, se devuelve tal cual.
     */
    public static File principal(String ruta) {
        if (ruta == null || ruta.isBlank()) {
            return null;
        }
        File base = new File(ruta);
        if (base.isFile()) {
            return base;
        }
        if (!base.isDirectory()) {
            return null;
        }
        String carpeta = base.getName().toLowerCase().replace(" ", "");
        File primero = null;
        try (DirectoryStream<Path> flujo = Files.newDirectoryStream(base.toPath(), "*.exe")) {
            for (Path elemento : flujo) {
                if (primero == null) {
                    primero = elemento.toFile();
                }
                String nombreSinExtension = elemento.getFileName().toString()
                        .toLowerCase().replace(".exe", "").replace(" ", "");
                if (!carpeta.isEmpty() && nombreSinExtension.contains(carpeta)) {
                    return elemento.toFile();
                }
            }
        } catch (IOException e) {
            // Carpeta inaccesible.
        }
        return primero;
    }

    /**
     * Convierte el valor "DisplayIcon" del registro (p. ej.
     * {@code "C:\Juego\juego.exe",0}) en un fichero, si apunta a un .exe existente.
     */
    public static File desdeDisplayIcon(String icono) {
        if (icono == null || icono.isBlank()) {
            return null;
        }
        String limpio = icono.trim();
        if (limpio.startsWith("\"")) {
            int fin = limpio.indexOf('"', 1);
            if (fin > 0) {
                limpio = limpio.substring(1, fin);
            }
        } else {
            int coma = limpio.lastIndexOf(',');
            if (coma > 2 && limpio.substring(coma).matches(",\\s*-?\\d+")) {
                limpio = limpio.substring(0, coma);
            }
        }
        limpio = limpio.replace("\"", "").trim();
        if (!limpio.toLowerCase().endsWith(".exe")) {
            return null;
        }
        File fichero = new File(limpio);
        return fichero.isFile() ? fichero : null;
    }
}

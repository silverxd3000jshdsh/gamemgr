package com.ejemplo.app;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.swing.filechooser.FileSystemView;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Obtiene el icono real de cada juego a partir de su ejecutable.
 *
 * Usa el icono del sistema de Windows (Swing {@link FileSystemView}) y lo
 * convierte a una imagen de JavaFX. Los iconos se guardan en caché para no
 * releer el disco cada vez que la tabla se repinta.
 */
public final class IconosJuegos {

    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    private IconosJuegos() {
    }

    /** Devuelve el icono del juego (o {@code null} si no se encuentra). */
    public static Image iconoDe(Juego juego) {
        String clave = juego.ruta() == null ? juego.nombre() : juego.ruta();
        return CACHE.computeIfAbsent(clave, k -> cargarIcono(juego));
    }

    private static Image cargarIcono(Juego juego) {
        File ejecutable = ejecutableDe(juego);
        if (ejecutable == null) {
            return null;
        }
        try {
            javax.swing.Icon icono = FileSystemView.getFileSystemView().getSystemIcon(ejecutable, 32, 32);
            if (icono == null) {
                return null;
            }
            int ancho = Math.max(1, icono.getIconWidth());
            int alto = Math.max(1, icono.getIconHeight());
            BufferedImage buffer = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = buffer.createGraphics();
            icono.paintIcon(new javax.swing.JLabel(), g, 0, 0);
            g.dispose();
            return SwingFXUtils.toFXImage(buffer, null);
        } catch (Exception e) {
            return null;
        }
    }

    /** Localiza el ejecutable a partir de la URI/path guardados o de la carpeta. */
    private static File ejecutableDe(Juego juego) {
        String objetivo = juego.ejecutable();
        if (objetivo != null && !objetivo.isBlank() && !objetivo.contains("://")) {
            File directo = new File(objetivo);
            if (directo.isFile()) {
                return directo;
            }
        }
        return Ejecutables.principal(juego.ruta());
    }
}

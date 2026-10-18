package com.ejemplo.app;

/**
 * Representa un juego instalado en el equipo.
 *
 * @param nombre      Nombre visible del juego.
 * @param plataforma  Origen ("Steam", "Epic Games", "Registro" o "Carpeta local").
 * @param ruta        Ruta de instalación en el disco.
 * @param ejecutable  Cómo lanzarlo: ruta a un .exe o una URI (p. ej. steam://rungameid/730).
 */
public record Juego(String nombre, String plataforma, String ruta, String ejecutable) {
}

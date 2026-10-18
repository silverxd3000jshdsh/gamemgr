package com.ejemplo.app;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Busca juegos instalados en el equipo leyendo:
 * <ul>
 *     <li>Los manifiestos de <b>Steam</b> (todas las bibliotecas).</li>
 *     <li>Los manifiestos de <b>Epic Games</b>.</li>
 *     <li>Las entradas del <b>registro de Windows</b> que parecen juegos.</li>
 * </ul>
 * Además permite tratar cualquier carpeta elegida por el usuario como juegos.
 *
 * No usa librerías externas: los manifiestos son texto plano y se parsean
 * con expresiones regulares sencillas.
 */
public class DetectorJuegos {

    /** Palabras que delatan software que NO es un juego (utilidades, runtimes, etc.). */
    private static final List<String> CLAVES_EXCLUIDAS = List.of(
            "update", "updater", "runtime", "redistributable", "driver", "sdk",
            "launcher", "prerequisite", "helper", "service", "component",
            "click-to-run", "webview", "add-in", "plugin", "extension",
            "visual c++", ".net", "framework", "jdk", "jre", "java", "python",
            "node.js", "npm", "git", "browser", "chrome", "firefox", "edge",
            "office", "excel", "outlook", "teams", "zoom", "discord", "whatsapp",
            "canva", "vlc", "winrar", "7-zip", "qbittorrent", "adobe", "acrobat",
            "vpn", "mail", "intellij", "pycharm", "webstorm", "eclipse", "vscode",
            "vscodium", "visual studio", "mysql", "postgres", "database",
            "antivirus", "mcafee", "avast", "spotify", "spicetify", "obsidian",
            "ollama", "blockbench", "bluej", "fastfetch", "glazewm", "zebar",
            "nano", "bedrock", "xylar", "reiboot", "tenorshare", "paragon",
            "nefarius", "gamepad", "ryzen", "crosshair", "copilot", "claude",
            "tlauncher", "temurin", "hotspot", "webadvisor", "lenovo", "canon",
            "amd ", "nvidia", "realtek", "subsystem", "utils", "virtualtablet",
            "wiztree", "vantage", "steam", "connect", "branding", "voice service"
    );

    /** Fabricantes que publican software general, no juegos. */
    private static final List<String> PUBLISHER_EXCLUIDOS = List.of(
            "microsoft", "adobe", "google", "mozilla", "jetbrains", "oracle",
            "python software", "advanced micro devices", "canon", "lenovo",
            "mcafee", "avast", "discord", "zoom", "canva", "paragon",
            "tenorshare", "nefarius", "glzr", "anthropic", "github",
            "spicetify", "video lan", "antibody software", "fastfetch", "ollama",
            "obsidian", "nankill", "valve corporation", "repaircang", "sunny",
            "proton"
    );

    /** Patrón de una línea de valor del registro: "Nombre    REG_SZ    valor". */
    private static final Pattern LINEA_REGISTRO =
            Pattern.compile("^\\s+(\\S+)\\s+REG_\\w+\\s*(.*)$");

    // ------------------------------------------------------------------
    // Punto de entrada
    // ------------------------------------------------------------------

    /** Busca juegos en todas las fuentes automáticas. */
    public List<Juego> detectarJuegos() {
        List<Juego> juegos = new ArrayList<>();
        juegos.addAll(detectarSteam());
        juegos.addAll(detectarEpic());
        juegos.addAll(detectarRegistro());
        return juegos;
    }

    // ------------------------------------------------------------------
    // Steam
    // ------------------------------------------------------------------

    /** Devuelve los juegos instalados encontrados en todas las bibliotecas de Steam. */
    public List<Juego> detectarSteam() {
        List<Juego> juegos = new ArrayList<>();

        Path steam = rutaSteam();
        if (steam == null) {
            return juegos;
        }

        List<Path> bibliotecas = new ArrayList<>();
        bibliotecas.add(steam);
        bibliotecas.addAll(leerBibliotecasSteam(steam.resolve("steamapps").resolve("libraryfolders.vdf")));

        for (Path biblioteca : bibliotecas) {
            Path steamapps = biblioteca.resolve("steamapps");
            if (!Files.isDirectory(steamapps)) {
                continue;
            }
            try (DirectoryStream<Path> flujo = Files.newDirectoryStream(steamapps, "appmanifest_*.acf")) {
                for (Path manifiesto : flujo) {
                    try {
                        String contenido = Files.readString(manifiesto, StandardCharsets.UTF_8);
                        String nombre = extraerCampoVdf(contenido, "name");
                        if (nombre == null) {
                            continue;
                        }
                        String installdir = extraerCampoVdf(contenido, "installdir");
                        Path ruta = (installdir != null)
                                ? steamapps.resolve("common").resolve(installdir)
                                : steamapps;
                        String appId = extraerCampoVdf(contenido, "appid");
                        String ejecutable = (appId != null) ? "steam://rungameid/" + appId : null;
                        juegos.add(new Juego(nombre, "Steam", ruta.toString(), ejecutable));
                    } catch (IOException e) {
                        // Manifiesto ilegible: se ignora.
                    }
                }
            } catch (IOException e) {
                // Biblioteca inaccesible: se ignora.
            }
        }
        return juegos;
    }

    /** Localiza el directorio de instalación de Steam. */
    private Path rutaSteam() {
        List<String> candidatos = new ArrayList<>();
        String pf = System.getenv("ProgramFiles");
        String pf86 = System.getenv("ProgramFiles(x86)");
        if (pf86 != null) {
            candidatos.add(pf86 + "\\Steam");
        }
        if (pf != null) {
            candidatos.add(pf + "\\Steam");
        }
        candidatos.add("C:\\Program Files (x86)\\Steam");
        candidatos.add("C:\\Program Files\\Steam");

        for (String candidato : candidatos) {
            Path ruta = Paths.get(candidato);
            if (Files.isDirectory(ruta)) {
                return ruta;
            }
        }
        return null;
    }

    /** Lee las rutas de biblioteca adicionales de libraryfolders.vdf. */
    private List<Path> leerBibliotecasSteam(Path vdf) {
        List<Path> bibliotecas = new ArrayList<>();
        if (!Files.isRegularFile(vdf)) {
            return bibliotecas;
        }
        try {
            String contenido = Files.readString(vdf, StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("\"path\"\\s+\"([^\"]+)\"").matcher(contenido);
            while (m.find()) {
                bibliotecas.add(Paths.get(desescapar(m.group(1))));
            }
        } catch (IOException e) {
            // Archivo ilegible: sin bibliotecas adicionales.
        }
        return bibliotecas;
    }

    /** Extrae el valor de una clave tipo VDF: "clave"  "valor". */
    private String extraerCampoVdf(String texto, String clave) {
        Matcher m = Pattern.compile(Pattern.quote("\"" + clave + "\"") + "\\s+\"([^\"]+)\"").matcher(texto);
        return m.find() ? desescapar(m.group(1)) : null;
    }

    // ------------------------------------------------------------------
    // Epic Games
    // ------------------------------------------------------------------

    /** Devuelve los juegos encontrados en los manifiestos del launcher de Epic. */
    public List<Juego> detectarEpic() {
        List<Juego> juegos = new ArrayList<>();

        String programData = System.getenv("ProgramData");
        Path base = Paths.get(programData != null ? programData : "C:\\ProgramData");
        Path manifiestos = base.resolve("Epic").resolve("EpicGamesLauncher").resolve("Data").resolve("Manifests");

        if (!Files.isDirectory(manifiestos)) {
            return juegos;
        }

        try (DirectoryStream<Path> flujo = Files.newDirectoryStream(manifiestos, "*.item")) {
            for (Path manifiesto : flujo) {
                try {
                    String contenido = Files.readString(manifiesto, StandardCharsets.UTF_8);
                    String nombre = extraerCampoJson(contenido, "DisplayName");
                    if (nombre == null) {
                        continue;
                    }
                    String ruta = extraerCampoJson(contenido, "InstallLocation");
                    String appName = extraerCampoJson(contenido, "AppName");
                    String lanzarExe = extraerCampoJson(contenido, "LaunchExecutable");

                    String ejecutable;
                    if (appName != null) {
                        ejecutable = "com.epicgames.launcher://apps/" + appName
                                + "?action=launch&silent=true";
                    } else if (ruta != null && lanzarExe != null) {
                        ejecutable = new File(ruta, lanzarExe).getAbsolutePath();
                    } else {
                        ejecutable = null;
                    }
                    juegos.add(new Juego(nombre, "Epic Games", ruta != null ? ruta : "", ejecutable));
                } catch (IOException e) {
                    // Manifiesto ilegible: se ignora.
                }
            }
        } catch (IOException e) {
            // Carpeta inaccesible: se ignora.
        }
        return juegos;
    }

    /** Extrae el valor de una clave de un JSON sencillo: "clave": "valor". */
    private String extraerCampoJson(String texto, String clave) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(clave) + "\"\\s*:\\s*\"([^\"]+)\"").matcher(texto);
        return m.find() ? desescapar(m.group(1)) : null;
    }

    // ------------------------------------------------------------------
    // Registro de Windows
    // ------------------------------------------------------------------

    /** Lee las tres ramas habituales de "programas instalados". */
    public List<Juego> detectarRegistro() {
        List<Juego> juegos = new ArrayList<>();
        String[] claves = {
                "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
                "HKLM\\SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall",
                "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall"
        };
        for (String clave : claves) {
            juegos.addAll(leerClaveRegistro(clave));
        }
        return juegos;
    }

    /** Ejecuta {@code reg query} sobre una clave y parsea el resultado. */
    private List<Juego> leerClaveRegistro(String clave) {
        // chcp 65001 fuerza salida UTF-8 para que los acentos se lean bien.
        String comando = "chcp 65001>nul & reg query " + clave + " /s";
        try {
            Process proceso = new ProcessBuilder("cmd.exe", "/c", comando)
                    .redirectErrorStream(true)
                    .start();
            String salida;
            try (InputStream in = proceso.getInputStream()) {
                salida = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            proceso.waitFor(60, TimeUnit.SECONDS);
            return parsearRegistro(salida);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** Convierte la salida de {@code reg query} en juegos, filtrando utilidades. */
    private List<Juego> parsearRegistro(String salida) {
        List<Juego> juegos = new ArrayList<>();

        String nombre = null;
        String ruta = null;
        String publisher = null;
        String displayIcon = null;
        boolean esSistema = false;
        boolean esActualizacion = false;

        for (String linea : salida.split("\\R")) {
            if (linea.startsWith("HKEY_")) {
                agregarSiEsJuego(juegos, nombre, ruta, publisher, displayIcon, esSistema, esActualizacion);
                nombre = null;
                ruta = null;
                publisher = null;
                displayIcon = null;
                esSistema = false;
                esActualizacion = false;
                continue;
            }
            Matcher m = LINEA_REGISTRO.matcher(linea);
            if (!m.matches()) {
                continue;
            }
            String campo = m.group(1);
            String valor = m.group(2).trim();
            switch (campo) {
                case "DisplayName" -> nombre = valor;
                case "InstallLocation" -> ruta = valor;
                case "Publisher" -> publisher = valor;
                case "DisplayIcon" -> displayIcon = valor;
                case "SystemComponent" -> esSistema = valor.equals("1");
                case "ParentKeyName" -> esActualizacion = true;
                default -> { /* Campo irrelevante. */ }
            }
        }
        agregarSiEsJuego(juegos, nombre, ruta, publisher, displayIcon, esSistema, esActualizacion);
        return juegos;
    }

    private void agregarSiEsJuego(List<Juego> juegos, String nombre, String ruta, String publisher,
                                  String displayIcon, boolean esSistema, boolean esActualizacion) {
        if (nombre == null || esSistema || esActualizacion) {
            return;
        }
        if (!pareceJuego(nombre, publisher)) {
            return;
        }
        File exe = Ejecutables.desdeDisplayIcon(displayIcon);
        if (exe == null) {
            exe = Ejecutables.principal(ruta);
        }
        String ejecutable = (exe != null) ? exe.getAbsolutePath() : null;
        juegos.add(new Juego(nombre.trim(), "Registro", ruta != null ? ruta : "", ejecutable));
    }

    /** Heurística: descarta utilidades y deja lo que "parece un juego". */
    private boolean pareceJuego(String nombre, String publisher) {
        String n = nombre.toLowerCase();
        String p = publisher == null ? "" : publisher.toLowerCase();
        for (String clave : CLAVES_EXCLUIDAS) {
            if (n.contains(clave)) {
                return false;
            }
        }
        for (String pub : PUBLISHER_EXCLUIDOS) {
            if (p.contains(pub)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Carpeta manual
    // ------------------------------------------------------------------

    /**
     * Trata cada subcarpeta directa de {@code carpeta} como un juego.
     * Así el usuario puede apuntar a su carpeta de juegos portables, GOG, etc.
     */
    public List<Juego> escanearCarpeta(Path carpeta) {
        List<Juego> juegos = new ArrayList<>();
        if (carpeta == null || !Files.isDirectory(carpeta)) {
            return juegos;
        }
        try (DirectoryStream<Path> flujo = Files.newDirectoryStream(carpeta)) {
            for (Path elemento : flujo) {
                if (Files.isDirectory(elemento)) {
                    File exe = Ejecutables.principal(elemento.toString());
                    juegos.add(new Juego(
                            elemento.getFileName().toString(),
                            "Carpeta local",
                            elemento.toString(),
                            exe != null ? exe.getAbsolutePath() : null));
                }
            }
        } catch (IOException e) {
            // Carpeta inaccesible: se devuelve la lista vacía.
        }
        return juegos;
    }

    /** Convierte los "\\\\" de los manifiestos en un único separador de Windows. */
    private String desescapar(String valor) {
        return valor.replace("\\\\", "\\");
    }
}

package com.ejemplo.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class GameMgr extends Application {

    /** Lista observable que alimenta la tabla (y por tanto el buscador). */
    private final ObservableList<Juego> juegos = FXCollections.observableArrayList();
    private final DetectorJuegos detector = new DetectorJuegos();
    private final Configuracion config = new Configuracion();

    private TableView<Juego> tabla;
    private Label etiquetaEstado;
    private Button botonRestaurar;

    @Override
    public void start(Stage ventana) {

        // 1. Encabezado
        Label titulo = new Label("GameMgr");
        titulo.getStyleClass().add("titulo");
        Label subtitulo = new Label("Juegos detectados en este equipo");
        subtitulo.getStyleClass().add("subtitulo");
        VBox encabezado = new VBox(2, titulo, subtitulo);

        // 2. Barra de acciones: buscador + botones
        TextField buscador = new TextField();
        buscador.setPromptText("Buscar juego...");
        buscador.getStyleClass().add("buscador");
        HBox.setHgrow(buscador, Priority.ALWAYS);

        Button botonEscanear = new Button("Escanear");
        botonEscanear.getStyleClass().add("boton-redondeado");

        Button botonCarpeta = new Button("Añadir carpeta");
        botonCarpeta.getStyleClass().add("boton-secundario");

        botonRestaurar = new Button("Restaurar descartados");
        botonRestaurar.getStyleClass().add("boton-secundario");

        HBox barraAcciones = new HBox(10, buscador, botonEscanear, botonCarpeta, botonRestaurar);
        barraAcciones.setAlignment(Pos.CENTER_LEFT);
        actualizarBotonRestaurar();

        // 3. Tabla de juegos
        tabla = new TableView<>();
        tabla.getStyleClass().add("tabla-juegos");
        tabla.setPlaceholder(new Label("No se han encontrado juegos. Pulsa «Escanear» o añade una carpeta."));

        TableColumn<Juego, Juego> colIcono = crearColumnaIcono();

        TableColumn<Juego, String> colNombre = new TableColumn<>("Juego");
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().nombre()));
        colNombre.setPrefWidth(340);

        TableColumn<Juego, String> colPlataforma = new TableColumn<>("Plataforma");
        colPlataforma.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().plataforma()));
        colPlataforma.setPrefWidth(110);

        TableColumn<Juego, String> colRuta = new TableColumn<>("Ubicación");
        colRuta.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().ruta()));
        colRuta.setPrefWidth(430);

        TableColumn<Juego, Juego> colAcciones = crearColumnaAcciones();

        tabla.getColumns().add(colIcono);
        tabla.getColumns().add(colNombre);
        tabla.getColumns().add(colPlataforma);
        tabla.getColumns().add(colRuta);
        tabla.getColumns().add(colAcciones);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(tabla, Priority.ALWAYS);

        // Menú contextual (click derecho).
        ContextMenu menuContextual = new ContextMenu();
        MenuItem itemJugar = new MenuItem("Jugar");
        itemJugar.setOnAction(e -> conSeleccion(this::jugar));
        MenuItem itemAbrir = new MenuItem("Abrir carpeta de instalación");
        itemAbrir.setOnAction(e -> conSeleccion(this::abrirCarpeta));
        MenuItem itemDescartar = new MenuItem("Descartar de la lista");
        itemDescartar.setOnAction(e -> conSeleccion(this::descartar));
        menuContextual.getItems().addAll(itemJugar, itemAbrir, itemDescartar);
        tabla.setContextMenu(menuContextual);

        // 4. Filtro en vivo
        FilteredList<Juego> filtrados = new FilteredList<>(juegos, j -> true);
        tabla.setItems(filtrados);
        buscador.textProperty().addListener((obs, anterior, texto) -> {
            String consulta = texto == null ? "" : texto.toLowerCase().trim();
            filtrados.setPredicate(j -> consulta.isEmpty()
                    || j.nombre().toLowerCase().contains(consulta)
                    || j.plataforma().toLowerCase().contains(consulta));
        });

        // 5. Barra de estado
        etiquetaEstado = new Label("Listo.");
        etiquetaEstado.getStyleClass().add("estado");

        // 6. Contenedor principal
        VBox raiz = new VBox(16, encabezado, barraAcciones, tabla, etiquetaEstado);
        raiz.setPadding(new Insets(22));
        raiz.getStyleClass().add("raiz");

        // 7. Lógica de los botones
        botonEscanear.setOnAction(e -> escanear());
        botonRestaurar.setOnAction(e -> {
            config.restaurarDescartados();
            actualizarBotonRestaurar();
            escanear();
            etiquetaEstado.setText("Descartados restaurados. Volviendo a buscar juegos...");
        });
        botonCarpeta.setOnAction(e -> {
            DirectoryChooser selector = new DirectoryChooser();
            selector.setTitle("Selecciona una carpeta de juegos");
            File carpeta = selector.showDialog(ventana);
            if (carpeta != null) {
                config.agregarCarpeta(carpeta.getAbsolutePath());
                List<Juego> encontrados = detector.escanearCarpeta(carpeta.toPath());
                agregar(encontrados);
                etiquetaEstado.setText("Añadidos " + encontrados.size()
                        + " juegos desde \"" + carpeta.getName() + "\". Se recordará esta carpeta.");
            }
        });

        // 8. Escena, estilos y ventana
        Scene escena = new Scene(raiz, 1000, 580);
        escena.getStylesheets().add(getClass().getResource("/estilos.css").toExternalForm());

        ventana.setTitle("GameMgr");
        ventana.setScene(escena);
        ventana.setResizable(true);
        ventana.setMinWidth(780);
        ventana.setMinHeight(460);
        ventana.show();

        escanear();
    }

    // ------------------------------------------------------------------
    // Columnas
    // ------------------------------------------------------------------

    /** Columna con el icono real extraído del ejecutable del juego. */
    private TableColumn<Juego, Juego> crearColumnaIcono() {
        TableColumn<Juego, Juego> columna = new TableColumn<>("");
        columna.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        columna.setCellFactory(c -> new TableCell<>() {
            private final ImageView vista = new ImageView();

            {
                vista.setFitWidth(26);
                vista.setFitHeight(26);
                vista.setPreserveRatio(true);
            }

            @Override
            protected void updateItem(Juego juego, boolean vacio) {
                super.updateItem(juego, vacio);
                if (vacio || juego == null) {
                    setGraphic(null);
                    return;
                }
                vista.setImage(IconosJuegos.iconoDe(juego));
                setGraphic(vista.getImage() != null ? vista : null);
            }
        });
        columna.setPrefWidth(46);
        columna.setSortable(false);
        return columna;
    }

    /** Columna con botones «Jugar» y «Abrir». */
    private TableColumn<Juego, Juego> crearColumnaAcciones() {
        TableColumn<Juego, Juego> columna = new TableColumn<>("Acciones");
        columna.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        columna.setCellFactory(c -> new TableCell<>() {
            private final Button botonJugar = new Button("Jugar");
            private final Button botonAbrir = new Button("Abrir");
            private final HBox caja = new HBox(6, botonJugar, botonAbrir);

            {
                botonJugar.getStyleClass().add("boton-tabla");
                botonAbrir.getStyleClass().add("boton-tabla-secundario");
                caja.setAlignment(Pos.CENTER_LEFT);
                botonJugar.setOnAction(e -> {
                    Juego juego = getItem();
                    if (juego != null) {
                        jugar(juego);
                    }
                });
                botonAbrir.setOnAction(e -> {
                    Juego juego = getItem();
                    if (juego != null) {
                        abrirCarpeta(juego);
                    }
                });
            }

            @Override
            protected void updateItem(Juego juego, boolean vacio) {
                super.updateItem(juego, vacio);
                if (vacio || juego == null) {
                    setGraphic(null);
                    return;
                }
                botonJugar.setDisable(juego.ejecutable() == null || juego.ejecutable().isBlank());
                botonAbrir.setDisable(juego.ruta() == null || juego.ruta().isBlank());
                setGraphic(caja);
            }
        });
        columna.setPrefWidth(160);
        columna.setSortable(false);
        return columna;
    }

    // ------------------------------------------------------------------
    // Acciones
    // ------------------------------------------------------------------

    private void conSeleccion(java.util.function.Consumer<Juego> accion) {
        Juego seleccionado = tabla.getSelectionModel().getSelectedItem();
        if (seleccionado != null) {
            accion.accept(seleccionado);
        }
    }

    /** Lanza el juego con su ejecutable o su URI de lanzador. */
    private void jugar(Juego juego) {
        String objetivo = juego.ejecutable();
        if (objetivo == null || objetivo.isBlank()) {
            etiquetaEstado.setText("No se encontró un ejecutable para " + juego.nombre() + ".");
            return;
        }
        try {
            if (objetivo.contains("://")) {
                // URI de lanzador (Steam / Epic): se abre con el manejador del sistema.
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", objetivo).start();
            } else {
                File exe = new File(objetivo);
                if (!exe.isFile()) {
                    etiquetaEstado.setText("El ejecutable ya no existe: " + objetivo);
                    return;
                }
                new ProcessBuilder(exe.getAbsolutePath())
                        .directory(exe.getParentFile())
                        .start();
            }
            etiquetaEstado.setText("Iniciando " + juego.nombre() + "...");
        } catch (Exception e) {
            etiquetaEstado.setText("No se pudo iniciar " + juego.nombre() + ".");
        }
    }

    /** Abre en el Explorador de Windows la carpeta donde está instalado el juego. */
    private void abrirCarpeta(Juego juego) {
        File destino = new File(juego.ruta());
        if (!destino.exists()) {
            File padre = destino.getParentFile();
            if (padre != null && padre.exists()) {
                destino = padre;
            }
        }
        if (!destino.exists()) {
            etiquetaEstado.setText("La carpeta no existe: " + juego.ruta());
            return;
        }
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(destino);
            } else {
                new ProcessBuilder("explorer.exe", destino.getAbsolutePath()).start();
            }
        } catch (Exception e) {
            try {
                new ProcessBuilder("explorer.exe", destino.getAbsolutePath()).start();
            } catch (Exception ex) {
                etiquetaEstado.setText("No se pudo abrir la carpeta de " + juego.nombre() + ".");
            }
        }
    }

    /** Quita el juego de la lista y recuerda no volver a mostrarlo. */
    private void descartar(Juego juego) {
        config.descartar(juego.nombre());
        juegos.remove(juego);
        actualizarBotonRestaurar();
        etiquetaEstado.setText("«" + juego.nombre() + "» descartado. No volverá a aparecer al escanear.");
    }

    /** Activa el botón de restaurar solo si hay juegos descartados. */
    private void actualizarBotonRestaurar() {
        int total = config.totalDescartados();
        botonRestaurar.setDisable(total == 0);
        botonRestaurar.setText(total == 0
                ? "Restaurar descartados"
                : "Restaurar descartados (" + total + ")");
    }

    // ------------------------------------------------------------------
    // Escaneo
    // ------------------------------------------------------------------

    /** Busca juegos de todas las fuentes en segundo plano. */
    private void escanear() {
        juegos.clear();
        etiquetaEstado.setText("Buscando juegos instalados...");

        Thread hilo = new Thread(() -> {
            List<Juego> encontrados = new ArrayList<>(detector.detectarJuegos());
            for (String carpeta : config.carpetas()) {
                encontrados.addAll(detector.escanearCarpeta(Paths.get(carpeta)));
            }
            Platform.runLater(() -> {
                agregar(encontrados);
                if (encontrados.isEmpty()) {
                    etiquetaEstado.setText("No se han detectado juegos automáticamente. "
                            + "Prueba a añadir una carpeta.");
                }
            });
        });
        hilo.setDaemon(true);
        hilo.start();
    }

    /** Añade juegos evitando duplicados y descartados. */
    private void agregar(List<Juego> nuevos) {
        for (Juego juego : nuevos) {
            if (config.estaDescartado(juego.nombre())) {
                continue;
            }
            boolean repetido = juegos.stream().anyMatch(existente ->
                    existente.nombre().equalsIgnoreCase(juego.nombre()));
            if (!repetido) {
                juegos.add(juego);
            }
        }
        etiquetaEstado.setText(juegos.size() + " juego(s) en la lista.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}

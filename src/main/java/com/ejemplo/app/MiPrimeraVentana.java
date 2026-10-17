package com.ejemplo.app;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class MiPrimeraVentana extends Application {

    private int contador = 0;

    @Override
    public void start(Stage ventana) {

        // 1. Título moderno
        Label etiquetaTexto = new Label("Clicks realizados: 0");
        etiquetaTexto.setStyle(
                "-fx-font-family: 'Segoe UI';" +
                "-fx-font-size: 22px;" +
                "-fx-font-weight: bold;" +
                "-fx-text-fill: white;");

        // 2. El botón estilizado (el redondeado y el efecto hover se definen en el CSS)
        Button botonClick = new Button("Incrementar Contador");
        botonClick.getStyleClass().add("boton-redondeado");

        // 3. Lógica del botón al hacer click
        botonClick.setOnAction(e -> {
            contador++;
            etiquetaTexto.setText("Clicks realizados: " + contador);
        });

        // 4. Contenedor principal con fondo oscuro
        VBox panelPrincipal = new VBox(25); // 25 px de separación entre hijos
        panelPrincipal.setAlignment(Pos.CENTER);
        panelPrincipal.setPadding(new Insets(30));
        panelPrincipal.setStyle("-fx-background-color: #212529;"); // Gris oscuro (#212529)
        panelPrincipal.getChildren().addAll(etiquetaTexto, botonClick);

        // 5. Escena y hoja de estilos (dónde vive el diseño del botón)
        Scene escena = new Scene(panelPrincipal, 380, 250);
        escena.getStylesheets().add(
                getClass().getResource("/estilos.css").toExternalForm());

        // 6. Configuración de la ventana principal
        ventana.setTitle("Panel de Control Moderno");
        ventana.setScene(escena);
        // Permite usar los botones nativos de minimizar/maximizar
        // de la barra de título y redimensionar la ventana.
        ventana.setResizable(true);
        ventana.setMinWidth(380);
        ventana.setMinHeight(250);
        ventana.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

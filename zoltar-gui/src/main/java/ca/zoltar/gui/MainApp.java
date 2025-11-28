package ca.zoltar.gui;

import ca.zoltar.db.DatabaseManager;
import ca.zoltar.util.ConfigManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(MainApp.class);

    @Override
    public void start(Stage primaryStage) {
        logger.info("Starting Zoltar...");

        // Initialize Configuration
        ConfigManager.getInstance();

        // Initialize Database
        try {
            DatabaseManager.getInstance();
        } catch (Exception e) {
            logger.error("Failed to initialize database", e);
            Platform.exit();
            return;
        }

        try {
            // Load main view
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ca/zoltar/gui/fxml/MainView.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root, 1200, 800);
            applyWindowIcon(primaryStage);

            primaryStage.setTitle("Zoltar - PubMed Novelty Evaluator");
            primaryStage.setScene(scene);
            primaryStage.setOnCloseRequest(event -> {
                logger.info("Application closing...");
                Platform.exit();
            });
            primaryStage.show();

            logger.info("Zoltar started successfully.");

        } catch (Exception e) {
            logger.error("Failed to load main view", e);
            Platform.exit();
        }
    }

    private void applyWindowIcon(Stage stage) {
        var iconUrl = MainApp.class.getResource("/ca/zoltar/gui/images/zoltar.png");
        if (iconUrl == null) {
            logger.warn("Window icon not found on classpath");
            return;
        }

        stage.getIcons().add(new Image(iconUrl.toExternalForm()));
    }

    public static void main(String[] args) {
        launch(args);
    }
}

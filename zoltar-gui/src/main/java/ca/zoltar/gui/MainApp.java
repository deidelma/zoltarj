package ca.zoltar.gui;

import ca.zoltar.db.DatabaseManager;
import ca.zoltar.util.ConfigManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
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

    public static void main(String[] args) {
        launch(args);
    }
}

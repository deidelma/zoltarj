package ca.zoltar.app;

import ca.zoltar.core.service.PdfIngestionService;
import ca.zoltar.db.DatabaseManager;
import ca.zoltar.db.TopicDao;
import ca.zoltar.util.ConfigManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

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
            // In a real app, show an error dialog here
        }

        // Check for CLI arguments
        Parameters params = getParameters();
        Map<String, String> namedParams = params.getNamed();
        
        if (namedParams.containsKey("ingest")) {
            handleIngestion(namedParams);
            Platform.exit();
            return;
        }

        // Setup UI
        Label label = new Label("Welcome to Zoltar");
        StackPane root = new StackPane(label);
        Scene scene = new Scene(root, 800, 600);

        primaryStage.setTitle("Zoltar");
        primaryStage.setScene(scene);
        primaryStage.show();
        
        logger.info("Zoltar started successfully.");
    }

    private void handleIngestion(Map<String, String> params) {
        String topicIdStr = params.get("topic-id");
        String pdfPathStr = params.get("pdf");

        if (topicIdStr == null || pdfPathStr == null) {
            logger.error("Usage: --ingest --topic-id=<id> --pdf=<path>");
            return;
        }

        try {
            int topicId = Integer.parseInt(topicIdStr);
            Path pdfPath = Paths.get(pdfPathStr);
            
            // Ensure topic exists (create if not for testing)
            TopicDao topicDao = new TopicDao();
            if (topicDao.findById(topicId).isEmpty()) {
                logger.info("Topic {} not found, creating temporary topic.", topicId);
                topicDao.create("Test Topic " + topicId, "test query", "Created by CLI");
            }

            PdfIngestionService ingestionService = new PdfIngestionService();
            ingestionService.ingestPdf(topicId, pdfPath);
            logger.info("Ingestion completed successfully.");

        } catch (Exception e) {
            logger.error("Ingestion failed", e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

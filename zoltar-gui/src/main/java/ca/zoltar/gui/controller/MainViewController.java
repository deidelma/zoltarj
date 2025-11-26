package ca.zoltar.gui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.Parent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Main view controller handling navigation between different views.
 */
public class MainViewController {
    private static final Logger logger = LoggerFactory.getLogger(MainViewController.class);
    
    @FXML private StackPane contentArea;
    @FXML private Label statusLabel;
    @FXML private ProgressBar statusProgress;
    
    @FXML private Button btnTopics;
    @FXML private Button btnLibrary;
    @FXML private Button btnPubMed;
    @FXML private Button btnEvaluate;
    @FXML private Button btnResults;
    @FXML private Button btnSettings;
    
    private Parent currentView;
    
    @FXML
    public void initialize() {
        logger.info("MainViewController initialized");
        // Load default view (Topics)
        showTopics();
    }
    
    @FXML
    private void showTopics() {
        loadView("/ca/zoltar/gui/fxml/TopicsView.fxml", "Topics");
        setActiveButton(btnTopics);
    }
    
    @FXML
    private void showLibrary() {
        loadView("/ca/zoltar/gui/fxml/LibraryView.fxml", "Library");
        setActiveButton(btnLibrary);
    }
    
    @FXML
    private void showPubMed() {
        loadView("/ca/zoltar/gui/fxml/PubMedMonitorView.fxml", "PubMed Monitor");
        setActiveButton(btnPubMed);
    }
    
    @FXML
    private void showEvaluate() {
        loadView("/ca/zoltar/gui/fxml/EvaluateView.fxml", "Evaluate");
        setActiveButton(btnEvaluate);
    }
    
    @FXML
    private void showResults() {
        loadView("/ca/zoltar/gui/fxml/ResultsView.fxml", "Results");
        setActiveButton(btnResults);
    }
    
    @FXML
    private void showSettings() {
        loadView("/ca/zoltar/gui/fxml/SettingsView.fxml", "Settings");
        setActiveButton(btnSettings);
    }
    
    @FXML
    private void handleExit() {
        Platform.exit();
    }
    
    @FXML
    private void handleAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About Zoltar");
        alert.setHeaderText("Zoltar - PubMed Novelty Evaluator");
        alert.setContentText("Version 1.0.0-SNAPSHOT\n\n" +
                "A desktop application for assessing novelty of PubMed abstracts\n" +
                "using hybrid retrieval (semantic + lexical) and LLM evaluation.");
        alert.showAndWait();
    }
    
    private void loadView(String fxmlPath, String viewName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();
            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
            currentView = view;
            setStatus("Loaded " + viewName);
            logger.info("Loaded view: {}", viewName);
        } catch (IOException e) {
            logger.error("Failed to load view: {}", viewName, e);
            showError("Failed to load " + viewName, e.getMessage());
        }
    }
    
    private void setActiveButton(Button activeButton) {
        // Remove active class from all buttons
        btnTopics.getStyleClass().remove("active");
        btnLibrary.getStyleClass().remove("active");
        btnPubMed.getStyleClass().remove("active");
        btnEvaluate.getStyleClass().remove("active");
        btnResults.getStyleClass().remove("active");
        btnSettings.getStyleClass().remove("active");
        
        // Add active class to selected button
        activeButton.getStyleClass().add("active");
    }
    
    public void setStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }
    
    public void showProgress(boolean visible) {
        Platform.runLater(() -> statusProgress.setVisible(visible));
    }
    
    public void setProgress(double progress) {
        Platform.runLater(() -> statusProgress.setProgress(progress));
    }
    
    private void showError(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(title);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
}

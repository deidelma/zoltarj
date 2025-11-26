package ca.zoltar.gui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PubMedMonitorViewController {
    private static final Logger logger = LoggerFactory.getLogger(PubMedMonitorViewController.class);
    
    @FXML private ComboBox<?> topicCombo;
    @FXML private TextField queryField;
    @FXML private TableView<?> abstractsTable;
    
    @FXML
    public void initialize() {
        logger.info("PubMedMonitorViewController initialized");
    }
    
    @FXML
    private void handleDryRun() {
        logger.info("Dry run clicked");
    }
    
    @FXML
    private void handleFetch() {
        logger.info("Fetch clicked");
    }
}

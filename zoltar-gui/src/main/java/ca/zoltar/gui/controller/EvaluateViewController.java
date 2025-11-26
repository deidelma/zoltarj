package ca.zoltar.gui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvaluateViewController {
    private static final Logger logger = LoggerFactory.getLogger(EvaluateViewController.class);
    
    @FXML private ComboBox<?> topicCombo;
    @FXML private ComboBox<?> modelCombo;
    @FXML private Slider alphaSlider;
    @FXML private Label alphaLabel;
    @FXML private Spinner<?> kContextSpinner;
    @FXML private TableView<?> abstractsTable;
    @FXML private ProgressBar progressBar;
    
    @FXML
    public void initialize() {
        logger.info("EvaluateViewController initialized");
        
        // Bind alpha label to slider value
        if (alphaSlider != null && alphaLabel != null) {
            alphaSlider.valueProperty().addListener((obs, oldVal, newVal) -> 
                alphaLabel.setText(String.format("%.2f", newVal.doubleValue()))
            );
        }
    }
    
    @FXML
    private void handleEvaluateSelected() {
        logger.info("Evaluate selected clicked");
    }
    
    @FXML
    private void handleEvaluateAll() {
        logger.info("Evaluate all clicked");
    }
}

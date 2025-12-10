package ca.zoltar.gui.controller;

import ca.zoltar.util.ConfigManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SettingsViewController {
    private static final Logger logger = LoggerFactory.getLogger(SettingsViewController.class);

    @FXML
    private PasswordField apiKeyField;
    @FXML
    private TextField chatModelField;
    @FXML
    private TextField embeddingModelField;
    @FXML
    private Spinner<Integer> chunkSizeSpinner;
    @FXML
    private Spinner<Integer> chunkOverlapSpinner;
    @FXML
    private Spinner<Double> defaultAlphaSpinner;
    @FXML
    private Label statusLabel;

    private final ConfigManager configManager = ConfigManager.getInstance();

    @FXML
    public void initialize() {
        logger.info("SettingsViewController initialized");
        loadSettings();
    }

    @SuppressWarnings("unchecked")
    private void loadSettings() {
        // Load OpenAI settings
        Map<String, Object> openai = (Map<String, Object>) configManager.get("openai");
        if (openai != null) {
            String apiKey = (String) openai.get("apiKey");
            String chatModel = (String) openai.get("chatModel");
            String embeddingModel = (String) openai.get("embeddingModel");

            if (apiKey != null) {
                apiKeyField.setText(apiKey);
            }
            chatModelField.setText(Objects.requireNonNullElse(chatModel, "gpt-4o-mini"));
            embeddingModelField.setText(Objects.requireNonNullElse(embeddingModel, "text-embedding-3-small"));
        } else {
            chatModelField.setText("gpt-4o-mini");
            embeddingModelField.setText("text-embedding-3-small");
        }

        // Load indexing settings
        Map<String, Object> indexing = (Map<String, Object>) configManager.get("indexing");
        if (indexing != null) {
            if (indexing.get("chunkSize") != null) {
                chunkSizeSpinner.getValueFactory().setValue((Integer) indexing.get("chunkSize"));
            }
            if (indexing.get("chunkOverlap") != null) {
                chunkOverlapSpinner.getValueFactory().setValue((Integer) indexing.get("chunkOverlap"));
            }
        }

        setStatus("Settings loaded");
    }

    @FXML
    private void handleSave() {
        try {
            // Save OpenAI settings
            Map<String, Object> openai = new HashMap<>();
            openai.put("apiKey", apiKeyField.getText());
            openai.put("chatModel", chatModelField.getText());
            openai.put("embeddingModel", embeddingModelField.getText());
            configManager.set("openai", openai);

            // Save indexing settings
            Map<String, Object> indexing = new HashMap<>();
            indexing.put("chunkSize", chunkSizeSpinner.getValue());
            indexing.put("chunkOverlap", chunkOverlapSpinner.getValue());
            configManager.set("indexing", indexing);

            configManager.saveConfig();

            setStatus("Settings saved successfully");
            logger.info("Settings saved");

            // Show confirmation dialog
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Settings Saved");
            alert.setHeaderText(null);
            alert.setContentText(
                    "Settings have been saved successfully. Restart the application for changes to take full effect.");
            alert.showAndWait();

        } catch (Exception e) {
            logger.error("Failed to save settings", e);
            setStatus("Error: " + e.getMessage());

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Save Failed");
            alert.setHeaderText(null);
            alert.setContentText("Failed to save settings: " + e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void handleReset() {
        chatModelField.setText("gpt-4o-mini");
        embeddingModelField.setText("text-embedding-3-small");
        chunkSizeSpinner.getValueFactory().setValue(512);
        chunkOverlapSpinner.getValueFactory().setValue(128);
        setStatus("Reset to defaults");
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }
}

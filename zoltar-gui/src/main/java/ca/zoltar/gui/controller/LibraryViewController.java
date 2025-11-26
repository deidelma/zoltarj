package ca.zoltar.gui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.TableView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LibraryViewController {
    private static final Logger logger = LoggerFactory.getLogger(LibraryViewController.class);
    
    @FXML private TableView<?> documentsTable;
    
    @FXML
    public void initialize() {
        logger.info("LibraryViewController initialized");
    }
    
    @FXML
    private void handleAddPdfs() {
        logger.info("Add PDFs clicked");
    }
    
    @FXML
    private void handleReindex() {
        logger.info("Re-index clicked");
    }
    
    @FXML
    private void handleDelete() {
        logger.info("Delete clicked");
    }
}

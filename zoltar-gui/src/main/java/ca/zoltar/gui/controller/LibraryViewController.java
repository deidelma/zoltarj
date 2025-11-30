package ca.zoltar.gui.controller;

import ca.zoltar.core.service.IndexingService;
import ca.zoltar.core.service.PdfIngestionService;
import ca.zoltar.db.ChunkDao;
import ca.zoltar.db.DocumentDao;
import ca.zoltar.db.TopicDao;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

public class LibraryViewController {
    private static final Logger logger = LoggerFactory.getLogger(LibraryViewController.class);

    @FXML
    private ComboBox<TopicDao.Topic> topicCombo;
    @FXML
    private TableView<DocumentRow> documentsTable;
    @FXML
    private TableColumn<DocumentRow, Integer> idColumn;
    @FXML
    private TableColumn<DocumentRow, String> titleColumn;
    @FXML
    private TableColumn<DocumentRow, String> filenameColumn;
    @FXML
    private TableColumn<DocumentRow, Integer> chunksColumn;
    @FXML
    private TableColumn<DocumentRow, String> statusColumn;
    @FXML
    private TableColumn<DocumentRow, String> addedColumn;
    @FXML
    private Button btnAddPdfs;
    @FXML
    private Button btnReindex;
    @FXML
    private Button btnDelete;
    @FXML
    private Label statusLabel;

    private final ObservableList<DocumentRow> documentsList = FXCollections.observableArrayList();
    private final TopicDao topicDao;
    private final DocumentDao documentDao;
    private final ChunkDao chunkDao;
    private final PdfIngestionService pdfIngestionService;
    private final IndexingService indexingService;

    public LibraryViewController() {
        this(new TopicDao(), new DocumentDao(), new ChunkDao(), new PdfIngestionService(), new IndexingService());
    }

    LibraryViewController(TopicDao topicDao, DocumentDao documentDao, ChunkDao chunkDao,
            PdfIngestionService pdfIngestionService, IndexingService indexingService) {
        this.topicDao = topicDao;
        this.documentDao = documentDao;
        this.chunkDao = chunkDao;
        this.pdfIngestionService = pdfIngestionService;
        this.indexingService = indexingService;
    }

    @FXML
    public void initialize() {
        logger.info("LibraryViewController initialized");

        // Setup table columns
        idColumn.setCellValueFactory(
                data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getId()).asObject());
        titleColumn.setCellValueFactory(
                data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getTitle()));
        filenameColumn.setCellValueFactory(
                data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getFilename()));
        chunksColumn.setCellValueFactory(
                data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getChunkCount()).asObject());
        statusColumn.setCellValueFactory(
                data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getStatus()));
        addedColumn.setCellValueFactory(
                data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getAdded()));

        documentsTable.setItems(documentsList);

        // Setup topic combo box
        topicCombo.setConverter(new StringConverter<TopicDao.Topic>() {
            @Override
            public String toString(TopicDao.Topic topic) {
                return topic == null ? "" : topic.name();
            }

            @Override
            public TopicDao.Topic fromString(String string) {
                return null; // Not needed for read-only combo
            }
        });

        topicCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadDocumentsForTopic(newVal.id());
                updateActionButtons(true);
            } else {
                documentsList.clear();
                updateActionButtons(false);
            }
        });

        updateActionButtons(false);
        loadTopics();
    }

    @FXML
    private void handleAddPdfs() {
        TopicDao.Topic selectedTopic = topicCombo.getSelectionModel().getSelectedItem();
        if (selectedTopic == null) {
            showError("No Topic Selected", "Please select a topic first.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select PDF Files");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));

        // Get the stage from any component
        Stage stage = (Stage) btnAddPdfs.getScene().getWindow();
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(stage);

        if (selectedFiles == null || selectedFiles.isEmpty()) {
            return;
        }

        // Process PDFs in background
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                int total = selectedFiles.size();
                int processed = 0;

                for (File file : selectedFiles) {
                    updateMessage("Processing " + file.getName() + "...");
                    updateProgress(processed, total);

                    try {
                        // Ingest PDF
                        pdfIngestionService.ingestPdf(selectedTopic.id(), file.toPath());

                        // Get the newly created document
                        List<DocumentDao.Document> docs = documentDao.findByTopicId(selectedTopic.id());
                        if (!docs.isEmpty()) {
                            DocumentDao.Document lastDoc = docs.get(docs.size() - 1);

                            // Index the document
                            updateMessage("Indexing " + file.getName() + "...");
                            indexingService.indexDocument(lastDoc.id());
                        }

                        logger.info("Successfully ingested and indexed: {}", file.getName());
                    } catch (Exception e) {
                        logger.error("Failed to process {}: {}", file.getName(), e.getMessage(), e);
                        Platform.runLater(() -> showError("Error Processing " + file.getName(), e.getMessage()));
                    }

                    processed++;
                }

                updateProgress(total, total);
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            setStatus("PDF processing completed");
            loadDocumentsForTopic(selectedTopic.id());
            showInfo("Success", "PDF files have been ingested and indexed successfully.");
        });

        task.setOnFailed(event -> {
            Throwable error = task.getException();
            logger.error("PDF processing failed", error);
            setStatus("PDF processing failed");
            showError("Processing Failed", error != null ? error.getMessage() : "Unknown error");
        });

        // Show progress
        setStatus("Processing PDFs...");

        // Run in background thread
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void handleReindex() {
        DocumentRow selected = documentsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("No Document Selected", "Please select a document to re-index.");
            return;
        }

        // Re-index in background
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Re-indexing document...");
                indexingService.indexDocument(selected.getId());
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            setStatus("Re-indexing completed");
            TopicDao.Topic selectedTopic = topicCombo.getSelectionModel().getSelectedItem();
            if (selectedTopic != null) {
                loadDocumentsForTopic(selectedTopic.id());
            }
            showInfo("Success", "Document has been re-indexed successfully.");
        });

        task.setOnFailed(event -> {
            Throwable error = task.getException();
            logger.error("Re-indexing failed", error);
            setStatus("Re-indexing failed");
            showError("Re-indexing Failed", error != null ? error.getMessage() : "Unknown error");
        });

        setStatus("Re-indexing...");
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void handleDelete() {
        DocumentRow selected = documentsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("No Document Selected", "Please select a document to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Document");
        confirm.setHeaderText("Delete \"" + selected.getTitle() + "\"?");
        confirm.setContentText("This will remove the document and all its chunks. This action cannot be undone.");

        confirm.showAndWait().ifPresent(response -> {
            if (response.getButtonData().isDefaultButton()) {
                try {
                    // TODO: Implement proper cascade delete in DAO
                    // For now, just show a message
                    showInfo("Not Implemented", "Document deletion is not yet implemented.");
                    logger.warn("Document deletion not yet implemented for ID: {}", selected.getId());
                } catch (Exception e) {
                    logger.error("Failed to delete document", e);
                    showError("Delete Failed", e.getMessage());
                }
            }
        });
    }

    private void loadTopics() {
        try {
            List<TopicDao.Topic> topics = topicDao.findAll();
            topicCombo.setItems(FXCollections.observableArrayList(topics));

            if (!topics.isEmpty()) {
                topicCombo.getSelectionModel().selectFirst();
            }

            logger.info("Loaded {} topics into combo box", topics.size());
            setStatus(String.format("%d topic%s available", topics.size(), topics.size() == 1 ? "" : "s"));
        } catch (SQLException e) {
            logger.error("Failed to load topics", e);
            setStatus("Error loading topics: " + e.getMessage());
        }
    }

    private void loadDocumentsForTopic(int topicId) {
        try {
            List<DocumentDao.Document> documents = documentDao.findByTopicId(topicId);
            documentsList.clear();

            for (DocumentDao.Document doc : documents) {
                int chunkCount = chunkDao.countByDocumentId(doc.id());
                String status = chunkCount > 0 ? "Indexed" : "Not Indexed";
                String filename = extractFilename(doc.sourcePath());

                documentsList.add(new DocumentRow(
                        doc.id(),
                        safe(doc.title()),
                        filename,
                        chunkCount,
                        status,
                        doc.addedAt()));
            }

            logger.info("Loaded {} documents for topic {}", documents.size(), topicId);
            setStatus(String.format("%d document%s", documents.size(), documents.size() == 1 ? "" : "s"));
        } catch (SQLException e) {
            logger.error("Failed to load documents", e);
            setStatus("Error loading documents: " + e.getMessage());
        }
    }

    private void updateActionButtons(boolean enabled) {
        if (btnAddPdfs != null)
            btnAddPdfs.setDisable(!enabled);
        if (btnReindex != null)
            btnReindex.setDisable(!enabled);
        if (btnDelete != null)
            btnDelete.setDisable(!enabled);
    }

    private void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message == null ? "" : message);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String extractFilename(String path) {
        if (path == null)
            return "";
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Inner class for table rows
    public static class DocumentRow {
        private final int id;
        private final String title;
        private final String filename;
        private final int chunkCount;
        private final String status;
        private final String added;

        public DocumentRow(int id, String title, String filename, int chunkCount, String status, String added) {
            this.id = id;
            this.title = title;
            this.filename = filename;
            this.chunkCount = chunkCount;
            this.status = status;
            this.added = added;
        }

        public int getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getFilename() {
            return filename;
        }

        public int getChunkCount() {
            return chunkCount;
        }

        public String getStatus() {
            return status;
        }

        public String getAdded() {
            return added;
        }
    }
}

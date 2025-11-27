package ca.zoltar.gui.controller;

import ca.zoltar.db.AbstractDao;
import ca.zoltar.db.DocumentDao;
import ca.zoltar.db.TopicDao;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class TopicsViewController {
    private static final Logger logger = LoggerFactory.getLogger(TopicsViewController.class);
    
    @FXML private TableView<TopicRow> topicsTable;
    @FXML private TableColumn<TopicRow, Integer> idColumn;
    @FXML private TableColumn<TopicRow, String> nameColumn;
    @FXML private TableColumn<TopicRow, String> queryColumn;
    @FXML private TableColumn<TopicRow, Integer> docsColumn;
    @FXML private TableColumn<TopicRow, Integer> abstractsColumn;
    @FXML private TableColumn<TopicRow, String> updatedColumn;
    @FXML private Button btnEdit;
    @FXML private Button btnDelete;
    @FXML private Label statusLabel;
    
    private final ObservableList<TopicRow> topicsList = FXCollections.observableArrayList();
    private final TopicDao topicDao;
    private final DocumentDao documentDao;
    private final AbstractDao abstractDao;

    public TopicsViewController() {
        this(new TopicDao(), new DocumentDao(), new AbstractDao());
    }

    TopicsViewController(TopicDao topicDao, DocumentDao documentDao, AbstractDao abstractDao) {
        this.topicDao = topicDao;
        this.documentDao = documentDao;
        this.abstractDao = abstractDao;
    }
    
    @FXML
    public void initialize() {
        logger.info("TopicsViewController initialized");
        
        // Setup table columns
        idColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getId()).asObject());
        nameColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getName()));
        queryColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getQuery()));
        docsColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getDocCount()).asObject());
        abstractsColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getAbstractCount()).asObject());
        updatedColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getUpdated()));
        
        topicsTable.setItems(topicsList);
        topicsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> updateActionButtons(newSelection != null));
        updateActionButtons(false);

        loadTopics();
    }
    
    @FXML
    void handleNewTopic() {
        logger.info("New topic clicked");
        try {
            Optional<TopicFormData> result = showTopicDialog("New Topic", null);
            if (result.isEmpty()) {
                setStatus("Topic creation cancelled");
                return;
            }

            TopicFormData form = result.get();
            topicDao.create(form.name(), form.query(), form.notes());
            showInfo("Create Topic", "Topic created successfully.");
            loadTopics();
        } catch (SQLException e) {
            logger.error("Failed to create topic", e);
            showError("Failed to create topic", e.getMessage());
        }
    }
    
    @FXML
    void handleEditTopic() {
        TopicRow selected = topicsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("Edit Topic", "Select a topic to edit.");
            return;
        }

        try {
            Optional<TopicDao.Topic> topicOpt = topicDao.findById(selected.getId());
            if (topicOpt.isEmpty()) {
                showError("Edit Topic", "Unable to locate the selected topic.");
                loadTopics();
                return;
            }

            Optional<TopicFormData> result = showTopicDialog("Edit Topic", topicOpt.get());
            if (result.isEmpty()) {
                setStatus("Edit cancelled");
                return;
            }

            TopicFormData form = result.get();
            topicDao.update(selected.getId(), form.name(), form.query(), form.notes());
            showInfo("Edit Topic", "Topic updated successfully.");
            loadTopics();
        } catch (SQLException e) {
            logger.error("Failed to edit topic", e);
            showError("Failed to edit topic", e.getMessage());
        }
    }
    
    @FXML
    void handleDeleteTopic() {
        TopicRow selected = topicsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("Delete Topic", "Select a topic to delete.");
            return;
        }

        if (!confirmTopicDeletion(selected)) {
            setStatus("Deletion cancelled");
            return;
        }

        try {
            topicDao.deleteCascade(selected.getId());
            showInfo("Delete Topic", "Topic deleted successfully.");
            loadTopics();
        } catch (SQLException e) {
            logger.error("Failed to delete topic", e);
            showError("Failed to delete topic", e.getMessage());
        }
    }

    protected boolean confirmTopicDeletion(TopicRow selected) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Topic");
        confirm.setHeaderText("Delete topic \"" + selected.getName() + "\"?");
        confirm.setContentText("This will remove " + selected.getDocCount() + " document(s) and " +
                " " + selected.getAbstractCount() + " abstract(s). This action cannot be undone.");

        Optional<ButtonType> result = confirm.showAndWait();
        return result.isPresent() && result.get().getButtonData() == ButtonBar.ButtonData.OK_DONE;
    }
    
    protected void loadTopics() {
        try {
            List<TopicDao.Topic> topics = topicDao.findAll();
            topicsList.clear();
            
            for (TopicDao.Topic topic : topics) {
                topicsList.add(new TopicRow(
                    topic.id(),
                    topic.name(),
                    safe(topic.queryString()),
                    documentCount(topic.id()),
                    abstractCount(topic.id()),
                    topic.updatedAt()
                ));
            }
            
            logger.info("Loaded {} topics", topics.size());
            setStatus(String.format("%d topic%s", topics.size(), topics.size() == 1 ? "" : "s"));
            updateActionButtons(false);
            
        } catch (SQLException e) {
            logger.error("Failed to load topics", e);
            showError("Failed to load topics", e.getMessage());
        }
    }
    
    protected void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
        setStatus(message);
    }
    
    protected void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
        setStatus(message);
    }

    protected Optional<TopicFormData> showTopicDialog(String title, TopicDao.Topic topic) {
        Dialog<TopicFormData> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(null);

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField nameField = new TextField(topic != null ? topic.name() : "");
        nameField.setPromptText("Topic name");

        TextArea queryArea = new TextArea(topic != null ? safe(topic.queryString()) : "");
        queryArea.setPromptText("PubMed query");
        queryArea.setPrefRowCount(3);

        TextArea notesArea = new TextArea(topic != null ? safe(topic.notes()) : "");
        notesArea.setPromptText("Notes");
        notesArea.setPrefRowCount(3);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, new Label("Name"), nameField);
        grid.addRow(1, new Label("Query"), queryArea);
        grid.addRow(2, new Label("Notes"), notesArea);

        dialog.getDialogPane().setContent(grid);

        Node saveButton = dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.disableProperty().bind(nameField.textProperty().isEmpty());

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return new TopicFormData(
                        nameField.getText().trim(),
                        queryArea.getText().trim(),
                        notesArea.getText().trim()
                );
            }
            return null;
        });

        return dialog.showAndWait();
    }

    private void updateActionButtons(boolean enabled) {
        if (btnEdit != null) {
            btnEdit.setDisable(!enabled);
        }
        if (btnDelete != null) {
            btnDelete.setDisable(!enabled);
        }
    }

    protected void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message == null ? "" : message);
        }
    }

    private int documentCount(int topicId) {
        try {
            return documentDao.countByTopicId(topicId);
        } catch (SQLException e) {
            logger.warn("Failed to count documents for topic {}", topicId, e);
            return 0;
        }
    }

    private int abstractCount(int topicId) {
        try {
            return abstractDao.countByTopic(topicId);
        } catch (SQLException e) {
            logger.warn("Failed to count abstracts for topic {}", topicId, e);
            return 0;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
    
    // Inner class for table rows
    public static class TopicRow {
        private final int id;
        private final String name;
        private final String query;
        private final int docCount;
        private final int abstractCount;
        private final String updated;
        
        public TopicRow(int id, String name, String query, int docCount, int abstractCount, String updated) {
            this.id = id;
            this.name = name;
            this.query = query;
            this.docCount = docCount;
            this.abstractCount = abstractCount;
            this.updated = updated;
        }
        
        public int getId() { return id; }
        public String getName() { return name; }
        public String getQuery() { return query; }
        public int getDocCount() { return docCount; }
        public int getAbstractCount() { return abstractCount; }
        public String getUpdated() { return updated; }
    }

    ObservableList<TopicRow> getDisplayedTopics() {
        return FXCollections.unmodifiableObservableList(topicsList);
    }

    protected record TopicFormData(String name, String query, String notes) {}
}

package ca.zoltar.gui.controller;

import ca.zoltar.db.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

public class TopicsViewController {
    private static final Logger logger = LoggerFactory.getLogger(TopicsViewController.class);
    
    @FXML private TableView<TopicRow> topicsTable;
    @FXML private TableColumn<TopicRow, Integer> idColumn;
    @FXML private TableColumn<TopicRow, String> nameColumn;
    @FXML private TableColumn<TopicRow, String> queryColumn;
    @FXML private TableColumn<TopicRow, Integer> docsColumn;
    @FXML private TableColumn<TopicRow, Integer> abstractsColumn;
    @FXML private TableColumn<TopicRow, String> updatedColumn;
    
    private final ObservableList<TopicRow> topicsList = FXCollections.observableArrayList();
    private final TopicDao topicDao = new TopicDao();
    
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
        
        loadTopics();
    }
    
    @FXML
    private void handleNewTopic() {
        logger.info("New topic clicked");
        showInfo("Create Topic", "This feature will be implemented soon.");
    }
    
    @FXML
    private void handleEditTopic() {
        logger.info("Edit topic clicked");
        showInfo("Edit Topic", "This feature will be implemented soon.");
    }
    
    @FXML
    private void handleDeleteTopic() {
        logger.info("Delete topic clicked");
        showInfo("Delete Topic", "This feature will be implemented soon.");
    }
    
    private void loadTopics() {
        try {
            List<TopicDao.Topic> topics = topicDao.findAll();
            topicsList.clear();
            
            for (TopicDao.Topic topic : topics) {
                topicsList.add(new TopicRow(
                    topic.id(),
                    topic.name(),
                    topic.queryString(),
                    0,  // TODO: Get actual document count
                    0,  // TODO: Get actual abstract count
                    topic.updatedAt()
                ));
            }
            
            logger.info("Loaded {} topics", topics.size());
            
        } catch (SQLException e) {
            logger.error("Failed to load topics", e);
            showError("Failed to load topics", e.getMessage());
        }
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
}

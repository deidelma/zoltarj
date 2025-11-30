package ca.zoltar.gui.controller;

import ca.zoltar.core.service.PubMedMonitorService;
import ca.zoltar.db.AbstractDao;
import ca.zoltar.db.TopicDao;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class PubMedMonitorViewController {
    private static final Logger logger = LoggerFactory.getLogger(PubMedMonitorViewController.class);

    @FXML
    private ComboBox<TopicDao.Topic> topicCombo;
    @FXML
    private TextField queryField;
    @FXML
    private TableView<AbstractRow> abstractsTable;
    @FXML
    private TableColumn<AbstractRow, String> pmidColumn;
    @FXML
    private TableColumn<AbstractRow, String> titleColumn;
    @FXML
    private TableColumn<AbstractRow, String> journalColumn;
    @FXML
    private TableColumn<AbstractRow, String> dateColumn;
    @FXML
    private TableColumn<AbstractRow, String> statusColumn;
    @FXML
    private Button dryRunButton;
    @FXML
    private Button fetchButton;
    @FXML
    private Label statusLabel;

    private TopicDao topicDao;
    private AbstractDao abstractDao;
    private PubMedMonitorService monitorService;
    private ObservableList<AbstractRow> abstractData;

    @FXML
    public void initialize() {
        logger.info("PubMedMonitorViewController initialized");

        try {
            // Initialize DAOs and services
            topicDao = new TopicDao();
            abstractDao = new AbstractDao();
            monitorService = new PubMedMonitorService();
            abstractData = FXCollections.observableArrayList();

            // Set up table columns
            pmidColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().pmid()));
            titleColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().title()));
            journalColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().journal()));
            dateColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().pubDate()));
            statusColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().status()));

            abstractsTable.setItems(abstractData);

            // Configure topic combo box
            topicCombo.setConverter(new StringConverter<TopicDao.Topic>() {
                @Override
                public String toString(TopicDao.Topic topic) {
                    return topic == null ? "" : topic.name();
                }

                @Override
                public TopicDao.Topic fromString(String string) {
                    return null;
                }
            });

            // Handle topic selection
            topicCombo.setOnAction(event -> handleTopicSelected());

            // Load topics
            loadTopics();

        } catch (Exception e) {
            logger.error("Failed to initialize PubMedMonitorViewController", e);
            showError("Initialization Error", "Failed to initialize PubMed Monitor: " + e.getMessage());
        }
    }

    private void loadTopics() {
        Task<List<TopicDao.Topic>> task = new Task<>() {
            @Override
            protected List<TopicDao.Topic> call() throws Exception {
                return topicDao.findAll();
            }
        };

        task.setOnSucceeded(event -> {
            List<TopicDao.Topic> topics = task.getValue();
            topicCombo.getItems().setAll(topics);
            if (!topics.isEmpty()) {
                logger.info("Loaded {} topics", topics.size());
            }
        });

        task.setOnFailed(event -> {
            logger.error("Failed to load topics", task.getException());
            showError("Load Error", "Failed to load topics: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    private void handleTopicSelected() {
        TopicDao.Topic topic = topicCombo.getValue();
        if (topic != null) {
            queryField.setText(topic.queryString());
            dryRunButton.setDisable(false);
            fetchButton.setDisable(false);
            loadStoredAbstracts(topic.id());
        } else {
            queryField.clear();
            dryRunButton.setDisable(true);
            fetchButton.setDisable(true);
            abstractData.clear();
        }
    }

    private void loadStoredAbstracts(int topicId) {
        Task<List<AbstractDao.Abstract>> task = new Task<>() {
            @Override
            protected List<AbstractDao.Abstract> call() throws Exception {
                return abstractDao.findByTopic(topicId);
            }
        };

        task.setOnSucceeded(event -> {
            List<AbstractDao.Abstract> abstracts = task.getValue();
            abstractData.clear();
            for (AbstractDao.Abstract abs : abstracts) {
                abstractData.add(new AbstractRow(
                        abs.pmid(),
                        abs.title(),
                        abs.journal(),
                        abs.pubDate(),
                        "Stored"));
            }
            updateStatus(abstracts.size() + " abstracts loaded");
            logger.info("Loaded {} stored abstracts for topic {}", abstracts.size(), topicId);
        });

        task.setOnFailed(event -> {
            logger.error("Failed to load abstracts", task.getException());
            showError("Load Error", "Failed to load abstracts: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    @FXML
    private void handleDryRun() {
        TopicDao.Topic topic = topicCombo.getValue();
        if (topic == null) {
            showWarning("No Topic Selected", "Please select a topic first.");
            return;
        }

        logger.info("Starting dry run for topic: {}", topic.name());
        updateStatus("Running dry search...");

        dryRunButton.setDisable(true);
        fetchButton.setDisable(true);

        Task<PubMedMonitorService.SearchPreview> task = new Task<>() {
            @Override
            protected PubMedMonitorService.SearchPreview call() throws Exception {
                return monitorService.dryRunSearch(topic.id(), 100);
            }
        };

        task.setOnSucceeded(event -> {
            PubMedMonitorService.SearchPreview preview = task.getValue();

            // Clear and show preview results
            abstractData.clear();
            for (String pmid : preview.newPmids()) {
                abstractData.add(new AbstractRow(pmid, "New result", "", "", "New"));
            }
            for (String pmid : preview.seenPmids()) {
                abstractData.add(new AbstractRow(pmid, "Already seen", "", "", "Seen"));
            }

            String message = String.format("Found %d results: %d new, %d already seen",
                    preview.totalCount(), preview.newCount(), preview.seenCount());
            updateStatus(message);

            showInfo("Dry Run Complete", message);

            logger.info("Dry run complete: {}", message);
            dryRunButton.setDisable(false);
            fetchButton.setDisable(false);
        });

        task.setOnFailed(event -> {
            logger.error("Dry run failed", task.getException());
            showError("Dry Run Failed", "Failed to perform dry run: " + task.getException().getMessage());
            updateStatus("Dry run failed");
            dryRunButton.setDisable(false);
            fetchButton.setDisable(false);
        });

        new Thread(task).start();
    }

    @FXML
    private void handleFetch() {
        TopicDao.Topic topic = topicCombo.getValue();
        if (topic == null) {
            showWarning("No Topic Selected", "Please select a topic first.");
            return;
        }

        logger.info("Starting fetch for topic: {}", topic.name());
        updateStatus("Fetching new abstracts from PubMed...");

        dryRunButton.setDisable(true);
        fetchButton.setDisable(true);

        Task<PubMedMonitorService.FetchStats> task = new Task<>() {
            @Override
            protected PubMedMonitorService.FetchStats call() throws Exception {
                return monitorService.fetchNewAbstracts(topic.id(), 100);
            }
        };

        task.setOnSucceeded(event -> {
            PubMedMonitorService.FetchStats stats = task.getValue();

            String message = String.format("Fetched %d new abstracts (from %d candidates)",
                    stats.storedAbstractCount(), stats.candidateCount());
            updateStatus(message);

            // Reload stored abstracts
            loadStoredAbstracts(topic.id());

            showInfo("Fetch Complete", message);

            logger.info("Fetch complete: {}", stats);
            dryRunButton.setDisable(false);
            fetchButton.setDisable(false);
        });

        task.setOnFailed(event -> {
            logger.error("Fetch failed", task.getException());
            showError("Fetch Failed", "Failed to fetch abstracts: " + task.getException().getMessage());
            updateStatus("Fetch failed");
            dryRunButton.setDisable(false);
            fetchButton.setDisable(false);
        });

        new Thread(task).start();
    }

    private void updateStatus(String message) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(message);
            }
        });
    }

    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showWarning(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showInfo(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Row model for the abstracts table.
     */
    public record AbstractRow(
            String pmid,
            String title,
            String journal,
            String pubDate,
            String status) {
    }
}

package ca.zoltar.gui.controller;

import ca.zoltar.core.service.EvaluationService;
import ca.zoltar.core.service.HybridRetrievalService;
import ca.zoltar.db.AbstractDao;
import ca.zoltar.db.DocumentDao;
import ca.zoltar.db.EvaluationResultDao;
import ca.zoltar.db.EvaluationRunDao;
import ca.zoltar.db.TopicDao;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class EvaluateViewController {
    private static final Logger logger = LoggerFactory.getLogger(EvaluateViewController.class);

    @FXML
    private ComboBox<TopicDao.Topic> topicCombo;
    @FXML
    private ComboBox<String> modelCombo;
    @FXML
    private Slider alphaSlider;
    @FXML
    private Label alphaLabel;
    @FXML
    private Spinner<Integer> kContextSpinner;
    @FXML
    private TableView<AbstractRow> abstractsTable;
    @FXML
    private TableColumn<AbstractRow, Boolean> selectColumn;
    @FXML
    private TableColumn<AbstractRow, String> pmidColumn;
    @FXML
    private TableColumn<AbstractRow, String> titleColumn;
    @FXML
    private TableColumn<AbstractRow, String> journalColumn;
    @FXML
    private TableColumn<AbstractRow, String> statusColumn;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label statusLabel;
    @FXML
    private Button evaluateSelectedButton;
    @FXML
    private Button evaluateAllButton;

    private TopicDao topicDao;
    private AbstractDao abstractDao;
    private DocumentDao documentDao;
    private EvaluationService evaluationService;
    private EvaluationRunDao evaluationRunDao;
    private EvaluationResultDao evaluationResultDao;
    private HybridRetrievalService retrievalService;
    private ObservableList<AbstractRow> abstractData;

    @FXML
    public void initialize() {
        logger.info("EvaluateViewController initialized");

        try {
            // Initialize DAOs and services
            topicDao = new TopicDao();
            abstractDao = new AbstractDao();
            documentDao = new DocumentDao();
            evaluationService = new EvaluationService();
            evaluationRunDao = new EvaluationRunDao();
            evaluationResultDao = new EvaluationResultDao();
            retrievalService = new HybridRetrievalService();
            abstractData = FXCollections.observableArrayList();

            // Set up table columns
            selectColumn.setCellValueFactory(data -> data.getValue().selectedProperty());
            selectColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectColumn));
            pmidColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().pmid()));
            titleColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().title()));
            journalColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().journal()));
            statusColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().status()));

            abstractsTable.setItems(abstractData);
            abstractsTable.setEditable(true);

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

            // Configure model combo box
            modelCombo.setItems(FXCollections.observableArrayList(
                    "gpt-4",
                    "gpt-4-turbo",
                    "gpt-3.5-turbo"));
            modelCombo.setValue("gpt-4");

            // Configure K context spinner
            SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 100, 30);
            kContextSpinner.setValueFactory(valueFactory);

            // Bind alpha label to slider value
            if (alphaSlider != null && alphaLabel != null) {
                alphaSlider.valueProperty().addListener(
                        (obs, oldVal, newVal) -> alphaLabel.setText(String.format("%.2f", newVal.doubleValue())));
            }

            // Handle topic selection
            topicCombo.setOnAction(event -> handleTopicSelected());

            // Load topics
            loadTopics();

        } catch (Exception e) {
            logger.error("Failed to initialize EvaluateViewController", e);
            showError("Initialization Error", "Failed to initialize Evaluate view: " + e.getMessage());
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
            loadAbstracts(topic.id());
            evaluateSelectedButton.setDisable(false);
            evaluateAllButton.setDisable(false);
        } else {
            abstractData.clear();
            evaluateSelectedButton.setDisable(true);
            evaluateAllButton.setDisable(true);
        }
    }

    private void loadAbstracts(int topicId) {
        Task<List<AbstractDao.Abstract>> task = new Task<>() {
            @Override
            protected List<AbstractDao.Abstract> call() throws Exception {
                // Get abstracts
                List<AbstractDao.Abstract> abstracts = abstractDao.findByTopic(topicId);

                // Get evaluation runs for this topic to determine status
                List<EvaluationRunDao.EvaluationRun> runs = evaluationRunDao.findByTopicId(topicId);

                return abstracts;
            }
        };

        task.setOnSucceeded(event -> {
            List<AbstractDao.Abstract> abstracts = task.getValue();
            abstractData.clear();

            for (AbstractDao.Abstract abs : abstracts) {
                // Check if this abstract has been evaluated
                String status = getEvaluationStatus(topicId, abs.pmid());
                abstractData.add(new AbstractRow(
                        abs.pmid(),
                        abs.title(),
                        abs.journal(),
                        status,
                        false));
            }

            updateStatus(abstracts.size() + " abstracts loaded");
            logger.info("Loaded {} abstracts for topic {}", abstracts.size(), topicId);
        });

        task.setOnFailed(event -> {
            logger.error("Failed to load abstracts", task.getException());
            showError("Load Error", "Failed to load abstracts: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    private String getEvaluationStatus(int topicId, String pmid) {
        try {
            List<EvaluationRunDao.EvaluationRun> runs = evaluationRunDao.findByTopicAndPmid(topicId, pmid);
            if (runs.isEmpty()) {
                return "Not Evaluated";
            }

            // Get the most recent evaluation
            EvaluationRunDao.EvaluationRun latestRun = runs.get(0);
            EvaluationResultDao.EvaluationResult result = evaluationResultDao.findByRunId(latestRun.id());

            if (result != null) {
                return result.noveltyLabel() + " (" + result.noveltyScore() + "/10)";
            }
            return "Evaluated";

        } catch (Exception e) {
            logger.warn("Failed to get evaluation status for PMID {}: {}", pmid, e.getMessage());
            return "Unknown";
        }
    }

    @FXML
    private void handleEvaluateSelected() {
        List<AbstractRow> selected = abstractData.stream()
                .filter(row -> row.selected.get())
                .toList();

        if (selected.isEmpty()) {
            showWarning("No Selection", "Please select at least one abstract to evaluate.");
            return;
        }

        TopicDao.Topic topic = topicCombo.getValue();
        if (topic == null) {
            showWarning("No Topic", "Please select a topic first.");
            return;
        }

        logger.info("Evaluating {} selected abstracts", selected.size());
        evaluateAbstracts(topic.id(), selected);
    }

    @FXML
    private void handleEvaluateAll() {
        if (abstractData.isEmpty()) {
            showWarning("No Abstracts", "No abstracts available to evaluate.");
            return;
        }

        TopicDao.Topic topic = topicCombo.getValue();
        if (topic == null) {
            showWarning("No Topic", "Please select a topic first.");
            return;
        }

        logger.info("Evaluating all {} abstracts", abstractData.size());
        evaluateAbstracts(topic.id(), abstractData);
    }

    private void evaluateAbstracts(int topicId, List<AbstractRow> abstracts) {
        // First check if there are any documents indexed for this topic
        try {
            int docCount = documentDao.countByTopicId(topicId);
            if (docCount == 0) {
                showWarning("No Documents Indexed",
                        "This topic has no indexed documents for context retrieval.\n\n" +
                                "Please add and index PDF documents in the Library before evaluating abstracts.\n\n" +
                                "Evaluation requires existing documents to provide context for novelty assessment.");
                return;
            }
            logger.info("Found {} indexed documents for topic {}", docCount, topicId);
        } catch (Exception e) {
            logger.error("Failed to check document count", e);
            showError("Database Error", "Failed to check indexed documents: " + e.getMessage());
            return;
        }

        // Update retrieval service parameters
        double alpha = alphaSlider.getValue();
        int kContext = kContextSpinner.getValue();
        retrievalService.setAlpha(alpha);
        retrievalService.setKContext(kContext);

        // Disable buttons during evaluation
        evaluateSelectedButton.setDisable(true);
        evaluateAllButton.setDisable(true);
        topicCombo.setDisable(true);

        progressBar.setVisible(true);
        progressBar.setProgress(0);

        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                int total = abstracts.size();

                for (int i = 0; i < total; i++) {
                    AbstractRow row = abstracts.get(i);

                    updateMessage("Evaluating PMID: " + row.pmid() + " (" + (i + 1) + "/" + total + ")");

                    try {
                        int runId = evaluationService.evaluateAbstract(topicId, row.pmid());

                        // Update status in the table
                        String newStatus = getEvaluationStatus(topicId, row.pmid());
                        Platform.runLater(() -> row.setStatus(newStatus));

                        completed.incrementAndGet();
                        logger.info("Successfully evaluated PMID {} (run ID: {})", row.pmid(), runId);

                    } catch (Exception e) {
                        failed.incrementAndGet();
                        logger.error("Failed to evaluate PMID {}: {}", row.pmid(), e.getMessage(), e);
                        String errorMsg = e.getMessage();
                        if (errorMsg == null || errorMsg.isEmpty()) {
                            errorMsg = e.getClass().getSimpleName();
                        }
                        // Truncate long error messages for display
                        String displayError = errorMsg.length() > 50 ? errorMsg.substring(0, 50) + "..." : errorMsg;
                        Platform.runLater(() -> row.setStatus("Error: " + displayError));
                    }

                    // Update progress
                    double progress = (i + 1) / (double) total;
                    updateProgress(progress, 1.0);
                }

                return null;
            }
        };

        task.messageProperty().addListener((obs, oldMsg, newMsg) -> updateStatus(newMsg));

        task.progressProperty().addListener((obs, oldProg, newProg) -> progressBar.setProgress(newProg.doubleValue()));

        task.setOnSucceeded(event -> {
            String message = String.format("Evaluation complete: %d succeeded, %d failed",
                    completed.get(), failed.get());
            updateStatus(message);

            if (failed.get() > 0) {
                String detailMsg = message + "\n\nCheck the logs for detailed error information.";
                showWarning("Evaluation Complete with Errors", detailMsg);
            } else {
                showInfo("Evaluation Complete", message);
            }

            progressBar.setVisible(false);
            evaluateSelectedButton.setDisable(false);
            evaluateAllButton.setDisable(false);
            topicCombo.setDisable(false);

            // Reload abstracts to update status
            loadAbstracts(topicId);
        });

        task.setOnFailed(event -> {
            logger.error("Evaluation task failed", task.getException());
            showError("Evaluation Failed", "Evaluation task failed: " + task.getException().getMessage());

            progressBar.setVisible(false);
            evaluateSelectedButton.setDisable(false);
            evaluateAllButton.setDisable(false);
            topicCombo.setDisable(false);
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
    public static class AbstractRow {
        private final String pmid;
        private final String title;
        private final String journal;
        private final SimpleStringProperty status;
        private final SimpleBooleanProperty selected;

        public AbstractRow(String pmid, String title, String journal, String status, boolean selected) {
            this.pmid = pmid;
            this.title = title;
            this.journal = journal;
            this.status = new SimpleStringProperty(status);
            this.selected = new SimpleBooleanProperty(selected);
        }

        public String pmid() {
            return pmid;
        }

        public String title() {
            return title;
        }

        public String journal() {
            return journal;
        }

        public String status() {
            return status.get();
        }

        public void setStatus(String status) {
            this.status.set(status);
        }

        public SimpleBooleanProperty selectedProperty() {
            return selected;
        }
    }
}

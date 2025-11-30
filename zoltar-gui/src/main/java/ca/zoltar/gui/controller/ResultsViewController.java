package ca.zoltar.gui.controller;

import ca.zoltar.db.AbstractDao;
import ca.zoltar.db.EvaluationResultDao;
import ca.zoltar.db.EvaluationRunDao;
import ca.zoltar.db.TopicDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class ResultsViewController {
    private static final Logger logger = LoggerFactory.getLogger(ResultsViewController.class);

    @FXML
    private ComboBox<String> labelFilter;
    @FXML
    private ComboBox<TopicDao.Topic> topicFilter;
    @FXML
    private TableView<ResultRow> resultsTable;
    @FXML
    private TableColumn<ResultRow, String> dateColumn;
    @FXML
    private TableColumn<ResultRow, String> topicColumn;
    @FXML
    private TableColumn<ResultRow, String> pmidColumn;
    @FXML
    private TableColumn<ResultRow, String> titleColumn;
    @FXML
    private TableColumn<ResultRow, String> labelColumn;
    @FXML
    private TableColumn<ResultRow, String> scoreColumn;
    @FXML
    private TextArea detailArea;

    private EvaluationRunDao runDao;
    private EvaluationResultDao resultDao;
    private AbstractDao abstractDao;
    private TopicDao topicDao;
    private ObservableList<ResultRow> allResults;
    private ObservableList<ResultRow> filteredResults;
    private ObjectMapper objectMapper;

    @FXML
    public void initialize() {
        logger.info("ResultsViewController initialized");

        try {
            // Initialize DAOs
            runDao = new EvaluationRunDao();
            resultDao = new EvaluationResultDao();
            abstractDao = new AbstractDao();
            topicDao = new TopicDao();
            objectMapper = new ObjectMapper();

            allResults = FXCollections.observableArrayList();
            filteredResults = FXCollections.observableArrayList();

            // Set up table columns
            dateColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().date()));
            topicColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().topicName()));
            pmidColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().pmid()));
            titleColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().title()));
            labelColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().label()));
            scoreColumn.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().score())));

            resultsTable.setItems(filteredResults);

            // Configure label filter
            labelFilter.setItems(FXCollections.observableArrayList(
                    "All",
                    "novel",
                    "uncertain",
                    "not_novel"));
            labelFilter.setValue("All");
            labelFilter.setOnAction(event -> applyFilters());

            // Configure topic filter
            topicFilter.setConverter(new StringConverter<TopicDao.Topic>() {
                @Override
                public String toString(TopicDao.Topic topic) {
                    return topic == null ? "All Topics" : topic.name();
                }

                @Override
                public TopicDao.Topic fromString(String string) {
                    return null;
                }
            });
            topicFilter.setOnAction(event -> applyFilters());

            // Handle row selection to show details
            resultsTable.getSelectionModel().selectedItemProperty().addListener(
                    (obs, oldSelection, newSelection) -> {
                        if (newSelection != null) {
                            showDetails(newSelection);
                        }
                    });

            // Load data
            loadResults();
            loadTopics();

        } catch (Exception e) {
            logger.error("Failed to initialize ResultsViewController", e);
            showError("Initialization Error", "Failed to initialize Results view: " + e.getMessage());
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
            topicFilter.getItems().clear();
            topicFilter.getItems().add(null); // "All Topics"
            topicFilter.getItems().addAll(topics);
            topicFilter.setValue(null);
        });

        task.setOnFailed(event -> {
            logger.error("Failed to load topics", task.getException());
        });

        new Thread(task).start();
    }

    private void loadResults() {
        Task<List<ResultRow>> task = new Task<>() {
            @Override
            protected List<ResultRow> call() throws Exception {
                List<EvaluationRunDao.EvaluationRun> runs = runDao.findAll();
                List<ResultRow> rows = new java.util.ArrayList<>();

                for (EvaluationRunDao.EvaluationRun run : runs) {
                    try {
                        // Get result
                        EvaluationResultDao.EvaluationResult result = resultDao.findByRunId(run.id());
                        if (result == null) {
                            continue;
                        }

                        // Get topic
                        TopicDao.Topic topic = topicDao.findById(run.topicId()).orElse(null);
                        String topicName = topic != null ? topic.name() : "Unknown";

                        // Get abstract
                        AbstractDao.Abstract abs = abstractDao.findByTopicAndPmid(run.topicId(), run.pmid());
                        String title = abs != null ? abs.title() : "Unknown";

                        // Format date
                        String formattedDate = formatDate(run.createdAt());

                        rows.add(new ResultRow(
                                run.id(),
                                formattedDate,
                                topicName,
                                run.pmid(),
                                title,
                                result.noveltyLabel(),
                                result.noveltyScore(),
                                result.rationale(),
                                run.llmModel(),
                                run.paramsJson(),
                                result.usedChunkIdsJson(),
                                result.hybridScoresJson()));
                    } catch (Exception e) {
                        logger.warn("Failed to process run {}: {}", run.id(), e.getMessage());
                    }
                }

                return rows;
            }
        };

        task.setOnSucceeded(event -> {
            allResults.setAll(task.getValue());
            applyFilters();
            logger.info("Loaded {} evaluation results", allResults.size());
        });

        task.setOnFailed(event -> {
            logger.error("Failed to load results", task.getException());
            showError("Load Error", "Failed to load evaluation results: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    private String formatDate(String dateString) {
        try {
            LocalDateTime dateTime = LocalDateTime.parse(dateString);
            return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (Exception e) {
            return dateString;
        }
    }

    private void applyFilters() {
        String labelFilterValue = labelFilter.getValue();
        TopicDao.Topic topicFilterValue = topicFilter.getValue();

        List<ResultRow> filtered = allResults.stream()
                .filter(row -> {
                    // Label filter
                    if (labelFilterValue != null && !labelFilterValue.equals("All")) {
                        if (!row.label().equals(labelFilterValue)) {
                            return false;
                        }
                    }

                    // Topic filter
                    if (topicFilterValue != null) {
                        if (!row.topicName().equals(topicFilterValue.name())) {
                            return false;
                        }
                    }

                    return true;
                })
                .collect(Collectors.toList());

        filteredResults.setAll(filtered);
    }

    private void showDetails(ResultRow row) {
        StringBuilder details = new StringBuilder();
        details.append("=== Evaluation Details ===\n\n");
        details.append("PMID: ").append(row.pmid()).append("\n");
        details.append("Title: ").append(row.title()).append("\n");
        details.append("Topic: ").append(row.topicName()).append("\n");
        details.append("Evaluated: ").append(row.date()).append("\n");
        details.append("Model: ").append(row.model()).append("\n\n");

        details.append("=== Assessment ===\n\n");
        details.append("Novelty Label: ").append(row.label()).append("\n");
        details.append("Novelty Score: ").append(row.score()).append("/10\n\n");

        details.append("=== Rationale ===\n\n");
        details.append(row.rationale()).append("\n\n");

        details.append("=== Parameters ===\n\n");
        try {
            ObjectNode params = (ObjectNode) objectMapper.readTree(row.paramsJson());
            details.append("Alpha: ").append(params.get("alpha").asDouble()).append("\n");
            details.append("K Context: ").append(params.get("k_context").asInt()).append("\n");
            details.append("K Semantic: ").append(params.get("k_semantic").asInt()).append("\n");
            details.append("K Lexical: ").append(params.get("k_lexical").asInt()).append("\n");
            details.append("Embedding Model: ").append(params.get("embedding_model").asText()).append("\n");
        } catch (Exception e) {
            details.append("(Unable to parse parameters)\n");
        }

        detailArea.setText(details.toString());
    }

    @FXML
    private void handleClearFilters() {
        logger.info("Clear filters clicked");
        labelFilter.setValue("All");
        topicFilter.setValue(null);
        applyFilters();
    }

    @FXML
    private void handleExportCsv() {
        logger.info("Export CSV clicked");

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Results to CSV");
        fileChooser.setInitialFileName("evaluation_results.csv");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = fileChooser.showSaveDialog(resultsTable.getScene().getWindow());
        if (file != null) {
            exportToCsv(file);
        }
    }

    private void exportToCsv(File file) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (FileWriter writer = new FileWriter(file)) {
                    // Header
                    writer.write("Date,Topic,PMID,Title,Label,Score,Rationale,Model\n");

                    // Data
                    for (ResultRow row : filteredResults) {
                        writer.write(escapeCsv(row.date()) + ",");
                        writer.write(escapeCsv(row.topicName()) + ",");
                        writer.write(escapeCsv(row.pmid()) + ",");
                        writer.write(escapeCsv(row.title()) + ",");
                        writer.write(escapeCsv(row.label()) + ",");
                        writer.write(row.score() + ",");
                        writer.write(escapeCsv(row.rationale()) + ",");
                        writer.write(escapeCsv(row.model()) + "\n");
                    }
                }
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            showInfo("Export Complete", "Results exported to " + file.getName());
            logger.info("Exported {} results to CSV", filteredResults.size());
        });

        task.setOnFailed(event -> {
            logger.error("Failed to export CSV", task.getException());
            showError("Export Failed", "Failed to export CSV: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    @FXML
    private void handleExportJson() {
        logger.info("Export JSON clicked");

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Results to JSON");
        fileChooser.setInitialFileName("evaluation_results.json");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON Files", "*.json"));

        File file = fileChooser.showSaveDialog(resultsTable.getScene().getWindow());
        if (file != null) {
            exportToJson(file);
        }
    }

    private void exportToJson(File file) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                ArrayNode jsonArray = objectMapper.createArrayNode();

                for (ResultRow row : filteredResults) {
                    ObjectNode jsonObj = objectMapper.createObjectNode();
                    jsonObj.put("date", row.date());
                    jsonObj.put("topic", row.topicName());
                    jsonObj.put("pmid", row.pmid());
                    jsonObj.put("title", row.title());
                    jsonObj.put("novelty_label", row.label());
                    jsonObj.put("novelty_score", row.score());
                    jsonObj.put("rationale", row.rationale());
                    jsonObj.put("model", row.model());

                    // Include parameters
                    try {
                        jsonObj.set("parameters", objectMapper.readTree(row.paramsJson()));
                    } catch (Exception e) {
                        jsonObj.put("parameters", row.paramsJson());
                    }

                    jsonArray.add(jsonObj);
                }

                objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, jsonArray);
                return null;
            }
        };

        task.setOnSucceeded(event -> {
            showInfo("Export Complete", "Results exported to " + file.getName());
            logger.info("Exported {} results to JSON", filteredResults.size());
        });

        task.setOnFailed(event -> {
            logger.error("Failed to export JSON", task.getException());
            showError("Export Failed", "Failed to export JSON: " + task.getException().getMessage());
        });

        new Thread(task).start();
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
     * Row model for the results table.
     */
    public record ResultRow(
            int runId,
            String date,
            String topicName,
            String pmid,
            String title,
            String label,
            int score,
            String rationale,
            String model,
            String paramsJson,
            String usedChunkIdsJson,
            String hybridScoresJson) {
    }
}

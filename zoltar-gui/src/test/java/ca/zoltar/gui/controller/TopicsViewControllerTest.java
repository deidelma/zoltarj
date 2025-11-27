package ca.zoltar.gui.controller;

import ca.zoltar.db.AbstractDao;
import ca.zoltar.db.DatabaseManager;
import ca.zoltar.db.DocumentDao;
import ca.zoltar.db.TopicDao;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicsViewControllerTest {

    private static boolean toolkitInitialized;

    @BeforeAll
    static void startToolkit() throws Exception {
        if (!toolkitInitialized) {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            latch.await();
            toolkitInitialized = true;
        }
    }

    @TempDir
    Path tempDir;

    private TopicDao topicDao;
    private DocumentDao documentDao;
    private AbstractDao abstractDao;
    private TestTopicsViewController controller;
    private TableView<TopicsViewController.TopicRow> topicsTable;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = tempDir.resolve("topics-ui.db");
        System.setProperty("zoltar.db.path", dbFile.toString());
        DatabaseManager.resetForTests();

        topicDao = new TopicDao();
        documentDao = new DocumentDao();
        abstractDao = new AbstractDao();
        controller = new TestTopicsViewController(topicDao, documentDao, abstractDao);
        runOnFxThread(() -> {
            wireFxmlFields(controller);
            controller.initialize();
        });
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("zoltar.db.path");
        DatabaseManager.resetForTests();
    }

    @Test
    void handleNewTopicCreatesRecordAndUpdatesTable() throws Exception {
        controller.setNextDialogResult(new TopicsViewController.TopicFormData("Topic Alpha", "term", "notes"));

        runOnFxThread(controller::handleNewTopic);

        assertEquals(1, topicDao.findAll().size());
        List<TopicsViewController.TopicRow> rows = getDisplayedTopicsSnapshot();
        assertEquals(1, rows.size());
        assertEquals("Topic Alpha", rows.get(0).getName());
        assertEquals("Topic created successfully.", controller.getLastInfoMessage());
        assertEquals("1 topic", controller.getLastStatus());
    }

    @Test
    void handleNewTopicCancelledDoesNothing() throws Exception {
        controller.setNextDialogResult(null);

        runOnFxThread(controller::handleNewTopic);

        assertTrue(topicDao.findAll().isEmpty());
        assertEquals("Topic creation cancelled", controller.getLastStatus());
    }

    private void runOnFxThread(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Exception e) {
                error.set(e);
            } finally {
                latch.countDown();
            }
        });
        latch.await();
        if (error.get() != null) {
            throw error.get();
        }
    }

    private void wireFxmlFields(TopicsViewController target) {
        try {
            topicsTable = new TableView<>();
            setField(target, "topicsTable", topicsTable);
            setField(target, "idColumn", new TableColumn<TopicsViewController.TopicRow, Integer>("ID"));
            setField(target, "nameColumn", new TableColumn<TopicsViewController.TopicRow, String>("Name"));
            setField(target, "queryColumn", new TableColumn<TopicsViewController.TopicRow, String>("Query"));
            setField(target, "docsColumn", new TableColumn<TopicsViewController.TopicRow, Integer>("Docs"));
            setField(target, "abstractsColumn", new TableColumn<TopicsViewController.TopicRow, Integer>("Abstracts"));
            setField(target, "updatedColumn", new TableColumn<TopicsViewController.TopicRow, String>("Updated"));
            setField(target, "btnEdit", new Button());
            setField(target, "btnDelete", new Button());
            setField(target, "statusLabel", new Label());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void handleDeleteTopicDeletesAfterConfirmation() throws Exception {
        TopicDao.Topic topic = topicDao.create("Delete Me", "term", null);
        documentDao.create(topic.id(), "Doc", "/tmp/d.pdf", null, "pmid:D", 2024, "Venue", "hash-delete");

        TopicsViewController.TopicRow row = new TopicsViewController.TopicRow(
            topic.id(), topic.name(), topic.queryString(), 1, 0, topic.updatedAt());
        controller.setNextDeleteConfirmation(true);

        runOnFxThread(() -> {
            topicsTable.getItems().setAll(row);
            topicsTable.getSelectionModel().select(row);
            controller.handleDeleteTopic();
        });

        assertTrue(topicDao.findAll().isEmpty());
        assertEquals(0, documentDao.countByTopicId(topic.id()));
        assertEquals("Topic deleted successfully.", controller.getLastInfoMessage());
        assertEquals("0 topics", controller.getLastStatus());
        assertTrue(getDisplayedTopicsSnapshot().isEmpty());
    }

    @Test
    void handleDeleteTopicCancelledLeavesTopic() throws Exception {
        TopicDao.Topic topic = topicDao.create("Keep Me", "term", null);
        TopicsViewController.TopicRow row = new TopicsViewController.TopicRow(
                topic.id(), topic.name(), topic.queryString(), 0, 0, topic.updatedAt());
        controller.setNextDeleteConfirmation(false);

        runOnFxThread(() -> {
            topicsTable.getItems().setAll(row);
            topicsTable.getSelectionModel().select(row);
            controller.handleDeleteTopic();
        });

        assertEquals(1, topicDao.findAll().size());
        assertEquals("Deletion cancelled", controller.getLastStatus());
        assertEquals(1, getDisplayedTopicsSnapshot().size());
    }

    @Test
    void handleEditTopicUpdatesTopic() throws Exception {
        TopicDao.Topic topic = topicDao.create("Original", "term", "notes");

        runOnFxThread(() -> {
            controller.loadTopics();
            selectTopicRowInternal(topic.id());
        });

        controller.setNextDialogResult(new TopicsViewController.TopicFormData("Updated", "new term", "new notes"));
        runOnFxThread(controller::handleEditTopic);

        TopicDao.Topic reloaded = topicDao.findById(topic.id()).orElseThrow();
        assertEquals("Updated", reloaded.name());
        assertEquals("new term", reloaded.queryString());
        assertEquals("Topic updated successfully.", controller.getLastInfoMessage());
        assertEquals("Updated", getDisplayedTopicsSnapshot().get(0).getName());
    }

    @Test
    void handleEditTopicCancelledKeepsOriginal() throws Exception {
        TopicDao.Topic topic = topicDao.create("Keep", "term", null);

        runOnFxThread(() -> {
            controller.loadTopics();
            selectTopicRowInternal(topic.id());
        });

        controller.setNextDialogResult(null);
        runOnFxThread(controller::handleEditTopic);

        TopicDao.Topic reloaded = topicDao.findById(topic.id()).orElseThrow();
        assertEquals("Keep", reloaded.name());
        assertEquals("Edit cancelled", controller.getLastStatus());
    }

    @Test
    void topicCrudEndToEnd() throws Exception {
        controller.setNextDialogResult(new TopicsViewController.TopicFormData("Alpha", "q1", "notes"));
        runOnFxThread(controller::handleNewTopic);

        List<TopicDao.Topic> topics = topicDao.findAll();
        assertEquals(1, topics.size());
        int topicId = topics.getFirst().id();

        runOnFxThread(() -> selectTopicRowInternal(topicId));
        controller.setNextDialogResult(new TopicsViewController.TopicFormData("Alpha+", "q2", "notes+"));
        runOnFxThread(controller::handleEditTopic);

        controller.setNextDeleteConfirmation(true);
        runOnFxThread(() -> {
            selectTopicRowInternal(topicId);
            controller.handleDeleteTopic();
        });

        assertTrue(topicDao.findAll().isEmpty());
        assertTrue(getDisplayedTopicsSnapshot().isEmpty());
    }

    private void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = TopicsViewController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private List<TopicsViewController.TopicRow> getDisplayedTopicsSnapshot() throws Exception {
        AtomicReference<List<TopicsViewController.TopicRow>> ref = new AtomicReference<>();
        runOnFxThread(() -> ref.set(List.copyOf(controller.getDisplayedTopics())));
        return ref.get();
    }

    private void selectTopicRowInternal(int topicId) {
        TopicsViewController.TopicRow row = controller.getDisplayedTopics()
                .stream()
                .filter(r -> r.getId() == topicId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Row not found for topic " + topicId));
        topicsTable.getSelectionModel().select(row);
    }

    private static final class TestTopicsViewController extends TopicsViewController {
        private Optional<TopicFormData> nextDialogResult = Optional.empty();
        private String lastStatus = "";
        private String lastInfoMessage = "";
        private boolean nextDeleteConfirmation = true;

        TestTopicsViewController(TopicDao topicDao, DocumentDao documentDao, AbstractDao abstractDao) {
            super(topicDao, documentDao, abstractDao);
        }

        void setNextDialogResult(TopicFormData data) {
            this.nextDialogResult = data == null ? Optional.empty() : Optional.of(data);
        }

        String getLastStatus() {
            return lastStatus;
        }

        String getLastInfoMessage() {
            return lastInfoMessage;
        }

        void setNextDeleteConfirmation(boolean confirm) {
            this.nextDeleteConfirmation = confirm;
        }

        @Override
        protected Optional<TopicFormData> showTopicDialog(String title, TopicDao.Topic topic) {
            return nextDialogResult;
        }

        @Override
        protected boolean confirmTopicDeletion(TopicRow selected) {
            return nextDeleteConfirmation;
        }

        @Override
        protected void showInfo(String title, String message) {
            lastInfoMessage = message;
            lastStatus = message;
        }

        @Override
        protected void showError(String title, String message) {
            lastStatus = message;
        }

        @Override
        protected void setStatus(String message) {
            lastStatus = message;
        }
    }
}

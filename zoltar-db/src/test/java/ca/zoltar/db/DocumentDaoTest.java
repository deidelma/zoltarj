package ca.zoltar.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentDaoTest {

    private TopicDao topicDao;
    private DocumentDao documentDao;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        Path dbFile = tempDir.resolve("test-zoltar.db");
        System.setProperty("zoltar.db.path", dbFile.toString());
        DatabaseManager.resetForTests();
        topicDao = new TopicDao();
        documentDao = new DocumentDao();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("zoltar.db.path");
        DatabaseManager.resetForTests();
    }

    @Test
    void countByTopicIdReturnsOnlyMatchingDocuments() throws SQLException {
        TopicDao.Topic topicOne = topicDao.create("One", "q1", null);
        TopicDao.Topic topicTwo = topicDao.create("Two", "q2", null);

        documentDao.create(topicOne.id(), "Doc A", "/tmp/a.pdf", null, "pmid:A", 2024, "Venue", "hash-a");
        documentDao.create(topicOne.id(), "Doc B", "/tmp/b.pdf", null, "pmid:B", 2024, "Venue", "hash-b");
        documentDao.create(topicTwo.id(), "Doc C", "/tmp/c.pdf", null, "pmid:C", 2024, "Venue", "hash-c");

        assertEquals(2, documentDao.countByTopicId(topicOne.id()));
        assertEquals(1, documentDao.countByTopicId(topicTwo.id()));
    }
}

package ca.zoltar.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TopicDaoTest {

    private TopicDao topicDao;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        Path dbFile = tempDir.resolve("test-zoltar.db");
        System.setProperty("zoltar.db.path", dbFile.toString());
        DatabaseManager.resetForTests();
        topicDao = new TopicDao();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("zoltar.db.path");
        DatabaseManager.resetForTests();
    }

    @Test
    void updateShouldPersistChanges() throws SQLException {
        TopicDao.Topic topic = topicDao.create("Initial", "pmid:123", "notes");

        TopicDao.Topic updated = topicDao.update(topic.id(), "Updated", "pmid:456", "new notes");

        assertEquals("Updated", updated.name());
        assertEquals("pmid:456", updated.queryString());
        assertEquals("new notes", updated.notes());
    }

    @Test
    void deleteCascadeRemovesTopicAndDependents() throws Exception {
        TopicDao.Topic topic = topicDao.create("Cascade", "term", "notes");

        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            int documentId = insertDocument(conn, topic.id(), "hash-1");
            int chunkId = insertChunk(conn, topic.id(), documentId);
            insertEmbedding(conn, chunkId);
            insertAbstract(conn, topic.id());
            insertPubmedSeen(conn, topic.id());
            int runId = insertEvaluationRun(conn, topic.id());
            insertEvaluationResult(conn, runId);
        }

        topicDao.deleteCascade(topic.id());

        assertTableEmpty("topic");
        assertTableEmpty("document");
        assertTableEmpty("chunk");
        assertTableEmpty("embedding");
        assertTableEmpty("abstract");
        assertTableEmpty("pubmed_seen");
        assertTableEmpty("evaluation_run");
        assertTableEmpty("evaluation_result");
    }

    private int insertDocument(Connection conn, int topicId, String hash) throws SQLException {
        String sql = "INSERT INTO document (topic_id, title, source_path, doi, pmid, year, venue, hash_sha256, added_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, topicId);
            pstmt.setString(2, "Doc");
            pstmt.setString(3, "/tmp/" + hash);
            pstmt.setString(4, "doi:" + hash);
            pstmt.setString(5, "pmid:" + hash);
            pstmt.setInt(6, 2024);
            pstmt.setString(7, "Venue");
            pstmt.setString(8, hash);
            pstmt.setString(9, LocalDateTime.now().toString());
            pstmt.executeUpdate();
        }
        return lastInsertId(conn);
    }

    private int insertChunk(Connection conn, int topicId, int documentId) throws SQLException {
        String sql = "INSERT INTO chunk (document_id, topic_id, chunk_index, text, tokens, lucene_doc_id, added_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, documentId);
            pstmt.setInt(2, topicId);
            pstmt.setInt(3, 0);
            pstmt.setString(4, "chunk text");
            pstmt.setInt(5, 128);
            pstmt.setInt(6, 1);
            pstmt.setString(7, LocalDateTime.now().toString());
            pstmt.executeUpdate();
        }
        return lastInsertId(conn);
    }

    private void insertEmbedding(Connection conn, int chunkId) throws SQLException {
        String sql = "INSERT INTO embedding (chunk_id, model, vector, created_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, chunkId);
            pstmt.setString(2, "text-embedding-3-small");
            pstmt.setBytes(3, new byte[]{1, 2, 3});
            pstmt.setString(4, LocalDateTime.now().toString());
            pstmt.executeUpdate();
        }
    }

    private void insertAbstract(Connection conn, int topicId) throws SQLException {
        String sql = "INSERT INTO abstract (topic_id, pmid, title, authors_json, journal, pub_date, abstract_text, added_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, topicId);
            pstmt.setString(2, "pmid-abs");
            pstmt.setString(3, "Title");
            pstmt.setString(4, "[\"Author\"]");
            pstmt.setString(5, "Journal");
            pstmt.setString(6, "2024-01-01");
            pstmt.setString(7, "Abstract");
            pstmt.setString(8, LocalDateTime.now().toString());
            pstmt.executeUpdate();
        }
    }

    private void insertPubmedSeen(Connection conn, int topicId) throws SQLException {
        String sql = "INSERT INTO pubmed_seen (topic_id, pmid, first_seen_at) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, topicId);
            pstmt.setString(2, "pmid-seen");
            pstmt.setString(3, LocalDateTime.now().toString());
            pstmt.executeUpdate();
        }
    }

    private int insertEvaluationRun(Connection conn, int topicId) throws SQLException {
        String sql = "INSERT INTO evaluation_run (topic_id, pmid, created_at, llm_model, params_json) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, topicId);
            pstmt.setString(2, "pmid-eval");
            pstmt.setString(3, LocalDateTime.now().toString());
            pstmt.setString(4, "gpt-4");
            pstmt.setString(5, "{}");
            pstmt.executeUpdate();
        }
        return lastInsertId(conn);
    }

    private void insertEvaluationResult(Connection conn, int runId) throws SQLException {
        String sql = "INSERT INTO evaluation_result (run_id, novelty_label, novelty_score, rationale, used_chunk_ids_json, hybrid_scores_json) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, runId);
            pstmt.setString(2, "novel");
            pstmt.setInt(3, 8);
            pstmt.setString(4, "rationale");
            pstmt.setString(5, "[]");
            pstmt.setString(6, "{}");
            pstmt.executeUpdate();
        }
    }

    private int lastInsertId(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void assertTableEmpty(String table) throws SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            assertEquals(0, rs.getInt(1), () -> table + " should be empty");
        }
    }
}

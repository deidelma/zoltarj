package ca.zoltar.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for evaluation_run table.
 * Stores metadata about LLM evaluation runs.
 */
public class EvaluationRunDao {
    private static final Logger logger = LoggerFactory.getLogger(EvaluationRunDao.class);

    public record EvaluationRun(
            int id,
            int topicId,
            String pmid,
            String createdAt,
            String llmModel,
            String paramsJson) {
    }

    /**
     * Create a new evaluation run record.
     * 
     * @param topicId    The topic ID
     * @param pmid       The PubMed ID being evaluated
     * @param llmModel   The LLM model used (e.g., "gpt-4")
     * @param paramsJson JSON string of evaluation parameters (alpha, K values,
     *                   etc.)
     * @return The ID of the created evaluation run
     * @throws SQLException If database operation fails
     */
    public int create(int topicId, String pmid, String llmModel, String paramsJson) throws SQLException {
        String sql = "INSERT INTO evaluation_run (topic_id, pmid, created_at, llm_model, params_json) " +
                "VALUES (?, ?, ?, ?, ?)";
        String now = LocalDateTime.now().toString();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, topicId);
            pstmt.setString(2, pmid);
            pstmt.setString(3, now);
            pstmt.setString(4, llmModel);
            pstmt.setString(5, paramsJson);

            pstmt.executeUpdate();

            // Use last_insert_rowid() for SQLite compatibility
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    logger.info("Created evaluation run {} for PMID {} with model {}", id, pmid, llmModel);
                    return id;
                } else {
                    throw new SQLException("Failed to get generated key for evaluation run");
                }
            }
        }
    }

    /**
     * Find an evaluation run by ID.
     * 
     * @param id The evaluation run ID
     * @return The evaluation run if found, null otherwise
     * @throws SQLException If database operation fails
     */
    public EvaluationRun findById(int id) throws SQLException {
        String sql = "SELECT * FROM evaluation_run WHERE id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Find all evaluation runs for a topic.
     * 
     * @param topicId The topic ID
     * @return List of evaluation runs
     * @throws SQLException If database operation fails
     */
    public List<EvaluationRun> findByTopicId(int topicId) throws SQLException {
        String sql = "SELECT * FROM evaluation_run WHERE topic_id = ? ORDER BY created_at DESC";
        List<EvaluationRun> runs = new ArrayList<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, topicId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    runs.add(mapRow(rs));
                }
            }
        }
        return runs;
    }

    /**
     * Find all evaluation runs.
     * 
     * @return List of all evaluation runs
     * @throws SQLException If database operation fails
     */
    public List<EvaluationRun> findAll() throws SQLException {
        String sql = "SELECT * FROM evaluation_run ORDER BY created_at DESC";
        List<EvaluationRun> runs = new ArrayList<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    runs.add(mapRow(rs));
                }
            }
        }
        return runs;
    }

    /**
     * Find all evaluation runs for a specific PMID.
     * 
     * @param pmid The PubMed ID
     * @return List of evaluation runs
     * @throws SQLException If database operation fails
     */
    public List<EvaluationRun> findByPmid(String pmid) throws SQLException {
        String sql = "SELECT * FROM evaluation_run WHERE pmid = ? ORDER BY created_at DESC";
        List<EvaluationRun> runs = new ArrayList<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, pmid);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    runs.add(mapRow(rs));
                }
            }
        }
        return runs;
    }

    /**
     * Find evaluation runs for a topic and PMID.
     * 
     * @param topicId The topic ID
     * @param pmid    The PubMed ID
     * @return List of evaluation runs
     * @throws SQLException If database operation fails
     */
    public List<EvaluationRun> findByTopicAndPmid(int topicId, String pmid) throws SQLException {
        String sql = "SELECT * FROM evaluation_run WHERE topic_id = ? AND pmid = ? ORDER BY created_at DESC";
        List<EvaluationRun> runs = new ArrayList<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, topicId);
            pstmt.setString(2, pmid);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    runs.add(mapRow(rs));
                }
            }
        }
        return runs;
    }

    /**
     * Count evaluation runs for a topic.
     * 
     * @param topicId The topic ID
     * @return Count of evaluation runs
     * @throws SQLException If database operation fails
     */
    public int countByTopicId(int topicId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM evaluation_run WHERE topic_id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, topicId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Delete an evaluation run and its results (cascade).
     * 
     * @param id The evaluation run ID
     * @throws SQLException If database operation fails
     */
    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM evaluation_run WHERE id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            int deleted = pstmt.executeUpdate();
            logger.debug("Deleted {} evaluation run(s) with ID {}", deleted, id);
        }
    }

    private EvaluationRun mapRow(ResultSet rs) throws SQLException {
        return new EvaluationRun(
                rs.getInt("id"),
                rs.getInt("topic_id"),
                rs.getString("pmid"),
                rs.getString("created_at"),
                rs.getString("llm_model"),
                rs.getString("params_json"));
    }
}

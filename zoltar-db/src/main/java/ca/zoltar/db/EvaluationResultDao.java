package ca.zoltar.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for evaluation_result table.
 * Stores LLM evaluation results for novelty assessment.
 */
public class EvaluationResultDao {
    private static final Logger logger = LoggerFactory.getLogger(EvaluationResultDao.class);

    public record EvaluationResult(
            int id,
            int runId,
            String noveltyLabel,
            int noveltyScore,
            String rationale,
            String usedChunkIdsJson,
            String hybridScoresJson
    ) {}

    /**
     * Create a new evaluation result record.
     * 
     * @param runId The evaluation run ID
     * @param noveltyLabel The novelty classification ("novel", "uncertain", "not_novel")
     * @param noveltyScore The novelty score (0-10)
     * @param rationale Free-text explanation of the evaluation
     * @param usedChunkIdsJson JSON array of chunk IDs used as context
     * @param hybridScoresJson JSON object mapping chunk IDs to their hybrid scores
     * @return The ID of the created evaluation result
     * @throws SQLException If database operation fails
     */
    public int create(int runId, String noveltyLabel, int noveltyScore, String rationale,
                      String usedChunkIdsJson, String hybridScoresJson) throws SQLException {
        
        String sql = "INSERT INTO evaluation_result (run_id, novelty_label, novelty_score, rationale, " +
                     "used_chunk_ids_json, hybrid_scores_json) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setInt(1, runId);
            pstmt.setString(2, noveltyLabel);
            pstmt.setInt(3, noveltyScore);
            pstmt.setString(4, rationale);
            pstmt.setString(5, usedChunkIdsJson);
            pstmt.setString(6, hybridScoresJson);
            
            pstmt.executeUpdate();
            
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    logger.info("Created evaluation result {} for run {} with label '{}'", 
                            id, runId, noveltyLabel);
                    return id;
                } else {
                    throw new SQLException("Failed to get generated key for evaluation result");
                }
            }
        }
    }

    /**
     * Find an evaluation result by ID.
     * 
     * @param id The evaluation result ID
     * @return The evaluation result if found, null otherwise
     * @throws SQLException If database operation fails
     */
    public EvaluationResult findById(int id) throws SQLException {
        String sql = "SELECT * FROM evaluation_result WHERE id = ?";

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
     * Find evaluation result by run ID.
     * 
     * @param runId The evaluation run ID
     * @return The evaluation result if found, null otherwise
     * @throws SQLException If database operation fails
     */
    public EvaluationResult findByRunId(int runId) throws SQLException {
        String sql = "SELECT * FROM evaluation_result WHERE run_id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, runId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Find all evaluation results with a specific novelty label.
     * 
     * @param noveltyLabel The novelty label to filter by
     * @return List of evaluation results
     * @throws SQLException If database operation fails
     */
    public List<EvaluationResult> findByNoveltyLabel(String noveltyLabel) throws SQLException {
        String sql = "SELECT * FROM evaluation_result WHERE novelty_label = ?";
        List<EvaluationResult> results = new ArrayList<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, noveltyLabel);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        }
        return results;
    }

    /**
     * Delete an evaluation result.
     * 
     * @param id The evaluation result ID
     * @throws SQLException If database operation fails
     */
    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM evaluation_result WHERE id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, id);
            int deleted = pstmt.executeUpdate();
            logger.debug("Deleted {} evaluation result(s) with ID {}", deleted, id);
        }
    }

    private EvaluationResult mapRow(ResultSet rs) throws SQLException {
        return new EvaluationResult(
                rs.getInt("id"),
                rs.getInt("run_id"),
                rs.getString("novelty_label"),
                rs.getInt("novelty_score"),
                rs.getString("rationale"),
                rs.getString("used_chunk_ids_json"),
                rs.getString("hybrid_scores_json")
        );
    }
}

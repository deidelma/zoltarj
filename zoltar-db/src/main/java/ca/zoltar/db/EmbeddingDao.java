package ca.zoltar.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for embeddings table.
 */
public class EmbeddingDao {
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingDao.class);

    public record Embedding(int id, int chunkId, String model, float[] vector, String createdAt) {
    }

    /**
     * Create a new embedding record.
     * 
     * @param chunkId The chunk ID this embedding belongs to
     * @param model   The embedding model used
     * @param vector  The embedding vector
     * @return The ID of the created embedding
     * @throws SQLException If database operation fails
     */
    public int create(int chunkId, String model, float[] vector) throws SQLException {
        String sql = "INSERT INTO embedding (chunk_id, model, vector, created_at) VALUES (?, ?, ?, ?)";
        String now = LocalDateTime.now().toString();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, chunkId);
            pstmt.setString(2, model);
            pstmt.setBytes(3, serializeVector(vector));
            pstmt.setString(4, now);

            pstmt.executeUpdate();

            // SQLite-specific way to get last insert ID
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    logger.debug("Created embedding {} for chunk {} with model {}", id, chunkId, model);
                    return id;
                } else {
                    throw new SQLException("Failed to get generated key for embedding");
                }
            }
        }
    }

    /**
     * Batch create embeddings for multiple chunks.
     * 
     * @param embeddings List of embeddings to create
     * @throws SQLException If database operation fails
     */
    public void batchCreate(List<EmbeddingInput> embeddings) throws SQLException {
        String sql = "INSERT INTO embedding (chunk_id, model, vector, created_at) VALUES (?, ?, ?, ?)";
        String now = LocalDateTime.now().toString();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);
            try {
                for (EmbeddingInput emb : embeddings) {
                    pstmt.setInt(1, emb.chunkId());
                    pstmt.setString(2, emb.model());
                    pstmt.setBytes(3, serializeVector(emb.vector()));
                    pstmt.setString(4, now);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                conn.commit();
                logger.info("Batch created {} embeddings", embeddings.size());
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Find an embedding by chunk ID and model.
     * 
     * @param chunkId The chunk ID
     * @param model   The embedding model
     * @return The embedding, or null if not found
     * @throws SQLException If database operation fails
     */
    public Embedding findByChunkIdAndModel(int chunkId, String model) throws SQLException {
        String sql = "SELECT * FROM embedding WHERE chunk_id = ? AND model = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, chunkId);
            pstmt.setString(2, model);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Find all embeddings for a topic.
     * 
     * @param topicId The topic ID
     * @param model   The embedding model to filter by
     * @return List of embeddings
     * @throws SQLException If database operation fails
     */
    public List<Embedding> findByTopicId(int topicId, String model) throws SQLException {
        String sql = """
                    SELECT e.* FROM embedding e
                    JOIN chunk c ON e.chunk_id = c.id
                    WHERE c.topic_id = ? AND e.model = ?
                    ORDER BY c.document_id, c.chunk_index
                """;

        List<Embedding> embeddings = new ArrayList<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, topicId);
            pstmt.setString(2, model);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    embeddings.add(mapRow(rs));
                }
            }
        }
        return embeddings;
    }

    /**
     * Count embeddings for a topic.
     * 
     * @param topicId The topic ID
     * @param model   The embedding model
     * @return Count of embeddings
     * @throws SQLException If database operation fails
     */
    public int countByTopicId(int topicId, String model) throws SQLException {
        String sql = """
                    SELECT COUNT(*) FROM embedding e
                    JOIN chunk c ON e.chunk_id = c.id
                    WHERE c.topic_id = ? AND e.model = ?
                """;

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, topicId);
            pstmt.setString(2, model);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Delete all embeddings for a chunk.
     * 
     * @param chunkId The chunk ID
     * @throws SQLException If database operation fails
     */
    public void deleteByChunkId(int chunkId) throws SQLException {
        String sql = "DELETE FROM embedding WHERE chunk_id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, chunkId);
            int deleted = pstmt.executeUpdate();
            logger.debug("Deleted {} embeddings for chunk {}", deleted, chunkId);
        }
    }

    private Embedding mapRow(ResultSet rs) throws SQLException {
        return new Embedding(
                rs.getInt("id"),
                rs.getInt("chunk_id"),
                rs.getString("model"),
                deserializeVector(rs.getBytes("vector")),
                rs.getString("created_at"));
    }

    /**
     * Serialize a float array to bytes for storage.
     */
    private byte[] serializeVector(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES);
        for (float v : vector) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }

    /**
     * Deserialize bytes back to a float array.
     */
    private float[] deserializeVector(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        float[] vector = new float[bytes.length / Float.BYTES];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }

    /**
     * Input record for batch embedding creation.
     */
    public record EmbeddingInput(int chunkId, String model, float[] vector) {
    }
}

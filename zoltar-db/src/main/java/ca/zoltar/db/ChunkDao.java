package ca.zoltar.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ChunkDao {
    private static final Logger logger = LoggerFactory.getLogger(ChunkDao.class);

    public record Chunk(int id, int documentId, int topicId, int chunkIndex, String text, int tokens,
            Integer luceneDocId, String addedAt) {
    }

    public void batchCreate(List<Chunk> chunks) throws SQLException {
        String sql = "INSERT INTO chunk (document_id, topic_id, chunk_index, text, tokens, added_at) VALUES (?, ?, ?, ?, ?, ?)";
        String now = LocalDateTime.now().toString();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);
            try {
                for (Chunk chunk : chunks) {
                    pstmt.setInt(1, chunk.documentId());
                    pstmt.setInt(2, chunk.topicId());
                    pstmt.setInt(3, chunk.chunkIndex());
                    pstmt.setString(4, chunk.text());
                    pstmt.setInt(5, chunk.tokens());
                    pstmt.setString(6, now);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public List<Chunk> findByDocumentId(int documentId) throws SQLException {
        String sql = "SELECT * FROM chunk WHERE document_id = ? ORDER BY chunk_index";
        List<Chunk> chunks = new ArrayList<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, documentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    chunks.add(mapRow(rs));
                }
            }
        }
        return chunks;
    }

    public List<Chunk> findByTopicId(int topicId) throws SQLException {
        String sql = "SELECT * FROM chunk WHERE topic_id = ? ORDER BY document_id, chunk_index";
        List<Chunk> chunks = new ArrayList<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, topicId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    chunks.add(mapRow(rs));
                }
            }
        }
        return chunks;
    }

    public Chunk findById(int id) throws SQLException {
        String sql = "SELECT * FROM chunk WHERE id = ?";

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

    public int countByTopicId(int topicId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM chunk WHERE topic_id = ?";

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

    public int countByDocumentId(int documentId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM chunk WHERE document_id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, documentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    private Chunk mapRow(ResultSet rs) throws SQLException {
        int luceneDocId = rs.getInt("lucene_doc_id");
        Integer luceneDocIdBoxed = rs.wasNull() ? null : luceneDocId;

        return new Chunk(
                rs.getInt("id"),
                rs.getInt("document_id"),
                rs.getInt("topic_id"),
                rs.getInt("chunk_index"),
                rs.getString("text"),
                rs.getInt("tokens"),
                luceneDocIdBoxed,
                rs.getString("added_at"));
    }
}

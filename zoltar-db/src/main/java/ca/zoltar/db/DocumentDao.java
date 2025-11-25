package ca.zoltar.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DocumentDao {
    private static final Logger logger = LoggerFactory.getLogger(DocumentDao.class);

    public record Document(int id, int topicId, String title, String sourcePath, String doi, String pmid, Integer year, String venue, String hashSha256, String addedAt) {}

    public Document create(int topicId, String title, String sourcePath, String doi, String pmid, Integer year, String venue, String hashSha256) throws SQLException {
        String sql = "INSERT INTO document (topic_id, title, source_path, doi, pmid, year, venue, hash_sha256, added_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String now = LocalDateTime.now().toString();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, topicId);
            pstmt.setString(2, title);
            pstmt.setString(3, sourcePath);
            pstmt.setString(4, doi);
            pstmt.setString(5, pmid);
            if (year != null) pstmt.setInt(6, year); else pstmt.setNull(6, Types.INTEGER);
            pstmt.setString(7, venue);
            pstmt.setString(8, hashSha256);
            pstmt.setString(9, now);
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating document failed, no rows affected.");
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    return new Document(id, topicId, title, sourcePath, doi, pmid, year, venue, hashSha256, now);
                } else {
                    throw new SQLException("Creating document failed, no ID obtained.");
                }
            }
        }
    }

    public Optional<Document> findByHash(String hashSha256) throws SQLException {
        String sql = "SELECT * FROM document WHERE hash_sha256 = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, hashSha256);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<Document> findByTopicId(int topicId) throws SQLException {
        String sql = "SELECT * FROM document WHERE topic_id = ?";
        List<Document> documents = new ArrayList<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, topicId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    documents.add(mapRow(rs));
                }
            }
        }
        return documents;
    }

    public Document findById(int id) throws SQLException {
        String sql = "SELECT * FROM document WHERE id = ?";

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

    private Document mapRow(ResultSet rs) throws SQLException {
        return new Document(
            rs.getInt("id"),
            rs.getInt("topic_id"),
            rs.getString("title"),
            rs.getString("source_path"),
            rs.getString("doi"),
            rs.getString("pmid"),
            rs.getObject("year") != null ? rs.getInt("year") : null,
            rs.getString("venue"),
            rs.getString("hash_sha256"),
            rs.getString("added_at")
        );
    }
}

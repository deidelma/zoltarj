package ca.zoltar.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TopicDao {
    private static final Logger logger = LoggerFactory.getLogger(TopicDao.class);

    public record Topic(int id, String name, String queryString, String notes, String createdAt, String updatedAt) {}

    public Topic create(String name, String queryString, String notes) throws SQLException {
        String sql = "INSERT INTO topic (name, query_string, notes, created_at, updated_at) VALUES (?, ?, ?, ?, ?)";
        String now = LocalDateTime.now().toString();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, name);
            pstmt.setString(2, queryString);
            pstmt.setString(3, notes);
            pstmt.setString(4, now);
            pstmt.setString(5, now);
            
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating topic failed, no rows affected.");
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    return new Topic(id, name, queryString, notes, now, now);
                } else {
                    throw new SQLException("Creating topic failed, no ID obtained.");
                }
            }
        }
    }

    public List<Topic> findAll() throws SQLException {
        String sql = "SELECT * FROM topic";
        List<Topic> topics = new ArrayList<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                topics.add(mapRow(rs));
            }
        }
        return topics;
    }

    public Optional<Topic> findById(int id) throws SQLException {
        String sql = "SELECT * FROM topic WHERE id = ?";
        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Topic update(int id, String name, String queryString, String notes) throws SQLException {
        String sql = "UPDATE topic SET name = ?, query_string = ?, notes = ?, updated_at = ? WHERE id = ?";
        String now = LocalDateTime.now().toString();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, name);
            pstmt.setString(2, queryString);
            pstmt.setString(3, notes);
            pstmt.setString(4, now);
            pstmt.setInt(5, id);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Updating topic failed, no rows affected.");
            }
        }

        return findById(id).orElseThrow(() -> new SQLException("Updated topic not found"));
    }

    public void deleteCascade(int topicId) throws SQLException {
        try (Connection conn = DatabaseManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                executeUpdate(conn, "DELETE FROM evaluation_result WHERE run_id IN (SELECT id FROM evaluation_run WHERE topic_id = ?)", topicId);
                executeUpdate(conn, "DELETE FROM evaluation_run WHERE topic_id = ?", topicId);
                executeUpdate(conn, "DELETE FROM embedding WHERE chunk_id IN (SELECT id FROM chunk WHERE topic_id = ?)", topicId);
                executeUpdate(conn, "DELETE FROM chunk WHERE topic_id = ?", topicId);
                executeUpdate(conn, "DELETE FROM document WHERE topic_id = ?", topicId);
                executeUpdate(conn, "DELETE FROM abstract WHERE topic_id = ?", topicId);
                executeUpdate(conn, "DELETE FROM pubmed_seen WHERE topic_id = ?", topicId);
                executeUpdate(conn, "DELETE FROM topic WHERE id = ?", topicId);
                conn.commit();
                logger.info("Deleted topic {} and dependent records", topicId);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private Topic mapRow(ResultSet rs) throws SQLException {
        return new Topic(
            rs.getInt("id"),
            rs.getString("name"),
            rs.getString("query_string"),
            rs.getString("notes"),
            rs.getString("created_at"),
            rs.getString("updated_at")
        );
    }

    private void executeUpdate(Connection conn, String sql, int topicId) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, topicId);
            pstmt.executeUpdate();
        }
    }
}

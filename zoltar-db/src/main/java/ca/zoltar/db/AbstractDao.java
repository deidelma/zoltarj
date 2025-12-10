package ca.zoltar.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for abstract table.
 * Stores PubMed abstracts with metadata.
 */
public class AbstractDao {
    private static final Logger logger = LoggerFactory.getLogger(AbstractDao.class);

    public record Abstract(
            int id,
            int topicId,
            String pmid,
            String title,
            String authorsJson,
            String journal,
            String pubDate,
            String abstractText,
            String addedAt
    ) {}

    /**
     * Create a new abstract record.
     * 
     * @param topicId The topic ID
     * @param pmid The PubMed ID
     * @param title Article title
     * @param authorsJson JSON array of author names
     * @param journal Journal name
     * @param pubDate Publication date
     * @param abstractText Abstract text
     * @return The ID of the created abstract
     * @throws SQLException If database operation fails
     */
    public int create(int topicId, String pmid, String title, String authorsJson,
                      String journal, String pubDate, String abstractText) throws SQLException {
        
        // Check if already exists
        Abstract existing = findByTopicAndPmid(topicId, pmid);
        if (existing != null) {
            logger.debug("Abstract for PMID {} already exists for topic {}", pmid, topicId);
            return existing.id();
        }

        String sql = """
                INSERT INTO abstract (topic_id, pmid, title, authors_json, journal, pub_date, abstract_text, added_at) \
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""";
        String now = LocalDateTime.now().toString();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setInt(1, topicId);
            pstmt.setString(2, pmid);
            pstmt.setString(3, title);
            pstmt.setString(4, authorsJson);
            pstmt.setString(5, journal);
            pstmt.setString(6, pubDate);
            pstmt.setString(7, abstractText);
            pstmt.setString(8, now);
            
            pstmt.executeUpdate();
            
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    logger.info("Created abstract {} for PMID {} in topic {}", id, pmid, topicId);
                    return id;
                } else {
                    throw new SQLException("Failed to get generated key for abstract");
                }
            }
        }
    }

    /**
     * Batch create abstract records.
     * 
     * @param abstracts List of abstracts to create
     * @return Number of new abstracts created (excludes duplicates)
     * @throws SQLException If database operation fails
     */
    public int batchCreate(List<AbstractInput> abstracts) throws SQLException {
        if (abstracts.isEmpty()) {
            return 0;
        }

        String sql = "INSERT OR IGNORE INTO abstract (topic_id, pmid, title, authors_json, journal, pub_date, abstract_text, added_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String now = LocalDateTime.now().toString();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            conn.setAutoCommit(false);
            try {
                for (AbstractInput abs : abstracts) {
                    pstmt.setInt(1, abs.topicId());
                    pstmt.setString(2, abs.pmid());
                    pstmt.setString(3, abs.title());
                    pstmt.setString(4, abs.authorsJson());
                    pstmt.setString(5, abs.journal());
                    pstmt.setString(6, abs.pubDate());
                    pstmt.setString(7, abs.abstractText());
                    pstmt.setString(8, now);
                    pstmt.addBatch();
                }
                int[] results = pstmt.executeBatch();
                conn.commit();
                
                int created = 0;
                for (int result : results) {
                    if (result > 0) created++;
                }
                
                logger.info("Batch created {} new abstracts (attempted {})", created, abstracts.size());
                return created;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Find an abstract by topic and PMID.
     * 
     * @param topicId The topic ID
     * @param pmid The PubMed ID
     * @return The abstract if found, null otherwise
     * @throws SQLException If database operation fails
     */
    public Abstract findByTopicAndPmid(int topicId, String pmid) throws SQLException {
        String sql = "SELECT * FROM abstract WHERE topic_id = ? AND pmid = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, topicId);
            pstmt.setString(2, pmid);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Find an abstract by ID.
     * 
     * @param id The abstract ID
     * @return The abstract if found, null otherwise
     * @throws SQLException If database operation fails
     */
    public Abstract findById(int id) throws SQLException {
        String sql = "SELECT * FROM abstract WHERE id = ?";

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
     * Find all abstracts for a topic.
     * 
     * @param topicId The topic ID
     * @return List of abstracts
     * @throws SQLException If database operation fails
     */
    public List<Abstract> findByTopic(int topicId) throws SQLException {
        String sql = "SELECT * FROM abstract WHERE topic_id = ? ORDER BY added_at DESC";
        List<Abstract> abstracts = new ArrayList<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, topicId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    abstracts.add(mapRow(rs));
                }
            }
        }
        return abstracts;
    }

    /**
     * Count abstracts for a topic.
     * 
     * @param topicId The topic ID
     * @return Count of abstracts
     * @throws SQLException If database operation fails
     */
    public int countByTopic(int topicId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM abstract WHERE topic_id = ?";

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
     * Delete an abstract.
     * 
     * @param id The abstract ID
     * @throws SQLException If database operation fails
     */
    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM abstract WHERE id = ?";

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, id);
            int deleted = pstmt.executeUpdate();
            logger.debug("Deleted {} abstract(s) with ID {}", deleted, id);
        }
    }

    private Abstract mapRow(ResultSet rs) throws SQLException {
        return new Abstract(
                rs.getInt("id"),
                rs.getInt("topic_id"),
                rs.getString("pmid"),
                rs.getString("title"),
                rs.getString("authors_json"),
                rs.getString("journal"),
                rs.getString("pub_date"),
                rs.getString("abstract_text"),
                rs.getString("added_at")
        );
    }

    /**
     * Input record for batch abstract creation.
     */
    public record AbstractInput(
            int topicId,
            String pmid,
            String title,
            String authorsJson,
            String journal,
            String pubDate,
            String abstractText
    ) {}
}

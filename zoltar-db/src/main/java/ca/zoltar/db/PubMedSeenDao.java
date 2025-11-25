package ca.zoltar.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Data Access Object for pubmed_seen table.
 * Tracks which PMIDs have been seen for each topic.
 */
public class PubMedSeenDao {
    private static final Logger logger = LoggerFactory.getLogger(PubMedSeenDao.class);

    public record PubMedSeen(int id, int topicId, String pmid, String firstSeenAt) {}

    /**
     * Mark a PMID as seen for a topic.
     * 
     * @param topicId The topic ID
     * @param pmid The PubMed ID
     * @return The ID of the created record, or existing ID if already seen
     * @throws SQLException If database operation fails
     */
    public int markAsSeen(int topicId, String pmid) throws SQLException {
        // Check if already exists
        PubMedSeen existing = findByTopicAndPmid(topicId, pmid);
        if (existing != null) {
            logger.debug("PMID {} already seen for topic {}", pmid, topicId);
            return existing.id();
        }

        String sql = "INSERT INTO pubmed_seen (topic_id, pmid, first_seen_at) VALUES (?, ?, ?)";
        String now = LocalDateTime.now().toString();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setInt(1, topicId);
            pstmt.setString(2, pmid);
            pstmt.setString(3, now);
            
            pstmt.executeUpdate();
            
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    logger.debug("Marked PMID {} as seen for topic {} (ID: {})", pmid, topicId, id);
                    return id;
                } else {
                    throw new SQLException("Failed to get generated key for pubmed_seen");
                }
            }
        }
    }

    /**
     * Batch mark multiple PMIDs as seen for a topic.
     * 
     * @param topicId The topic ID
     * @param pmids List of PubMed IDs
     * @return Number of new PMIDs marked as seen (excludes already seen)
     * @throws SQLException If database operation fails
     */
    public int batchMarkAsSeen(int topicId, List<String> pmids) throws SQLException {
        if (pmids.isEmpty()) {
            return 0;
        }

        // Get already seen PMIDs
        Set<String> alreadySeen = new HashSet<>(findPmidsByTopic(topicId));
        
        // Filter to only new PMIDs
        List<String> newPmids = pmids.stream()
                .filter(pmid -> !alreadySeen.contains(pmid))
                .toList();
        
        if (newPmids.isEmpty()) {
            logger.debug("All {} PMIDs already seen for topic {}", pmids.size(), topicId);
            return 0;
        }

        String sql = "INSERT INTO pubmed_seen (topic_id, pmid, first_seen_at) VALUES (?, ?, ?)";
        String now = LocalDateTime.now().toString();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            conn.setAutoCommit(false);
            try {
                for (String pmid : newPmids) {
                    pstmt.setInt(1, topicId);
                    pstmt.setString(2, pmid);
                    pstmt.setString(3, now);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                conn.commit();
                logger.info("Marked {} new PMIDs as seen for topic {}", newPmids.size(), topicId);
                return newPmids.size();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Check if a PMID has been seen for a topic.
     * 
     * @param topicId The topic ID
     * @param pmid The PubMed ID
     * @return true if already seen, false otherwise
     * @throws SQLException If database operation fails
     */
    public boolean isSeen(int topicId, String pmid) throws SQLException {
        return findByTopicAndPmid(topicId, pmid) != null;
    }

    /**
     * Find a specific pubmed_seen record.
     * 
     * @param topicId The topic ID
     * @param pmid The PubMed ID
     * @return The record if found, null otherwise
     * @throws SQLException If database operation fails
     */
    public PubMedSeen findByTopicAndPmid(int topicId, String pmid) throws SQLException {
        String sql = "SELECT * FROM pubmed_seen WHERE topic_id = ? AND pmid = ?";

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
     * Get all PMIDs seen for a topic.
     * 
     * @param topicId The topic ID
     * @return List of PMIDs
     * @throws SQLException If database operation fails
     */
    public List<String> findPmidsByTopic(int topicId) throws SQLException {
        String sql = "SELECT pmid FROM pubmed_seen WHERE topic_id = ? ORDER BY first_seen_at DESC";
        List<String> pmids = new ArrayList<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, topicId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    pmids.add(rs.getString("pmid"));
                }
            }
        }
        return pmids;
    }

    /**
     * Get all seen records for a topic.
     * 
     * @param topicId The topic ID
     * @return List of records
     * @throws SQLException If database operation fails
     */
    public List<PubMedSeen> findByTopic(int topicId) throws SQLException {
        String sql = "SELECT * FROM pubmed_seen WHERE topic_id = ? ORDER BY first_seen_at DESC";
        List<PubMedSeen> records = new ArrayList<>();

        try (Connection conn = DatabaseManager.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, topicId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    records.add(mapRow(rs));
                }
            }
        }
        return records;
    }

    /**
     * Count seen PMIDs for a topic.
     * 
     * @param topicId The topic ID
     * @return Count of seen PMIDs
     * @throws SQLException If database operation fails
     */
    public int countByTopic(int topicId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM pubmed_seen WHERE topic_id = ?";

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

    private PubMedSeen mapRow(ResultSet rs) throws SQLException {
        return new PubMedSeen(
                rs.getInt("id"),
                rs.getInt("topic_id"),
                rs.getString("pmid"),
                rs.getString("first_seen_at")
        );
    }
}

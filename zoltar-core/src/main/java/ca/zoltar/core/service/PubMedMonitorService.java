package ca.zoltar.core.service;

import ca.zoltar.db.AbstractDao;
import ca.zoltar.db.PubMedSeenDao;
import ca.zoltar.db.TopicDao;
import ca.zoltar.pubmed.PubMedClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for monitoring PubMed for new abstracts matching topic queries.
 */
public class PubMedMonitorService {
    private static final Logger logger = LoggerFactory.getLogger(PubMedMonitorService.class);
    
    private final PubMedClient pubMedClient;
    private final PubMedSeenDao pubMedSeenDao;
    private final AbstractDao abstractDao;
    private final TopicDao topicDao;
    private final ObjectMapper objectMapper;

    public PubMedMonitorService() {
        this.pubMedClient = new PubMedClient();
        this.pubMedSeenDao = new PubMedSeenDao();
        this.abstractDao = new AbstractDao();
        this.topicDao = new TopicDao();
        this.objectMapper = new ObjectMapper();
    }

    public PubMedMonitorService(PubMedClient pubMedClient) {
        this.pubMedClient = pubMedClient;
        this.pubMedSeenDao = new PubMedSeenDao();
        this.abstractDao = new AbstractDao();
        this.topicDao = new TopicDao();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Perform a dry run search to preview candidate PMIDs without storing them.
     * 
     * @param topicId The topic ID to search for
     * @param maxResults Maximum number of results
     * @return Search preview with new and seen PMIDs
     * @throws SQLException If database operation fails
     * @throws IOException If PubMed API call fails
     * @throws InterruptedException If operation is interrupted
     */
    public SearchPreview dryRunSearch(int topicId, int maxResults) 
            throws SQLException, IOException, InterruptedException {
        
        TopicDao.Topic topic = topicDao.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + topicId));
        
        if (topic.queryString() == null || topic.queryString().isBlank()) {
            throw new IllegalArgumentException("Topic has no query string defined");
        }
        
        logger.info("Performing dry run search for topic {} with query: '{}'", 
                topicId, topic.queryString());
        
        // Search PubMed
        List<String> candidatePmids = pubMedClient.search(topic.queryString(), maxResults);
        
        // Get already seen PMIDs
        Set<String> seenPmids = new HashSet<>(pubMedSeenDao.findPmidsByTopic(topicId));
        
        // Identify new vs seen
        List<String> newPmids = candidatePmids.stream()
                .filter(pmid -> !seenPmids.contains(pmid))
                .collect(Collectors.toList());
        
        List<String> alreadySeenPmids = candidatePmids.stream()
                .filter(seenPmids::contains)
                .collect(Collectors.toList());
        
        logger.info("Dry run results: {} total, {} new, {} already seen",
                candidatePmids.size(), newPmids.size(), alreadySeenPmids.size());
        
        return new SearchPreview(
                topicId,
                topic.queryString(),
                candidatePmids,
                newPmids,
                alreadySeenPmids
        );
    }

    /**
     * Fetch and store new abstracts for a topic.
     * 
     * @param topicId The topic ID
     * @param maxResults Maximum number of results to fetch
     * @return Statistics about the fetch operation
     * @throws SQLException If database operation fails
     * @throws IOException If PubMed API call fails
     * @throws InterruptedException If operation is interrupted
     */
    public FetchStats fetchNewAbstracts(int topicId, int maxResults)
            throws SQLException, IOException, InterruptedException {
        
        TopicDao.Topic topic = topicDao.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + topicId));
        
        if (topic.queryString() == null || topic.queryString().isBlank()) {
            throw new IllegalArgumentException("Topic has no query string defined");
        }
        
        logger.info("Fetching new abstracts for topic {} with query: '{}'", 
                topicId, topic.queryString());
        
        // Search PubMed
        List<String> candidatePmids = pubMedClient.search(topic.queryString(), maxResults);
        logger.info("Found {} candidate PMIDs", candidatePmids.size());
        
        // Get already seen PMIDs
        Set<String> seenPmids = new HashSet<>(pubMedSeenDao.findPmidsByTopic(topicId));
        
        // Filter to new PMIDs only
        List<String> newPmids = candidatePmids.stream()
                .filter(pmid -> !seenPmids.contains(pmid))
                .collect(Collectors.toList());
        
        logger.info("{} new PMIDs to fetch ({}skipped as already seen)",
                newPmids.size(), candidatePmids.size() - newPmids.size());
        
        if (newPmids.isEmpty()) {
            logger.info("No new abstracts to fetch");
            return new FetchStats(topicId, candidatePmids.size(), 0, 0);
        }
        
        // Fetch article details from PubMed
        List<PubMedClient.PubMedArticle> articles = pubMedClient.fetchArticles(newPmids);
        logger.info("Fetched {} articles from PubMed", articles.size());
        
        // Store abstracts
        List<AbstractDao.AbstractInput> abstractInputs = new ArrayList<>();
        for (PubMedClient.PubMedArticle article : articles) {
            String authorsJson = serializeAuthors(article.authors());
            
            abstractInputs.add(new AbstractDao.AbstractInput(
                    topicId,
                    article.pmid(),
                    article.title(),
                    authorsJson,
                    article.journal(),
                    article.pubDate(),
                    article.abstractText()
            ));
        }
        
        int stored = abstractDao.batchCreate(abstractInputs);
        logger.info("Stored {} new abstracts", stored);
        
        // Mark PMIDs as seen
        int marked = pubMedSeenDao.batchMarkAsSeen(topicId, newPmids);
        logger.info("Marked {} PMIDs as seen", marked);
        
        return new FetchStats(topicId, candidatePmids.size(), newPmids.size(), stored);
    }

    /**
     * Get statistics about abstracts for a topic.
     * 
     * @param topicId The topic ID
     * @return Statistics object
     * @throws SQLException If database operation fails
     */
    public TopicStats getTopicStats(int topicId) throws SQLException {
        int seenCount = pubMedSeenDao.countByTopic(topicId);
        int abstractCount = abstractDao.countByTopic(topicId);
        
        return new TopicStats(topicId, seenCount, abstractCount);
    }

    /**
     * Serialize authors list to JSON.
     */
    private String serializeAuthors(List<String> authors) {
        try {
            return objectMapper.writeValueAsString(authors);
        } catch (Exception e) {
            logger.warn("Failed to serialize authors to JSON: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * Record for search preview results.
     */
    public record SearchPreview(
            int topicId,
            String query,
            List<String> allPmids,
            List<String> newPmids,
            List<String> seenPmids
    ) {
        public int totalCount() { return allPmids.size(); }
        public int newCount() { return newPmids.size(); }
        public int seenCount() { return seenPmids.size(); }
    }

    /**
     * Record for fetch operation statistics.
     */
    public record FetchStats(
            int topicId,
            int candidateCount,
            int newPmidCount,
            int storedAbstractCount
    ) {
        @Override
        public String toString() {
            return String.format("FetchStats{topicId=%d, candidates=%d, new=%d, stored=%d}",
                    topicId, candidateCount, newPmidCount, storedAbstractCount);
        }
    }

    /**
     * Record for topic statistics.
     */
    public record TopicStats(
            int topicId,
            int seenPmidCount,
            int storedAbstractCount
    ) {
        @Override
        public String toString() {
            return String.format("TopicStats{topicId=%d, seen=%d, stored=%d}",
                    topicId, seenPmidCount, storedAbstractCount);
        }
    }
}

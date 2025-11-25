package ca.zoltar.core.service;

import ca.zoltar.db.AbstractDao;
import ca.zoltar.db.TopicDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Demo/test class for Phase 4 implementation.
 * This demonstrates the PubMed monitoring functionality.
 */
public class Phase4Demo {
    private static final Logger logger = LoggerFactory.getLogger(Phase4Demo.class);

    public static void main(String[] args) {
        logger.info("=== Phase 4 Demo: PubMed Monitor & Abstract Storage ===");
        
        try {
            // Initialize services
            logger.info("Initializing services...");
            TopicDao topicDao = new TopicDao();
            AbstractDao abstractDao = new AbstractDao();
            PubMedMonitorService monitorService = new PubMedMonitorService();
            
            // Create a test topic with a PubMed query
            logger.info("\nCreating test topic...");
            TopicDao.Topic topic = topicDao.create(
                "CRISPR Gene Editing",
                "CRISPR[Title/Abstract] AND gene editing[Title/Abstract]",
                "Testing PubMed search for CRISPR gene editing articles"
            );
            logger.info("Created topic: {} (ID: {})", topic.name(), topic.id());
            logger.info("Query: {}", topic.queryString());
            
            // Get initial statistics
            logger.info("\n--- Initial Statistics ---");
            PubMedMonitorService.TopicStats initialStats = monitorService.getTopicStats(topic.id());
            logger.info("Seen PMIDs: {}", initialStats.seenPmidCount());
            logger.info("Stored abstracts: {}", initialStats.storedAbstractCount());
            
            // Perform dry run search
            logger.info("\n--- Dry Run Search (Preview) ---");
            logger.info("Searching PubMed (this may take a few seconds)...");
            PubMedMonitorService.SearchPreview preview = monitorService.dryRunSearch(topic.id(), 10);
            logger.info("Total results: {}", preview.totalCount());
            logger.info("New PMIDs: {}", preview.newCount());
            logger.info("Already seen: {}", preview.seenCount());
            
            if (!preview.newPmids().isEmpty()) {
                logger.info("\nNew PMIDs found:");
                for (int i = 0; i < Math.min(5, preview.newPmids().size()); i++) {
                    logger.info("  {}. PMID: {}", i + 1, preview.newPmids().get(i));
                }
                if (preview.newPmids().size() > 5) {
                    logger.info("  ... and {} more", preview.newPmids().size() - 5);
                }
            }
            
            // Fetch and store new abstracts
            logger.info("\n--- Fetching New Abstracts ---");
            logger.info("Fetching abstracts from PubMed (this may take a few seconds)...");
            PubMedMonitorService.FetchStats fetchStats = monitorService.fetchNewAbstracts(topic.id(), 10);
            logger.info("Fetch results:");
            logger.info("  Candidates found: {}", fetchStats.candidateCount());
            logger.info("  New PMIDs: {}", fetchStats.newPmidCount());
            logger.info("  Abstracts stored: {}", fetchStats.storedAbstractCount());
            
            // Get final statistics
            logger.info("\n--- Final Statistics ---");
            PubMedMonitorService.TopicStats finalStats = monitorService.getTopicStats(topic.id());
            logger.info("Seen PMIDs: {}", finalStats.seenPmidCount());
            logger.info("Stored abstracts: {}", finalStats.storedAbstractCount());
            
            // Display some sample abstracts
            logger.info("\n--- Sample Abstracts ---");
            List<AbstractDao.Abstract> abstracts = abstractDao.findByTopic(topic.id());
            int sampleCount = Math.min(3, abstracts.size());
            
            for (int i = 0; i < sampleCount; i++) {
                AbstractDao.Abstract abs = abstracts.get(i);
                logger.info("\n{}. PMID: {}", i + 1, abs.pmid());
                logger.info("   Title: {}", abs.title());
                logger.info("   Journal: {}", abs.journal());
                logger.info("   Pub Date: {}", abs.pubDate());
                logger.info("   Authors: {}", abs.authorsJson());
                logger.info("   Abstract: {}...", 
                        abs.abstractText().length() > 100 
                                ? abs.abstractText().substring(0, 100) 
                                : abs.abstractText());
            }
            
            if (abstracts.size() > sampleCount) {
                logger.info("\n... and {} more abstracts", abstracts.size() - sampleCount);
            }
            
            // Try fetching again to test deduplication
            logger.info("\n--- Testing Deduplication ---");
            logger.info("Fetching again with same query...");
            PubMedMonitorService.FetchStats secondFetch = monitorService.fetchNewAbstracts(topic.id(), 10);
            logger.info("Second fetch results:");
            logger.info("  Candidates found: {}", secondFetch.candidateCount());
            logger.info("  New PMIDs: {}", secondFetch.newPmidCount());
            logger.info("  Abstracts stored: {}", secondFetch.storedAbstractCount());
            
            if (secondFetch.newPmidCount() == 0) {
                logger.info("✓ Deduplication working correctly - no duplicates stored");
            }
            
            logger.info("\n=== Phase 4 Demo Complete ===");
            logger.info("Successfully demonstrated:");
            logger.info("1. PubMed search using E-utilities");
            logger.info("2. Dry run preview to identify new vs seen PMIDs");
            logger.info("3. Fetching and storing new abstracts");
            logger.info("4. Deduplication to avoid re-processing");
            logger.info("5. Statistics tracking");
            
        } catch (Exception e) {
            logger.error("Demo failed", e);
            System.exit(1);
        }
    }
}

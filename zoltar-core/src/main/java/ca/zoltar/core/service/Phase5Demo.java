package ca.zoltar.core.service;

import ca.zoltar.db.AbstractDao;
import ca.zoltar.db.ChunkDao;
import ca.zoltar.db.TopicDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Demo for Phase 5: Hybrid Retrieval Service
 * 
 * Demonstrates:
 * - Retrieving a stored PubMed abstract
 * - Running hybrid retrieval (semantic + lexical)
 * - Displaying top-K results with hybrid scores
 * - Testing parameter tuning (alpha, K values)
 */
public class Phase5Demo {
    private static final Logger logger = LoggerFactory.getLogger(Phase5Demo.class);
    
    public static void main(String[] args) {
        logger.info("=== Phase 5 Demo: Hybrid Retrieval Service ===");
        logger.info("NOTE: This demo requires an OpenAI API key to be configured.");
        logger.info("Set 'openai.apiKey' in ~/.zoltar-java/config.json");
        logger.info("");
        
        try {
            // Initialize services
            logger.info("Initializing services...");
            TopicDao topicDao = new TopicDao();
            ChunkDao chunkDao = new ChunkDao();
            AbstractDao abstractDao = new AbstractDao();
            HybridRetrievalService retrievalService = new HybridRetrievalService();
            
            // Get an existing topic with indexed content
            logger.info("\nFinding topic with indexed content...");
            List<TopicDao.Topic> topics = topicDao.findAll();
            if (topics.isEmpty()) {
                logger.error("No topics found. Please run Phase 1 demo first.");
                return;
            }
            
            // Find a topic with both chunks and abstracts
            TopicDao.Topic topic = null;
            for (TopicDao.Topic t : topics) {
                int chunkCount = chunkDao.countByTopicId(t.id());
                int abstractCount = abstractDao.countByTopic(t.id());
                if (chunkCount > 0 && abstractCount > 0) {
                    topic = t;
                    logger.info("Found topic with {} chunks and {} abstracts: {} (ID: {})", 
                            chunkCount, abstractCount, t.name(), t.id());
                    break;
                }
            }
            
            if (topic == null) {
                // If no topic has both, find one with just chunks
                for (TopicDao.Topic t : topics) {
                    int chunkCount = chunkDao.countByTopicId(t.id());
                    if (chunkCount > 0) {
                        topic = t;
                        logger.info("Found topic with {} chunks: {} (ID: {})", 
                                chunkCount, t.name(), t.id());
                        break;
                    }
                }
            }
            
            if (topic == null) {
                logger.error("No topics with indexed chunks found. Please run Phase 2-3 demos first.");
                return;
            }
            
            // Get a stored abstract to use as query, or use a sample query
            String queryText;
            List<AbstractDao.Abstract> abstracts = abstractDao.findByTopic(topic.id());
            if (!abstracts.isEmpty()) {
                AbstractDao.Abstract abstract1 = abstracts.get(0);
                logger.info("\nUsing stored abstract: PMID {} - {}", abstract1.pmid(), abstract1.title());
                queryText = abstract1.title() + "\n\n" + abstract1.abstractText();
            } else {
                logger.info("\nNo abstracts found, using sample query text");
                queryText = "CRISPR gene editing technology for treating genetic diseases. " +
                        "This paper describes novel applications of CRISPR-Cas9 for therapeutic interventions.";
            }
            
            logger.info("Query text length: {} characters", queryText.length());
            
            // === Test 1: Default parameters ===
            logger.info("\n--- Test 1: Default Parameters (α=0.6, K_sem=200, K_lex=200, K_ctx=30) ---");
            List<HybridRetrievalService.RetrievalResult> results1 = 
                    retrievalService.retrieve(topic.id(), queryText);
            
            displayResults(results1, 10);
            
            // === Test 2: Higher semantic weight ===
            logger.info("\n--- Test 2: Higher Semantic Weight (α=0.8) ---");
            retrievalService.setAlpha(0.8);
            List<HybridRetrievalService.RetrievalResult> results2 = 
                    retrievalService.retrieve(topic.id(), queryText);
            
            displayResults(results2, 5);
            
            // === Test 3: Higher lexical weight ===
            logger.info("\n--- Test 3: Higher Lexical Weight (α=0.3) ---");
            retrievalService.setAlpha(0.3);
            List<HybridRetrievalService.RetrievalResult> results3 = 
                    retrievalService.retrieve(topic.id(), queryText);
            
            displayResults(results3, 5);
            
            // === Test 4: Fewer context chunks ===
            logger.info("\n--- Test 4: Fewer Context Chunks (K_ctx=10) ---");
            retrievalService.setAlpha(0.6); // Reset to default
            retrievalService.setKContext(10);
            List<HybridRetrievalService.RetrievalResult> results4 = 
                    retrievalService.retrieve(topic.id(), queryText);
            
            logger.info("Returned {} chunks (expected 10)", results4.size());
            displayResults(results4, 10);
            
            // === Summary statistics ===
            logger.info("\n--- Summary Statistics ---");
            logger.info("Test 1 (α=0.6): {} chunks, avg hybrid score: {:.3f}", 
                    results1.size(), 
                    results1.stream().mapToDouble(HybridRetrievalService.RetrievalResult::hybridScore).average().orElse(0.0));
            logger.info("Test 2 (α=0.8): {} chunks, avg hybrid score: {:.3f}", 
                    results2.size(), 
                    results2.stream().mapToDouble(HybridRetrievalService.RetrievalResult::hybridScore).average().orElse(0.0));
            logger.info("Test 3 (α=0.3): {} chunks, avg hybrid score: {:.3f}", 
                    results3.size(), 
                    results3.stream().mapToDouble(HybridRetrievalService.RetrievalResult::hybridScore).average().orElse(0.0));
            logger.info("Test 4 (K_ctx=10): {} chunks, avg hybrid score: {:.3f}", 
                    results4.size(), 
                    results4.stream().mapToDouble(HybridRetrievalService.RetrievalResult::hybridScore).average().orElse(0.0));
            
            logger.info("\n=== Phase 5 Demo Complete ===");
            
        } catch (Exception e) {
            logger.error("Demo failed", e);
            System.exit(1);
        }
    }
    
    /**
     * Display retrieval results in a formatted way.
     */
    private static void displayResults(List<HybridRetrievalService.RetrievalResult> results, int limit) {
        if (results.isEmpty()) {
            logger.info("No results found.");
            return;
        }
        
        logger.info("Top {} results (out of {}):", Math.min(limit, results.size()), results.size());
        
        int count = 0;
        for (HybridRetrievalService.RetrievalResult result : results) {
            if (++count > limit) break;
            
            logger.info("\n{}. Chunk ID: {} (Document: {}, Index: {})", 
                    count, result.chunkId(), result.documentId(), result.chunkIndex());
            logger.info("   Hybrid: {:.4f} | Semantic: {:.4f} | Lexical: {:.4f}", 
                    result.hybridScore(), result.semanticScore(), result.lexicalScore());
            
            // Show first 150 chars of text
            String preview = result.text().length() > 150 
                    ? result.text().substring(0, 150) + "..." 
                    : result.text();
            logger.info("   Text: {}", preview.replace("\n", " "));
        }
        
        if (results.size() > limit) {
            logger.info("\n... and {} more results", results.size() - limit);
        }
    }
}

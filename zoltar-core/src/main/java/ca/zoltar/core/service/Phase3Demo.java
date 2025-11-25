package ca.zoltar.core.service;

import ca.zoltar.db.*;
import ca.zoltar.search.LuceneSearcher;
import ca.zoltar.util.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Demo/test class for Phase 3 implementation.
 * This demonstrates the embeddings and Lucene indexing functionality.
 */
public class Phase3Demo {
    private static final Logger logger = LoggerFactory.getLogger(Phase3Demo.class);

    public static void main(String[] args) {
        logger.info("=== Phase 3 Demo: Embeddings & Lucene Indexing ===");
        
        try {
            // Initialize services
            logger.info("Initializing services...");
            TopicDao topicDao = new TopicDao();
            DocumentDao documentDao = new DocumentDao();
            ChunkDao chunkDao = new ChunkDao();
            IndexingService indexingService = new IndexingService();
            
            // Create a test topic
            logger.info("Creating test topic...");
            TopicDao.Topic topic = topicDao.create(
                "Macrophages in Pulmonary Fibrosis",
                "macrophages AND pulmonary fibrosis",
                "Test topic for Phase 3 demo"
            );
            logger.info("Created topic: {} (ID: {})", topic.name(), topic.id());
            
            // Create a test document
            logger.info("Creating test document...");
            DocumentDao.Document document = documentDao.create(
                topic.id(),
                "The Role of Macrophages in Lung Fibrosis",
                "/path/to/test.pdf",
                "10.1234/example.doi",
                null,
                2024,
                "Journal of Pulmonary Research",
                "test-hash-" + System.currentTimeMillis()
            );
            logger.info("Created document: {} (ID: {})", document.title(), document.id());
            
            // Create test chunks
            logger.info("Creating test chunks...");
            List<ChunkDao.Chunk> chunks = List.of(
                new ChunkDao.Chunk(
                    0, document.id(), topic.id(), 0,
                    "Macrophages play a crucial role in the pathogenesis of pulmonary fibrosis. " +
                    "These immune cells accumulate in the lungs and contribute to inflammation and tissue remodeling.",
                    30, null, LocalDateTime.now().toString()
                ),
                new ChunkDao.Chunk(
                    0, document.id(), topic.id(), 1,
                    "Interstitial lung diseases, including idiopathic pulmonary fibrosis, are characterized by " +
                    "progressive scarring of lung tissue. Macrophage polarization is a key factor in disease progression.",
                    28, null, LocalDateTime.now().toString()
                ),
                new ChunkDao.Chunk(
                    0, document.id(), topic.id(), 2,
                    "Recent studies have identified specific macrophage subsets that promote fibrosis through " +
                    "the production of profibrotic mediators such as TGF-beta and PDGF.",
                    26, null, LocalDateTime.now().toString()
                )
            );
            chunkDao.batchCreate(chunks);
            logger.info("Created {} test chunks", chunks.size());
            
            // Check indexing status before
            logger.info("\n--- Indexing Status Before ---");
            IndexingService.TopicIndexStatus statusBefore = indexingService.getTopicIndexStatus(topic.id());
            logger.info("Total chunks: {}", statusBefore.totalChunks());
            logger.info("Embedded chunks: {}", statusBefore.embeddedChunks());
            logger.info("Lucene index exists: {}", statusBefore.luceneIndexExists());
            logger.info("Status: {}", statusBefore.status());
            
            // Check if OpenAI API key is configured
            String apiKey = (String) ConfigManager.getInstance().get("openai.apiKey");
            if (apiKey == null || apiKey.isBlank()) {
                logger.warn("\n!!! OpenAI API key not configured !!!");
                logger.warn("To test embeddings, set 'openai.apiKey' in ~/.zoltar-java/config.json");
                logger.warn("Proceeding with Lucene indexing only...\n");
                
                // Index in Lucene without embeddings
                logger.info("\n--- Starting Lucene Indexing (without embeddings) ---");
                List<ChunkDao.Chunk> topicChunks = chunkDao.findByTopicId(topic.id());
                List<ca.zoltar.search.LuceneIndexer.ChunkDocument> luceneChunks = topicChunks.stream()
                        .map(c -> new ca.zoltar.search.LuceneIndexer.ChunkDocument(
                                c.id(),
                                c.documentId(),
                                c.topicId(),
                                c.chunkIndex(),
                                c.text()
                        ))
                        .collect(java.util.stream.Collectors.toList());
                
                indexingService.searchService.rebuildIndex(luceneChunks, topic.id());
                logger.info("Lucene indexing completed for {} chunks", topicChunks.size());
            } else {
                // Generate embeddings and index
                logger.info("\n--- Starting Indexing Process ---");
                IndexingService.IndexingStats stats = indexingService.indexTopic(topic.id());
                logger.info("Indexing completed:");
                logger.info("  Total chunks: {}", stats.totalChunks());
                logger.info("  New embeddings: {}", stats.newEmbeddings());
                
                // Check indexing status after
                logger.info("\n--- Indexing Status After ---");
                IndexingService.TopicIndexStatus statusAfter = indexingService.getTopicIndexStatus(topic.id());
                logger.info("Total chunks: {}", statusAfter.totalChunks());
                logger.info("Embedded chunks: {}", statusAfter.embeddedChunks());
                logger.info("Lucene index exists: {}", statusAfter.luceneIndexExists());
                logger.info("Lucene doc count: {}", statusAfter.luceneDocCount());
                logger.info("Status: {}", statusAfter.status());
                logger.info("Fully indexed: {}", statusAfter.isFullyIndexed());
            }
            
            // Test Lucene search (works even without embeddings)
            logger.info("\n--- Testing Lucene Search ---");
            testSearch(indexingService, topic.id(), "macrophages inflammation");
            testSearch(indexingService, topic.id(), "pulmonary fibrosis TGF-beta");
            testSearch(indexingService, topic.id(), "lung tissue scarring");
            
            logger.info("\n=== Phase 3 Demo Complete ===");
            logger.info("Check the logs above to verify:");
            logger.info("1. Embeddings were generated (if API key configured)");
            logger.info("2. Lucene index was created");
            logger.info("3. Search queries return relevant results");
            
        } catch (Exception e) {
            logger.error("Demo failed", e);
            System.exit(1);
        }
    }
    
    private static void testSearch(IndexingService indexingService, int topicId, String query) {
        try {
            logger.info("Searching for: '{}'", query);
            List<LuceneSearcher.SearchHit> hits = indexingService.searchService.search(topicId, query, 5);
            
            if (hits.isEmpty()) {
                logger.info("  No results found");
            } else {
                for (int i = 0; i < hits.size(); i++) {
                    LuceneSearcher.SearchHit hit = hits.get(i);
                    logger.info("  {}. Chunk ID: {}, Score: {}", 
                            i + 1, hit.chunkId(), String.format("%.4f", hit.bm25Score()));
                }
            }
        } catch (Exception e) {
            logger.error("Search failed", e);
        }
    }
}

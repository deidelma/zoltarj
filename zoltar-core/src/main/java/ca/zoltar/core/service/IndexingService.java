package ca.zoltar.core.service;

import ca.zoltar.db.ChunkDao;
import ca.zoltar.db.DocumentDao;
import ca.zoltar.search.LuceneIndexer;
import ca.zoltar.search.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for orchestrating the indexing of documents, including both
 * embeddings generation and Lucene indexing.
 */
public class IndexingService {
    private static final Logger logger = LoggerFactory.getLogger(IndexingService.class);
    
    private final EmbeddingService embeddingService;
    final SearchService searchService; // Package-private for demo access
    private final ChunkDao chunkDao;
    private final DocumentDao documentDao;

    public IndexingService() {
        this.embeddingService = new EmbeddingService();
        this.searchService = new SearchService();
        this.chunkDao = new ChunkDao();
        this.documentDao = new DocumentDao();
    }

    /**
     * Fully index a document - generate embeddings and create Lucene index.
     * 
     * @param documentId The document ID to index
     * @return Statistics about the indexing operation
     * @throws SQLException If database operation fails
     * @throws IOException If indexing fails
     * @throws InterruptedException If the operation is interrupted
     */
    public IndexingStats indexDocument(int documentId) throws SQLException, IOException, InterruptedException {
        logger.info("Starting full indexing for document {}", documentId);
        
        // Get document info
        DocumentDao.Document document = documentDao.findById(documentId);
        if (document == null) {
            throw new IllegalArgumentException("Document not found: " + documentId);
        }
        
        int topicId = document.topicId();
        
        // Get chunks for this document
        List<ChunkDao.Chunk> chunks = chunkDao.findByDocumentId(documentId);
        logger.info("Found {} chunks for document {}", chunks.size(), documentId);
        
        // Generate embeddings
        logger.info("Generating embeddings for document {}", documentId);
        int embeddingsCreated = embeddingService.generateEmbeddingsForDocument(documentId);
        
        // Index in Lucene
        logger.info("Indexing chunks in Lucene for document {}", documentId);
        List<LuceneIndexer.ChunkDocument> luceneChunks = chunks.stream()
                .map(c -> new LuceneIndexer.ChunkDocument(
                        c.id(),
                        c.documentId(),
                        c.topicId(),
                        c.chunkIndex(),
                        c.text()
                ))
                .collect(Collectors.toList());
        
        searchService.batchIndexChunks(luceneChunks, topicId);
        
        logger.info("Completed indexing for document {}: {} chunks, {} new embeddings", 
                documentId, chunks.size(), embeddingsCreated);
        
        return new IndexingStats(documentId, topicId, chunks.size(), embeddingsCreated);
    }

    /**
     * Fully index a topic - generate embeddings and create Lucene index for all documents.
     * 
     * @param topicId The topic ID to index
     * @return Statistics about the indexing operation
     * @throws SQLException If database operation fails
     * @throws IOException If indexing fails
     * @throws InterruptedException If the operation is interrupted
     */
    public IndexingStats indexTopic(int topicId) throws SQLException, IOException, InterruptedException {
        logger.info("Starting full indexing for topic {}", topicId);
        
        // Get all chunks for this topic
        List<ChunkDao.Chunk> chunks = chunkDao.findByTopicId(topicId);
        logger.info("Found {} chunks for topic {}", chunks.size(), topicId);
        
        if (chunks.isEmpty()) {
            logger.warn("No chunks found for topic {}", topicId);
            return new IndexingStats(0, topicId, 0, 0);
        }
        
        // Generate embeddings
        logger.info("Generating embeddings for topic {}", topicId);
        int embeddingsCreated = embeddingService.generateEmbeddingsForTopic(topicId);
        
        // Rebuild Lucene index
        logger.info("Rebuilding Lucene index for topic {}", topicId);
        List<LuceneIndexer.ChunkDocument> luceneChunks = chunks.stream()
                .map(c -> new LuceneIndexer.ChunkDocument(
                        c.id(),
                        c.documentId(),
                        c.topicId(),
                        c.chunkIndex(),
                        c.text()
                ))
                .collect(Collectors.toList());
        
        searchService.rebuildIndex(luceneChunks, topicId);
        
        logger.info("Completed indexing for topic {}: {} chunks, {} new embeddings", 
                topicId, chunks.size(), embeddingsCreated);
        
        return new IndexingStats(0, topicId, chunks.size(), embeddingsCreated);
    }

    /**
     * Get indexing status for a topic.
     * 
     * @param topicId The topic ID
     * @return Status information
     * @throws SQLException If database operation fails
     * @throws IOException If index access fails
     */
    public TopicIndexStatus getTopicIndexStatus(int topicId) throws SQLException, IOException {
        EmbeddingService.EmbeddingStats embStats = embeddingService.getStatsForTopic(topicId);
        
        boolean luceneIndexExists = searchService.indexExists(topicId);
        int luceneDocCount = luceneIndexExists ? searchService.getIndexSize(topicId) : 0;
        
        return new TopicIndexStatus(
                topicId,
                embStats.totalChunks(),
                embStats.embeddedChunks(),
                luceneIndexExists,
                luceneDocCount
        );
    }

    /**
     * Record for indexing statistics.
     */
    public record IndexingStats(
            int documentId,
            int topicId,
            int totalChunks,
            int newEmbeddings
    ) {}

    /**
     * Record for topic index status.
     */
    public record TopicIndexStatus(
            int topicId,
            int totalChunks,
            int embeddedChunks,
            boolean luceneIndexExists,
            int luceneDocCount
    ) {
        public boolean isFullyIndexed() {
            return totalChunks > 0 
                    && embeddedChunks == totalChunks 
                    && luceneIndexExists 
                    && luceneDocCount == totalChunks;
        }
        
        public String status() {
            if (totalChunks == 0) {
                return "No chunks";
            } else if (isFullyIndexed()) {
                return "Fully indexed";
            } else if (embeddedChunks == 0 && !luceneIndexExists) {
                return "Not indexed";
            } else {
                return "Partially indexed";
            }
        }
    }
}

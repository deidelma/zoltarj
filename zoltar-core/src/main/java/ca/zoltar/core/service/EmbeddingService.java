package ca.zoltar.core.service;

import ca.zoltar.db.ChunkDao;
import ca.zoltar.db.EmbeddingDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing embeddings for text chunks.
 */
public class EmbeddingService {
    private static final Logger logger = LoggerFactory.getLogger(EmbeddingService.class);
    
    private final OpenAIClient openAIClient;
    private final EmbeddingDao embeddingDao;
    private final ChunkDao chunkDao;

    public EmbeddingService() {
        this.openAIClient = new OpenAIClient();
        this.embeddingDao = new EmbeddingDao();
        this.chunkDao = new ChunkDao();
    }

    /**
     * Generate and store embedding for a single chunk.
     * 
     * @param chunkId The chunk ID to generate embedding for
     * @return The ID of the created embedding
     * @throws SQLException If database operation fails
     * @throws IOException If OpenAI API call fails
     * @throws InterruptedException If the operation is interrupted
     */
    public int generateAndStoreEmbedding(int chunkId) throws SQLException, IOException, InterruptedException {
        String model = openAIClient.getEmbeddingModel();
        
        // Check if embedding already exists
        EmbeddingDao.Embedding existing = embeddingDao.findByChunkIdAndModel(chunkId, model);
        if (existing != null) {
            logger.debug("Embedding already exists for chunk {} with model {}", chunkId, model);
            return existing.id();
        }

        // Get chunk text
        ChunkDao.Chunk chunk = chunkDao.findById(chunkId);
        if (chunk == null) {
            throw new IllegalArgumentException("Chunk not found: " + chunkId);
        }

        // Generate embedding
        logger.info("Generating embedding for chunk {} (length: {})", chunkId, chunk.text().length());
        float[] vector = openAIClient.generateEmbedding(chunk.text());

        // Store embedding
        int embeddingId = embeddingDao.create(chunkId, model, vector);
        logger.info("Stored embedding {} for chunk {}", embeddingId, chunkId);
        
        return embeddingId;
    }

    /**
     * Generate and store embeddings for all chunks in a document.
     * 
     * @param documentId The document ID
     * @return Number of embeddings created
     * @throws SQLException If database operation fails
     * @throws IOException If OpenAI API call fails
     * @throws InterruptedException If the operation is interrupted
     */
    public int generateEmbeddingsForDocument(int documentId) throws SQLException, IOException, InterruptedException {
        List<ChunkDao.Chunk> chunks = chunkDao.findByDocumentId(documentId);
        logger.info("Generating embeddings for {} chunks in document {}", chunks.size(), documentId);
        
        String model = openAIClient.getEmbeddingModel();
        int created = 0;
        
        for (ChunkDao.Chunk chunk : chunks) {
            // Check if embedding already exists
            EmbeddingDao.Embedding existing = embeddingDao.findByChunkIdAndModel(chunk.id(), model);
            if (existing != null) {
                logger.debug("Skipping chunk {} - embedding already exists", chunk.id());
                continue;
            }
            
            try {
                // Generate embedding
                float[] vector = openAIClient.generateEmbedding(chunk.text());
                
                // Store embedding
                embeddingDao.create(chunk.id(), model, vector);
                created++;
                
                // Add a small delay to avoid rate limiting
                Thread.sleep(100);
                
            } catch (IOException | InterruptedException e) {
                logger.error("Failed to generate embedding for chunk {}: {}", chunk.id(), e.getMessage());
                throw e;
            }
        }
        
        logger.info("Created {} new embeddings for document {}", created, documentId);
        return created;
    }

    /**
     * Generate and store embeddings for all chunks in a topic.
     * 
     * @param topicId The topic ID
     * @return Number of embeddings created
     * @throws SQLException If database operation fails
     * @throws IOException If OpenAI API call fails
     * @throws InterruptedException If the operation is interrupted
     */
    public int generateEmbeddingsForTopic(int topicId) throws SQLException, IOException, InterruptedException {
        List<ChunkDao.Chunk> chunks = chunkDao.findByTopicId(topicId);
        logger.info("Generating embeddings for {} chunks in topic {}", chunks.size(), topicId);
        
        String model = openAIClient.getEmbeddingModel();
        int created = 0;
        
        for (ChunkDao.Chunk chunk : chunks) {
            // Check if embedding already exists
            EmbeddingDao.Embedding existing = embeddingDao.findByChunkIdAndModel(chunk.id(), model);
            if (existing != null) {
                logger.debug("Skipping chunk {} - embedding already exists", chunk.id());
                continue;
            }
            
            try {
                // Generate embedding
                float[] vector = openAIClient.generateEmbedding(chunk.text());
                
                // Store embedding
                embeddingDao.create(chunk.id(), model, vector);
                created++;
                
                // Add a small delay to avoid rate limiting
                Thread.sleep(100);
                
            } catch (IOException | InterruptedException e) {
                logger.error("Failed to generate embedding for chunk {}: {}", chunk.id(), e.getMessage());
                throw e;
            }
        }
        
        logger.info("Created {} new embeddings for topic {}", created, topicId);
        return created;
    }

    /**
     * Calculate cosine similarity between two vectors.
     * 
     * @param a First vector
     * @param b Second vector
     * @return Cosine similarity score (0 to 1)
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vectors must have the same dimension");
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Get embedding statistics for a topic.
     * 
     * @param topicId The topic ID
     * @return Statistics object
     * @throws SQLException If database operation fails
     */
    public EmbeddingStats getStatsForTopic(int topicId) throws SQLException {
        String model = openAIClient.getEmbeddingModel();
        int totalChunks = chunkDao.countByTopicId(topicId);
        int embeddedChunks = embeddingDao.countByTopicId(topicId, model);
        
        return new EmbeddingStats(topicId, model, totalChunks, embeddedChunks);
    }

    /**
     * Record for embedding statistics.
     */
    public record EmbeddingStats(int topicId, String model, int totalChunks, int embeddedChunks) {
        public int missingEmbeddings() {
            return totalChunks - embeddedChunks;
        }
        
        public double completionPercentage() {
            if (totalChunks == 0) return 0.0;
            return (embeddedChunks * 100.0) / totalChunks;
        }
    }
}

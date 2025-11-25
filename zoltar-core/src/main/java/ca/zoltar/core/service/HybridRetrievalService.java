package ca.zoltar.core.service;

import ca.zoltar.db.ChunkDao;
import ca.zoltar.db.EmbeddingDao;
import ca.zoltar.search.LuceneSearcher;
import org.apache.lucene.queryparser.classic.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for hybrid retrieval combining semantic (embedding) and lexical (BM25) search.
 * 
 * Implements the hybrid retrieval strategy:
 * 1. Semantic retrieval: Find top K_sem chunks by cosine similarity
 * 2. Lexical retrieval: Find top K_lex chunks by BM25 score
 * 3. Merge and normalize scores
 * 4. Hybrid scoring: H = α * semantic_score + (1 - α) * bm25_score
 * 5. Return top K_ctx chunks
 */
public class HybridRetrievalService {
    private static final Logger logger = LoggerFactory.getLogger(HybridRetrievalService.class);
    
    private final OpenAIClient openAIClient;
    private final EmbeddingDao embeddingDao;
    private final ChunkDao chunkDao;
    private final LuceneSearcher luceneSearcher;
    
    // Default parameters
    private double alpha = 0.6;          // Weight for semantic score (vs lexical)
    private int kSemantic = 200;         // Top K candidates from semantic search
    private int kLexical = 200;          // Top K candidates from lexical search
    private int kContext = 30;           // Final top K chunks to return
    
    public HybridRetrievalService() {
        this.openAIClient = new OpenAIClient();
        this.embeddingDao = new EmbeddingDao();
        this.chunkDao = new ChunkDao();
        this.luceneSearcher = new LuceneSearcher();
    }
    
    /**
     * Retrieve relevant chunks for a given query text using hybrid retrieval.
     * 
     * @param topicId The topic to search within
     * @param queryText The query text (e.g., PubMed abstract)
     * @return List of retrieval results ranked by hybrid score
     * @throws SQLException If database operation fails
     * @throws IOException If OpenAI API or Lucene operation fails
     * @throws InterruptedException If operation is interrupted
     * @throws ParseException If Lucene query parsing fails
     */
    public List<RetrievalResult> retrieve(int topicId, String queryText) 
            throws SQLException, IOException, InterruptedException, ParseException {
        
        logger.info("Starting hybrid retrieval for topic {} with query length {}", 
                topicId, queryText.length());
        
        // Step 1: Generate embedding for query
        float[] queryEmbedding = openAIClient.generateEmbedding(queryText);
        String embeddingModel = openAIClient.getEmbeddingModel();
        
        // Step 2: Semantic retrieval
        logger.debug("Performing semantic retrieval (K_sem={})", kSemantic);
        Map<Integer, Double> semanticScores = performSemanticRetrieval(
                topicId, queryEmbedding, embeddingModel);
        
        // Step 3: Lexical retrieval
        logger.debug("Performing lexical retrieval (K_lex={})", kLexical);
        Map<Integer, Float> lexicalScores = performLexicalRetrieval(topicId, queryText);
        
        // Step 4: Merge candidates
        Set<Integer> allChunkIds = new HashSet<>();
        allChunkIds.addAll(semanticScores.keySet());
        allChunkIds.addAll(lexicalScores.keySet());
        
        logger.debug("Merging {} unique chunks from {} semantic + {} lexical", 
                allChunkIds.size(), semanticScores.size(), lexicalScores.size());
        
        // Step 5: Normalize scores
        Map<Integer, Double> normalizedSemantic = normalizeScores(semanticScores);
        Map<Integer, Double> normalizedLexical = normalizeScores(
                lexicalScores.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> (double) e.getValue())));
        
        // Step 6: Calculate hybrid scores
        List<RetrievalResult> results = new ArrayList<>();
        for (int chunkId : allChunkIds) {
            double semScore = normalizedSemantic.getOrDefault(chunkId, 0.0);
            double lexScore = normalizedLexical.getOrDefault(chunkId, 0.0);
            double hybridScore = alpha * semScore + (1.0 - alpha) * lexScore;
            
            // Get chunk metadata
            ChunkDao.Chunk chunk = chunkDao.findById(chunkId);
            if (chunk == null) {
                logger.warn("Chunk {} not found in database, skipping", chunkId);
                continue;
            }
            
            results.add(new RetrievalResult(
                    chunkId,
                    chunk.documentId(),
                    topicId,
                    chunk.chunkIndex(),
                    chunk.text(),
                    hybridScore,
                    semScore,
                    lexScore
            ));
        }
        
        // Step 7: Sort by hybrid score and take top K_ctx
        results.sort(Comparator.comparingDouble(RetrievalResult::hybridScore).reversed());
        List<RetrievalResult> topResults = results.stream()
                .limit(kContext)
                .collect(Collectors.toList());
        
        logger.info("Hybrid retrieval completed: {} total candidates, returning top {}", 
                results.size(), topResults.size());
        
        return topResults;
    }
    
    /**
     * Perform semantic retrieval using cosine similarity.
     */
    private Map<Integer, Double> performSemanticRetrieval(
            int topicId, float[] queryEmbedding, String embeddingModel) throws SQLException {
        
        // Get all embeddings for this topic
        List<EmbeddingDao.Embedding> embeddings = embeddingDao.findByTopicId(
                topicId, embeddingModel);
        
        if (embeddings.isEmpty()) {
            logger.warn("No embeddings found for topic {} with model {}", topicId, embeddingModel);
            return Map.of();
        }
        
        // Calculate cosine similarity for each embedding
        Map<Integer, Double> scores = new HashMap<>();
        for (EmbeddingDao.Embedding embedding : embeddings) {
            double similarity = VectorSimilarity.cosineSimilarity(queryEmbedding, embedding.vector());
            scores.put(embedding.chunkId(), similarity);
        }
        
        // Return top K_sem
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .limit(kSemantic)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    
    /**
     * Perform lexical retrieval using Lucene BM25.
     */
    private Map<Integer, Float> performLexicalRetrieval(int topicId, String queryText) 
            throws IOException, ParseException {
        
        List<LuceneSearcher.SearchHit> hits = luceneSearcher.search(topicId, queryText, kLexical);
        
        Map<Integer, Float> scores = new HashMap<>();
        for (LuceneSearcher.SearchHit hit : hits) {
            scores.put(hit.chunkId(), hit.bm25Score());
        }
        
        return scores;
    }
    
    /**
     * Normalize scores to [0, 1] range using min-max normalization.
     */
    private Map<Integer, Double> normalizeScores(Map<Integer, Double> scores) {
        if (scores.isEmpty()) {
            return Map.of();
        }
        
        double min = scores.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        
        if (max == min) {
            // All scores are the same, normalize to 1.0
            return scores.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> 1.0));
        }
        
        return scores.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> (e.getValue() - min) / (max - min)
                ));
    }
    
    // Configuration methods
    
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Alpha must be in range [0, 1]");
        }
        this.alpha = alpha;
    }
    
    public void setKSemantic(int kSemantic) {
        if (kSemantic <= 0) {
            throw new IllegalArgumentException("K_semantic must be positive");
        }
        this.kSemantic = kSemantic;
    }
    
    public void setKLexical(int kLexical) {
        if (kLexical <= 0) {
            throw new IllegalArgumentException("K_lexical must be positive");
        }
        this.kLexical = kLexical;
    }
    
    public void setKContext(int kContext) {
        if (kContext <= 0) {
            throw new IllegalArgumentException("K_context must be positive");
        }
        this.kContext = kContext;
    }
    
    public double getAlpha() {
        return alpha;
    }
    
    public int getKSemantic() {
        return kSemantic;
    }
    
    public int getKLexical() {
        return kLexical;
    }
    
    public int getKContext() {
        return kContext;
    }
    
    /**
     * Result of hybrid retrieval for a single chunk.
     */
    public record RetrievalResult(
            int chunkId,
            int documentId,
            int topicId,
            int chunkIndex,
            String text,
            double hybridScore,
            double semanticScore,
            double lexicalScore
    ) {}
}

package ca.zoltar.core.service;

import ca.zoltar.db.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.lucene.queryparser.classic.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for evaluating novelty of PubMed abstracts using LLM.
 * 
 * Orchestrates:
 * 1. Hybrid retrieval of relevant context chunks
 * 2. Prompt construction for LLM
 * 3. LLM evaluation via OpenAI
 * 4. Parsing and validation of results
 * 5. Persistence of evaluation runs and results
 */
public class EvaluationService {
    private static final Logger logger = LoggerFactory.getLogger(EvaluationService.class);
    
    private final HybridRetrievalService retrievalService;
    private final OpenAIClient openAIClient;
    private final AbstractDao abstractDao;
    private final DocumentDao documentDao;
    private final TopicDao topicDao;
    private final EvaluationRunDao evaluationRunDao;
    private final EvaluationResultDao evaluationResultDao;
    private final ObjectMapper objectMapper;
    
    public EvaluationService() {
        this.retrievalService = new HybridRetrievalService();
        this.openAIClient = new OpenAIClient();
        this.abstractDao = new AbstractDao();
        this.documentDao = new DocumentDao();
        this.topicDao = new TopicDao();
        this.evaluationRunDao = new EvaluationRunDao();
        this.evaluationResultDao = new EvaluationResultDao();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Evaluate the novelty of a PubMed abstract.
     * 
     * @param topicId The topic ID
     * @param pmid The PubMed ID to evaluate
     * @return The evaluation run ID
     * @throws SQLException If database operation fails
     * @throws IOException If OpenAI API call fails
     * @throws InterruptedException If operation is interrupted
     * @throws ParseException If Lucene query parsing fails
     */
    public int evaluateAbstract(int topicId, String pmid) 
            throws SQLException, IOException, InterruptedException, ParseException {
        
        logger.info("Starting novelty evaluation for PMID {} in topic {}", pmid, topicId);
        
        // 1. Retrieve the abstract
        AbstractDao.Abstract abstract1 = abstractDao.findByTopicAndPmid(topicId, pmid);
        if (abstract1 == null) {
            throw new IllegalArgumentException("Abstract not found for PMID " + pmid + " in topic " + topicId);
        }
        
        // 2. Get topic info
        TopicDao.Topic topic = topicDao.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + topicId));
        
        // 3. Retrieve relevant context using hybrid retrieval
        String queryText = abstract1.title() + "\n\n" + abstract1.abstractText();
        logger.info("Retrieving context chunks for abstract");
        
        List<HybridRetrievalService.RetrievalResult> retrievalResults = 
                retrievalService.retrieve(topicId, queryText);
        
        if (retrievalResults.isEmpty()) {
            throw new IllegalStateException("No context chunks found for evaluation");
        }
        
        logger.info("Retrieved {} context chunks", retrievalResults.size());
        
        // 4. Build prompts
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(topic, abstract1, retrievalResults);
        
        // 5. Call LLM for evaluation
        logger.info("Calling LLM for novelty evaluation");
        String llmResponse = openAIClient.generateChatCompletion(systemPrompt, userPrompt, "json_object");
        
        // 6. Parse and validate response
        NoveltyEvaluationResult evaluationResult = parseEvaluationResponse(llmResponse);
        
        if (!evaluationResult.isValid()) {
            throw new IllegalStateException("Invalid evaluation result from LLM");
        }
        
        logger.info("LLM evaluation: label={}, score={}", 
                evaluationResult.noveltyLabel(), evaluationResult.noveltyScore());
        
        // 7. Persist results
        int runId = persistEvaluation(topicId, pmid, retrievalResults, evaluationResult);
        
        logger.info("Evaluation complete, run ID: {}", runId);
        return runId;
    }
    
    /**
     * Build the system prompt for the LLM.
     */
    private String buildSystemPrompt() {
        return """
            You are an expert research assistant in biomedical science.
            You receive a research topic, a new PubMed abstract, and a set of context passages from existing articles on that topic.
            Your task is to judge whether the abstract describes a novel contribution relative to the provided context.
            
            Focus on:
            - Novel methods, techniques, or experimental approaches
            - New populations, species, or systems studied
            - Unexpected outcomes or contradictory findings
            - New applications or combinations of existing knowledge
            
            Output your evaluation as a JSON object with these exact fields:
            {
              "novelty_label": "novel" | "uncertain" | "not_novel",
              "novelty_score": <integer 0-10, where 10 is highly novel>,
              "rationale": "<detailed explanation of your assessment>",
              "supporting_evidence": [
                {"chunk_id": <integer>, "explanation": "<brief note>"},
                ...
              ]
            }
            
            Be thorough in your rationale and cite specific chunk IDs that support your judgment.
            """;
    }
    
    /**
     * Build the user prompt with topic, abstract, and context.
     */
    private String buildUserPrompt(TopicDao.Topic topic, AbstractDao.Abstract abstract1,
                                   List<HybridRetrievalService.RetrievalResult> context) 
            throws SQLException {
        
        StringBuilder prompt = new StringBuilder();
        
        // Topic information
        prompt.append("TOPIC: ").append(topic.name()).append("\n");
        if (topic.notes() != null && !topic.notes().isBlank()) {
            prompt.append("Topic Notes: ").append(topic.notes()).append("\n");
        }
        prompt.append("\n");
        
        // Abstract to evaluate
        prompt.append("NEW ABSTRACT TO EVALUATE:\n");
        prompt.append("PMID: ").append(abstract1.pmid()).append("\n");
        prompt.append("Title: ").append(abstract1.title()).append("\n");
        if (abstract1.journal() != null) {
            prompt.append("Journal: ").append(abstract1.journal()).append("\n");
        }
        if (abstract1.pubDate() != null) {
            prompt.append("Publication Date: ").append(abstract1.pubDate()).append("\n");
        }
        prompt.append("\nAbstract Text:\n").append(abstract1.abstractText()).append("\n\n");
        
        // Context from retrieved chunks
        prompt.append("CONTEXT FROM EXISTING LITERATURE:\n");
        prompt.append("(Retrieved ").append(context.size()).append(" most relevant passages)\n\n");
        
        int chunkNum = 1;
        for (HybridRetrievalService.RetrievalResult result : context) {
            DocumentDao.Document doc = documentDao.findById(result.documentId());
            
            prompt.append(String.format("[Chunk %d - ID: %d, Hybrid Score: %.3f]\n", 
                    chunkNum++, result.chunkId(), result.hybridScore()));
            
            if (doc != null) {
                prompt.append("Source: ").append(doc.title() != null ? doc.title() : "Unknown");
                if (doc.year() != null) {
                    prompt.append(" (").append(doc.year()).append(")");
                }
                prompt.append("\n");
            }
            
            prompt.append("Text: ").append(result.text()).append("\n\n");
        }
        
        prompt.append("Based on this context, evaluate the novelty of the new abstract.");
        
        return prompt.toString();
    }
    
    /**
     * Parse the LLM response into a NoveltyEvaluationResult.
     */
    private NoveltyEvaluationResult parseEvaluationResponse(String jsonResponse) throws IOException {
        JsonNode root = objectMapper.readTree(jsonResponse);
        
        String noveltyLabel = root.get("novelty_label").asText();
        int noveltyScore = root.get("novelty_score").asInt();
        String rationale = root.get("rationale").asText();
        
        List<NoveltyEvaluationResult.ChunkEvidence> evidence = new ArrayList<>();
        JsonNode evidenceArray = root.get("supporting_evidence");
        if (evidenceArray != null && evidenceArray.isArray()) {
            for (JsonNode item : evidenceArray) {
                int chunkId = item.get("chunk_id").asInt();
                String explanation = item.get("explanation").asText();
                evidence.add(new NoveltyEvaluationResult.ChunkEvidence(chunkId, explanation));
            }
        }
        
        return new NoveltyEvaluationResult(noveltyLabel, noveltyScore, rationale, evidence);
    }
    
    /**
     * Persist the evaluation run and results to the database.
     */
    private int persistEvaluation(int topicId, String pmid, 
                                  List<HybridRetrievalService.RetrievalResult> retrievalResults,
                                  NoveltyEvaluationResult evaluationResult) throws SQLException, IOException {
        
        // Build params JSON
        ObjectNode params = objectMapper.createObjectNode();
        params.put("alpha", retrievalService.getAlpha());
        params.put("k_semantic", retrievalService.getKSemantic());
        params.put("k_lexical", retrievalService.getKLexical());
        params.put("k_context", retrievalService.getKContext());
        params.put("embedding_model", openAIClient.getEmbeddingModel());
        String paramsJson = objectMapper.writeValueAsString(params);
        
        // Create evaluation run
        int runId = evaluationRunDao.create(topicId, pmid, "gpt-4", paramsJson);
        
        // Build used_chunk_ids JSON
        List<Integer> chunkIds = retrievalResults.stream()
                .map(HybridRetrievalService.RetrievalResult::chunkId)
                .collect(Collectors.toList());
        String usedChunkIdsJson = objectMapper.writeValueAsString(chunkIds);
        
        // Build hybrid_scores JSON
        Map<Integer, Double> scoresMap = new HashMap<>();
        for (HybridRetrievalService.RetrievalResult result : retrievalResults) {
            scoresMap.put(result.chunkId(), result.hybridScore());
        }
        String hybridScoresJson = objectMapper.writeValueAsString(scoresMap);
        
        // Create evaluation result
        evaluationResultDao.create(
                runId,
                evaluationResult.noveltyLabel(),
                evaluationResult.noveltyScore(),
                evaluationResult.rationale(),
                usedChunkIdsJson,
                hybridScoresJson
        );
        
        return runId;
    }
    
    /**
     * Get a complete evaluation result with both run and result data.
     */
    public EvaluationSummary getEvaluationSummary(int runId) throws SQLException {
        EvaluationRunDao.EvaluationRun run = evaluationRunDao.findById(runId);
        if (run == null) {
            throw new IllegalArgumentException("Evaluation run not found: " + runId);
        }
        
        EvaluationResultDao.EvaluationResult result = evaluationResultDao.findByRunId(runId);
        if (result == null) {
            throw new IllegalArgumentException("Evaluation result not found for run: " + runId);
        }
        
        return new EvaluationSummary(run, result);
    }
    
    /**
     * Combined summary of evaluation run and result.
     */
    public record EvaluationSummary(
            EvaluationRunDao.EvaluationRun run,
            EvaluationResultDao.EvaluationResult result
    ) {}
}

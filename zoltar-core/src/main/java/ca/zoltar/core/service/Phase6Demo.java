package ca.zoltar.core.service;

import ca.zoltar.db.AbstractDao;
import ca.zoltar.db.EvaluationResultDao;
import ca.zoltar.db.EvaluationRunDao;
import ca.zoltar.db.TopicDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Demo for Phase 6: LLM Evaluation Pipeline
 * 
 * Demonstrates:
 * - End-to-end novelty evaluation of a PubMed abstract
 * - Hybrid retrieval of context
 * - LLM-based novelty assessment
 * - Persistence of evaluation results
 */
public class Phase6Demo {
    private static final Logger logger = LoggerFactory.getLogger(Phase6Demo.class);
    
    public static void main(String[] args) {
        logger.info("=== Phase 6 Demo: LLM Evaluation Pipeline ===");
        logger.info("NOTE: This demo requires an OpenAI API key to be configured.");
        logger.info("Set 'openai.apiKey' and 'openai.chatModel' in ~/.zoltar-java/config.json");
        logger.info("");
        
        try {
            // Initialize services
            logger.info("Initializing services...");
            TopicDao topicDao = new TopicDao();
            AbstractDao abstractDao = new AbstractDao();
            EvaluationService evaluationService = new EvaluationService();
            
            // Find a topic with abstracts and indexed content
            logger.info("\nFinding topic with abstracts and indexed content...");
            List<TopicDao.Topic> topics = topicDao.findAll();
            if (topics.isEmpty()) {
                logger.error("No topics found. Please run earlier demos first.");
                return;
            }
            
            TopicDao.Topic topic = null;
            AbstractDao.Abstract abstractToEvaluate = null;
            
            for (TopicDao.Topic t : topics) {
                List<AbstractDao.Abstract> abstracts = abstractDao.findByTopic(t.id());
                if (!abstracts.isEmpty()) {
                    topic = t;
                    abstractToEvaluate = abstracts.get(0);
                    logger.info("Found topic: {} (ID: {}) with {} abstracts", 
                            t.name(), t.id(), abstracts.size());
                    break;
                }
            }
            
            if (topic == null || abstractToEvaluate == null) {
                logger.error("No topics with abstracts found. Please run Phase 4 demo first.");
                return;
            }
            
            logger.info("\nAbstract to evaluate:");
            logger.info("  PMID: {}", abstractToEvaluate.pmid());
            logger.info("  Title: {}", abstractToEvaluate.title());
            logger.info("  Journal: {}", abstractToEvaluate.journal());
            logger.info("  Pub Date: {}", abstractToEvaluate.pubDate());
            
            // Run evaluation
            logger.info("\n--- Running Novelty Evaluation ---");
            logger.info("This will:");
            logger.info("  1. Retrieve relevant context chunks using hybrid retrieval");
            logger.info("  2. Build prompts for the LLM");
            logger.info("  3. Call OpenAI GPT to evaluate novelty");
            logger.info("  4. Parse and validate the response");
            logger.info("  5. Persist the evaluation results");
            logger.info("");
            
            int runId = evaluationService.evaluateAbstract(topic.id(), abstractToEvaluate.pmid());
            
            // Display results
            logger.info("\n--- Evaluation Results ---");
            EvaluationService.EvaluationSummary summary = evaluationService.getEvaluationSummary(runId);
            
            logger.info("Run ID: {}", summary.run().id());
            logger.info("Topic: {} (ID: {})", topic.name(), summary.run().topicId());
            logger.info("PMID: {}", summary.run().pmid());
            logger.info("LLM Model: {}", summary.run().llmModel());
            logger.info("Created: {}", summary.run().createdAt());
            logger.info("Parameters: {}", summary.run().paramsJson());
            logger.info("");
            
            EvaluationResultDao.EvaluationResult result = summary.result();
            logger.info("Novelty Label: {}", result.noveltyLabel());
            logger.info("Novelty Score: {}/10", result.noveltyScore());
            logger.info("");
            logger.info("Rationale:");
            logger.info("{}", result.rationale());
            logger.info("");
            logger.info("Used Chunk IDs: {}", result.usedChunkIdsJson());
            logger.info("Hybrid Scores: {}", result.hybridScoresJson());
            
            // Summary
            logger.info("\n--- Summary ---");
            String noveltyCategory = switch (result.noveltyLabel()) {
                case "novel" -> "✓ NOVEL - This abstract represents novel work";
                case "uncertain" -> "? UNCERTAIN - Novelty is unclear";
                case "not_novel" -> "✗ NOT NOVEL - This work appears incremental";
                default -> "UNKNOWN";
            };
            logger.info(noveltyCategory);
            logger.info("Score: {}/10", result.noveltyScore());
            
            logger.info("\n=== Phase 6 Demo Complete ===");
            logger.info("Evaluation successfully saved to database (run ID: {})", runId);
            
        } catch (Exception e) {
            logger.error("Demo failed", e);
            System.exit(1);
        }
    }
}

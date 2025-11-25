package ca.zoltar.core.service;

import java.util.List;
import java.util.Map;

/**
 * Result of a novelty evaluation from the LLM.
 * 
 * This record holds the structured output from the LLM evaluation,
 * including the novelty classification, score, rationale, and supporting evidence.
 */
public record NoveltyEvaluationResult(
        String noveltyLabel,           // "novel", "uncertain", or "not_novel"
        int noveltyScore,               // 0-10 scale
        String rationale,               // Free-text explanation
        List<ChunkEvidence> supportingEvidence  // Evidence from specific chunks
) {
    /**
     * Evidence from a specific chunk supporting the evaluation.
     */
    public record ChunkEvidence(
            int chunkId,
            String explanation
    ) {}
    
    /**
     * Validate that the novelty label is one of the expected values.
     */
    public boolean isValidNoveltyLabel() {
        return "novel".equals(noveltyLabel) || 
               "uncertain".equals(noveltyLabel) || 
               "not_novel".equals(noveltyLabel);
    }
    
    /**
     * Validate that the novelty score is in valid range.
     */
    public boolean isValidNoveltyScore() {
        return noveltyScore >= 0 && noveltyScore <= 10;
    }
    
    /**
     * Validate the entire result.
     */
    public boolean isValid() {
        return isValidNoveltyLabel() && isValidNoveltyScore() && rationale != null;
    }
}

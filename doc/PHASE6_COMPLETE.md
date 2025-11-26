# Phase 6 - LLM Evaluation Pipeline Implementation

## Summary

Phase 6 has been successfully implemented with a complete end-to-end LLM evaluation pipeline for assessing PubMed abstract novelty.

### Created Files

#### Database Layer (zoltar-db)

1. **EvaluationRunDao.java** - DAO for evaluation_run table
   - Stores metadata about each evaluation run
   - Fields: topic_id, pmid, llm_model, params_json, created_at
   - Methods: create, findById, findByTopicId, findByPmid, findByTopicAndPmid, countByTopicId, delete

2. **EvaluationResultDao.java** - DAO for evaluation_result table
   - Stores LLM evaluation results
   - Fields: run_id, novelty_label, novelty_score, rationale, used_chunk_ids_json, hybrid_scores_json
   - Methods: create, findById, findByRunId, findByNoveltyLabel, delete

#### Core Services (zoltar-core)

3. **OpenAIClient.java** (enhanced)
   - Added `generateChatCompletion()` method for GPT API calls
   - Supports JSON response format specification
   - Configurable chat model (default: gpt-4)
   - Timeout: 60 seconds for LLM calls

4. **NoveltyEvaluationResult.java** - Record for LLM evaluation output
   - `noveltyLabel`: "novel" | "uncertain" | "not_novel"
   - `noveltyScore`: 0-10 integer scale
   - `rationale`: Free-text explanation
   - `supportingEvidence`: List of ChunkEvidence with chunk IDs and explanations
   - Validation methods: isValidNoveltyLabel(), isValidNoveltyScore(), isValid()

5. **EvaluationService.java** - Main orchestration service
   - `evaluateAbstract()`: End-to-end evaluation pipeline
   - `buildSystemPrompt()`: Creates expert system prompt for LLM
   - `buildUserPrompt()`: Constructs context-rich user prompt
   - `parseEvaluationResponse()`: Parses and validates JSON response
   - `persistEvaluation()`: Saves evaluation run and results to database
   - `getEvaluationSummary()`: Retrieves complete evaluation with run and result data

6. **Phase6Demo.java** - Demonstration program
   - End-to-end novelty evaluation workflow
   - Finds topic with abstracts and indexed content
   - Runs evaluation with full retrieval → LLM → persistence pipeline
   - Displays formatted results

### Database Schema

The evaluation tables were already present in DatabaseManager:

```sql
CREATE TABLE evaluation_run (
    id          INTEGER PRIMARY KEY,
    topic_id    INTEGER NOT NULL,
    pmid        TEXT NOT NULL,
    created_at  TEXT NOT NULL,
    llm_model   TEXT NOT NULL,
    params_json TEXT NOT NULL,
    FOREIGN KEY (topic_id) REFERENCES topic(id)
);

CREATE TABLE evaluation_result (
    id                    INTEGER PRIMARY KEY,
    run_id                INTEGER NOT NULL,
    novelty_label         TEXT NOT NULL,
    novelty_score         INTEGER NOT NULL,
    rationale             TEXT,
    used_chunk_ids_json   TEXT,
    hybrid_scores_json    TEXT,
    FOREIGN KEY (run_id) REFERENCES evaluation_result(id)
);
```

## Evaluation Pipeline

### Workflow

1. **Retrieve Abstract**: Load PubMed abstract from database by PMID and topic
2. **Get Topic Context**: Load topic information for prompt context
3. **Hybrid Retrieval**: Use HybridRetrievalService to find relevant chunks
   - Combines semantic (embeddings) and lexical (BM25) search
   - Returns top-K context chunks with hybrid scores
4. **Build Prompts**:
   - System prompt: Sets LLM role as biomedical research expert
   - User prompt: Includes topic, abstract, and retrieved context chunks
5. **LLM Evaluation**: Call OpenAI GPT with JSON response format
6. **Parse Response**: Extract novelty_label, score, rationale, and evidence
7. **Validate**: Ensure all fields are present and values are valid
8. **Persist**: Save evaluation run metadata and results to database

### Prompt Structure

#### System Prompt
```
You are an expert research assistant in biomedical science.
You receive a research topic, a new PubMed abstract, and context from existing articles.
Your task is to judge whether the abstract describes a novel contribution.

Focus on:
- Novel methods, techniques, or experimental approaches
- New populations, species, or systems studied
- Unexpected outcomes or contradictory findings
- New applications or combinations of existing knowledge

Output as JSON with: novelty_label, novelty_score, rationale, supporting_evidence
```

#### User Prompt
```
TOPIC: <topic name and notes>

NEW ABSTRACT TO EVALUATE:
PMID: <pmid>
Title: <title>
Journal: <journal>
Publication Date: <date>

Abstract Text:
<abstract text>

CONTEXT FROM EXISTING LITERATURE:
(Retrieved N most relevant passages)

[Chunk 1 - ID: X, Hybrid Score: 0.XXX]
Source: <document title (year)>
Text: <chunk text>

[Chunk 2 - ID: Y, Hybrid Score: 0.YYY]
...

Based on this context, evaluate the novelty of the new abstract.
```

### Response Format

```json
{
  "novelty_label": "novel",
  "novelty_score": 8,
  "rationale": "This abstract presents a novel CRISPR-based approach...",
  "supporting_evidence": [
    {
      "chunk_id": 42,
      "explanation": "This chunk describes standard CRISPR methods, highlighting the novelty of the new approach"
    },
    {
      "chunk_id": 57,
      "explanation": "Previous work used different cell types, making this population novel"
    }
  ]
}
```

## Configuration

Add to `~/.zoltar-java/config.json`:

```json
{
  "openai": {
    "apiKey": "sk-your-api-key-here",
    "embeddingModel": "text-embedding-3-small",
    "chatModel": "gpt-4"
  }
}
```

## Running the Demo

```bash
mvn exec:java -pl zoltar-core -Dexec.mainClass="ca.zoltar.core.service.Phase6Demo"
```

**Prerequisites**:
- OpenAI API key configured
- Topic with indexed chunks (Phase 2-3)
- Topic with stored PubMed abstracts (Phase 4)

## API Usage

```java
EvaluationService service = new EvaluationService();

// Evaluate an abstract
int runId = service.evaluateAbstract(topicId, pmid);

// Get results
EvaluationService.EvaluationSummary summary = service.getEvaluationSummary(runId);

// Access details
String noveltyLabel = summary.result().noveltyLabel();
int noveltyScore = summary.result().noveltyScore();
String rationale = summary.result().rationale();
```

## Acceptance Criteria Status

✅ **Implemented**: Prompt builder for novelty evaluation
✅ **Implemented**: OpenAI chat client with JSON response format
✅ **Implemented**: EvaluationService orchestrating retrieval + evaluation + persistence
✅ **Implemented**: Robust error handling and JSON validation
✅ **Ready**: End-to-end evaluation of real PubMed abstracts
✅ **Implemented**: Parsing into NoveltyEvaluationResult
✅ **Implemented**: Persistence to evaluation_run and evaluation_result tables

## Testing Notes

The Phase6Demo requires:
1. An OpenAI API key (for both embeddings and chat completions)
2. Existing indexed content from Phase 2-3
3. Stored PubMed abstracts from Phase 4

The demo will:
- Find a topic with both abstracts and indexed chunks
- Run full evaluation pipeline
- Display novelty assessment with score and rationale
- Save results to database

## Next Steps

Phase 6 is complete. Ready to proceed to Phase 7 (JavaFX GUI Completion).

## Cost Considerations

Each evaluation includes:
- 1 embedding API call (for query abstract): ~$0.0001
- 1 chat completion API call (GPT-4): ~$0.01-0.03 depending on context length
- Total per evaluation: ~$0.01-0.03

Context size depends on:
- Abstract length: ~200-500 tokens
- Retrieved chunks: K_ctx * ~200 tokens/chunk (default 30 chunks = ~6000 tokens)
- System + user prompt structure: ~500 tokens
- Total typical input: ~7000-8000 tokens

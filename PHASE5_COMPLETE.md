# Phase 5 - Hybrid Retrieval Implementation

## Summary

Phase 5 has been successfully implemented with the following components:

### Created Files

1. **VectorSimilarity.java** - Vector similarity utilities
   - Cosine similarity calculation
   - Euclidean distance calculation
   - Vector normalization

2. **HybridRetrievalService.java** - Main hybrid retrieval service
   - Semantic retrieval using cosine similarity with embeddings
   - Lexical retrieval using Lucene BM25
   - Score normalization (min-max)
   - Configurable hybrid scoring: H = α * semantic + (1-α) * lexical
   - Tunable parameters:
     - `alpha` (semantic weight, default 0.6)
     - `kSemantic` (top semantic candidates, default 200)
     - `kLexical` (top lexical candidates, default 200)
     - `kContext` (final top chunks, default 30)

3. **Phase5Demo.java** - Demonstration program
   - Tests hybrid retrieval with different parameter configurations
   - Shows top-K results with hybrid, semantic, and lexical scores
   - Displays summary statistics

### Module Updates

- Updated `ca.zoltar.core/module-info.java` to require `org.apache.lucene.queryparser`

## Testing Requirements

**IMPORTANT**: To run the Phase5Demo, you need an OpenAI API key configured.

### Setup Instructions

1. Create or edit `~/.zoltar-java/config.json`:

```json
{
  "openai": {
    "apiKey": "sk-your-api-key-here",
    "embeddingModel": "text-embedding-3-small"
  }
}
```

2. Ensure you have:
   - A topic with indexed chunks (from Phase 2-3)
   - Embeddings generated for those chunks (from Phase 3)
   - Lucene index built (from Phase 3)
   - Optionally: PubMed abstracts stored (from Phase 4)

3. Run the demo:

```bash
mvn exec:java -pl zoltar-core -Dexec.mainClass="ca.zoltar.core.service.Phase5Demo"
```

## Implementation Details

### Hybrid Retrieval Algorithm

1. **Query Embedding Generation**: Generate embedding vector for query text using OpenAI API
2. **Semantic Retrieval**: Calculate cosine similarity between query embedding and all chunk embeddings, take top K_sem
3. **Lexical Retrieval**: Use Lucene BM25 to find top K_lex chunks
4. **Merge**: Combine unique chunks from both methods
5. **Normalize**: Min-max normalization of both score types to [0,1]
6. **Hybrid Score**: H = α * norm_semantic + (1-α) * norm_lexical
7. **Rank and Return**: Sort by hybrid score, return top K_ctx chunks

### API

```java
HybridRetrievalService service = new HybridRetrievalService();

// Configure parameters
service.setAlpha(0.6);        // 60% semantic, 40% lexical
service.setKSemantic(200);    // Top 200 semantic candidates
service.setKLexical(200);     // Top 200 lexical candidates  
service.setKContext(30);      // Return top 30 final results

// Retrieve relevant chunks
List<RetrievalResult> results = service.retrieve(topicId, queryText);

// Each result contains:
// - chunkId, documentId, topicId, chunkIndex
// - text content
// - hybridScore, semanticScore, lexicalScore
```

## Acceptance Criteria Status

✅ **Implemented**: HybridRetrievalService in ca.zoltar.core
✅ **Implemented**: Combines semantic (cosine similarity) and lexical (BM25) scores
✅ **Implemented**: Tunable parameters (α, K_sem, K_lex, K_ctx)
✅ **Implemented**: Returns ranked list of context chunks with hybrid scores
⚠️ **Requires API Key**: Testing requires OpenAI API key configuration

## Next Steps

Phase 5 is complete. Ready to proceed to Phase 6 (LLM Evaluation Pipeline).

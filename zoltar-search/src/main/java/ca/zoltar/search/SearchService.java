package ca.zoltar.search;

import org.apache.lucene.queryparser.classic.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Main service for managing Lucene indexing and searching.
 */
public class SearchService {
    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);
    
    private final LuceneIndexer indexer;
    private final LuceneSearcher searcher;

    public SearchService() {
        this.indexer = new LuceneIndexer();
        this.searcher = new LuceneSearcher();
        logger.info("SearchService initialized.");
    }

    /**
     * Index a single chunk.
     */
    public void indexChunk(LuceneIndexer.ChunkDocument chunk) throws IOException {
        indexer.indexChunk(chunk);
    }

    /**
     * Batch index multiple chunks.
     */
    public void batchIndexChunks(List<LuceneIndexer.ChunkDocument> chunks, int topicId) throws IOException {
        indexer.batchIndexChunks(chunks, topicId);
    }

    /**
     * Rebuild the entire index for a topic.
     */
    public void rebuildIndex(List<LuceneIndexer.ChunkDocument> chunks, int topicId) throws IOException {
        indexer.rebuildIndex(chunks, topicId);
    }

    /**
     * Delete a chunk from the index.
     */
    public void deleteChunk(int chunkId, int topicId) throws IOException {
        indexer.deleteChunk(chunkId, topicId);
    }

    /**
     * Delete the entire index for a topic.
     */
    public void deleteTopicIndex(int topicId) throws IOException {
        indexer.deleteTopicIndex(topicId);
    }

    /**
     * Search for chunks matching a query.
     */
    public List<LuceneSearcher.SearchHit> search(int topicId, String queryText, int maxResults) 
            throws IOException, ParseException {
        return searcher.search(topicId, queryText, maxResults);
    }

    /**
     * Get the number of documents in the index for a topic.
     */
    public int getIndexSize(int topicId) throws IOException {
        return searcher.getIndexSize(topicId);
    }

    /**
     * Check if an index exists for a topic.
     */
    public boolean indexExists(int topicId) {
        return searcher.indexExists(topicId);
    }
}

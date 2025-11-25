package ca.zoltar.search;

import ca.zoltar.util.ConfigManager;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for searching indexed chunks using Lucene with BM25.
 */
public class LuceneSearcher {
    private static final Logger logger = LoggerFactory.getLogger(LuceneSearcher.class);
    
    private final Path indexBaseDir;
    private final Analyzer analyzer;

    public LuceneSearcher() {
        ConfigManager config = ConfigManager.getInstance();
        this.indexBaseDir = config.getAppDir().resolve("indexes");
        this.analyzer = new StandardAnalyzer();
    }

    /**
     * Get the index directory for a specific topic.
     */
    private Path getTopicIndexPath(int topicId) {
        return indexBaseDir.resolve(String.valueOf(topicId));
    }

    /**
     * Search for chunks matching a query.
     * 
     * @param topicId The topic ID to search within
     * @param queryText The search query text
     * @param maxResults Maximum number of results to return
     * @return List of search hits with chunk IDs and BM25 scores
     * @throws IOException If search fails
     * @throws ParseException If query parsing fails
     */
    public List<SearchHit> search(int topicId, String queryText, int maxResults) throws IOException, ParseException {
        Path indexPath = getTopicIndexPath(topicId);
        
        if (!Files.exists(indexPath)) {
            logger.warn("Index does not exist for topic {}", topicId);
            return List.of();
        }
        
        try (Directory directory = FSDirectory.open(indexPath);
             DirectoryReader reader = DirectoryReader.open(directory)) {
            
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new BM25Similarity());
            
            // Parse query
            QueryParser parser = new QueryParser(LuceneIndexer.FIELD_CONTENT, analyzer);
            Query query = parser.parse(QueryParser.escape(queryText));
            
            // Execute search
            TopDocs topDocs = searcher.search(query, maxResults);
            
            // Convert to SearchHit objects
            List<SearchHit> hits = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                
                int chunkId = Integer.parseInt(doc.get(LuceneIndexer.FIELD_CHUNK_ID));
                int documentId = doc.getField(LuceneIndexer.FIELD_DOCUMENT_ID).numericValue().intValue();
                int chunkIndex = doc.getField(LuceneIndexer.FIELD_CHUNK_INDEX).numericValue().intValue();
                float score = scoreDoc.score;
                
                hits.add(new SearchHit(chunkId, documentId, topicId, chunkIndex, score));
            }
            
            logger.info("Found {} hits for query '{}' in topic {}", hits.size(), queryText, topicId);
            return hits;
            
        } catch (IOException e) {
            logger.error("Search failed for topic {}", topicId, e);
            throw e;
        }
    }

    /**
     * Get the number of documents in the index for a topic.
     * 
     * @param topicId The topic ID
     * @return Number of indexed documents, or 0 if index doesn't exist
     * @throws IOException If operation fails
     */
    public int getIndexSize(int topicId) throws IOException {
        Path indexPath = getTopicIndexPath(topicId);
        
        if (!Files.exists(indexPath)) {
            return 0;
        }
        
        try (Directory directory = FSDirectory.open(indexPath);
             DirectoryReader reader = DirectoryReader.open(directory)) {
            return reader.numDocs();
        } catch (IOException e) {
            logger.error("Failed to get index size for topic {}", topicId, e);
            throw e;
        }
    }

    /**
     * Check if an index exists for a topic.
     * 
     * @param topicId The topic ID
     * @return true if index exists and is readable
     */
    public boolean indexExists(int topicId) {
        Path indexPath = getTopicIndexPath(topicId);
        
        if (!Files.exists(indexPath)) {
            return false;
        }
        
        try (Directory directory = FSDirectory.open(indexPath);
             DirectoryReader reader = DirectoryReader.open(directory)) {
            return reader.numDocs() > 0;
        } catch (IOException e) {
            logger.debug("Index exists but is not readable for topic {}", topicId);
            return false;
        }
    }

    /**
     * Record representing a search hit.
     */
    public record SearchHit(
            int chunkId,
            int documentId,
            int topicId,
            int chunkIndex,
            float bm25Score
    ) {}
}

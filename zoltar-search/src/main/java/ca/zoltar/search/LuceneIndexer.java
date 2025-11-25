package ca.zoltar.search;

import ca.zoltar.util.ConfigManager;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Service for indexing chunks into Lucene with BM25 similarity.
 */
public class LuceneIndexer {
    private static final Logger logger = LoggerFactory.getLogger(LuceneIndexer.class);
    
    // Field names
    public static final String FIELD_CHUNK_ID = "chunk_id";
    public static final String FIELD_DOCUMENT_ID = "document_id";
    public static final String FIELD_TOPIC_ID = "topic_id";
    public static final String FIELD_CONTENT = "content";
    public static final String FIELD_CHUNK_INDEX = "chunk_index";

    private final Path indexBaseDir;
    private final Analyzer analyzer;

    public LuceneIndexer() {
        ConfigManager config = ConfigManager.getInstance();
        this.indexBaseDir = config.getAppDir().resolve("indexes");
        this.analyzer = new StandardAnalyzer();
        
        try {
            Files.createDirectories(indexBaseDir);
            logger.info("Lucene index base directory: {}", indexBaseDir);
        } catch (IOException e) {
            logger.error("Failed to create index base directory", e);
            throw new RuntimeException("Failed to initialize Lucene indexer", e);
        }
    }

    /**
     * Get the index directory for a specific topic.
     */
    private Path getTopicIndexPath(int topicId) {
        return indexBaseDir.resolve(String.valueOf(topicId));
    }

    /**
     * Index a single chunk.
     * 
     * @param chunk The chunk to index
     * @throws IOException If indexing fails
     */
    public void indexChunk(ChunkDocument chunk) throws IOException {
        Path indexPath = getTopicIndexPath(chunk.topicId());
        Files.createDirectories(indexPath);
        
        try (Directory directory = FSDirectory.open(indexPath)) {
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setSimilarity(new BM25Similarity());
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            
            try (IndexWriter writer = new IndexWriter(directory, config)) {
                Document doc = createDocument(chunk);
                
                // Update or add document (based on chunk_id)
                Term idTerm = new Term(FIELD_CHUNK_ID, String.valueOf(chunk.chunkId()));
                writer.updateDocument(idTerm, doc);
                
                writer.commit();
                logger.debug("Indexed chunk {} for topic {}", chunk.chunkId(), chunk.topicId());
            }
        }
    }

    /**
     * Batch index multiple chunks.
     * 
     * @param chunks List of chunks to index
     * @param topicId The topic ID (all chunks must belong to this topic)
     * @throws IOException If indexing fails
     */
    public void batchIndexChunks(List<ChunkDocument> chunks, int topicId) throws IOException {
        if (chunks.isEmpty()) {
            logger.debug("No chunks to index for topic {}", topicId);
            return;
        }
        
        Path indexPath = getTopicIndexPath(topicId);
        Files.createDirectories(indexPath);
        
        try (Directory directory = FSDirectory.open(indexPath)) {
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setSimilarity(new BM25Similarity());
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            
            try (IndexWriter writer = new IndexWriter(directory, config)) {
                for (ChunkDocument chunk : chunks) {
                    if (chunk.topicId() != topicId) {
                        logger.warn("Chunk {} belongs to topic {}, expected topic {}", 
                                chunk.chunkId(), chunk.topicId(), topicId);
                        continue;
                    }
                    
                    Document doc = createDocument(chunk);
                    Term idTerm = new Term(FIELD_CHUNK_ID, String.valueOf(chunk.chunkId()));
                    writer.updateDocument(idTerm, doc);
                }
                
                writer.commit();
                logger.info("Batch indexed {} chunks for topic {}", chunks.size(), topicId);
            }
        }
    }

    /**
     * Rebuild the entire index for a topic.
     * 
     * @param chunks All chunks for the topic
     * @param topicId The topic ID
     * @throws IOException If indexing fails
     */
    public void rebuildIndex(List<ChunkDocument> chunks, int topicId) throws IOException {
        Path indexPath = getTopicIndexPath(topicId);
        
        // Delete existing index
        if (Files.exists(indexPath)) {
            deleteIndexDirectory(indexPath);
            logger.info("Deleted existing index for topic {}", topicId);
        }
        
        Files.createDirectories(indexPath);
        
        try (Directory directory = FSDirectory.open(indexPath)) {
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setSimilarity(new BM25Similarity());
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            
            try (IndexWriter writer = new IndexWriter(directory, config)) {
                for (ChunkDocument chunk : chunks) {
                    Document doc = createDocument(chunk);
                    writer.addDocument(doc);
                }
                
                writer.commit();
                logger.info("Rebuilt index with {} chunks for topic {}", chunks.size(), topicId);
            }
        }
    }

    /**
     * Delete a chunk from the index.
     * 
     * @param chunkId The chunk ID to delete
     * @param topicId The topic ID
     * @throws IOException If deletion fails
     */
    public void deleteChunk(int chunkId, int topicId) throws IOException {
        Path indexPath = getTopicIndexPath(topicId);
        
        if (!Files.exists(indexPath)) {
            logger.debug("Index does not exist for topic {}", topicId);
            return;
        }
        
        try (Directory directory = FSDirectory.open(indexPath)) {
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setSimilarity(new BM25Similarity());
            
            try (IndexWriter writer = new IndexWriter(directory, config)) {
                Term idTerm = new Term(FIELD_CHUNK_ID, String.valueOf(chunkId));
                writer.deleteDocuments(idTerm);
                writer.commit();
                logger.debug("Deleted chunk {} from index for topic {}", chunkId, topicId);
            }
        }
    }

    /**
     * Delete the entire index for a topic.
     * 
     * @param topicId The topic ID
     * @throws IOException If deletion fails
     */
    public void deleteTopicIndex(int topicId) throws IOException {
        Path indexPath = getTopicIndexPath(topicId);
        
        if (Files.exists(indexPath)) {
            deleteIndexDirectory(indexPath);
            logger.info("Deleted index for topic {}", topicId);
        }
    }

    /**
     * Create a Lucene document from a chunk.
     */
    private Document createDocument(ChunkDocument chunk) {
        Document doc = new Document();
        
        // Stored fields (not analyzed, for retrieval)
        doc.add(new StoredField(FIELD_CHUNK_ID, chunk.chunkId()));
        doc.add(new StoredField(FIELD_DOCUMENT_ID, chunk.documentId()));
        doc.add(new StoredField(FIELD_TOPIC_ID, chunk.topicId()));
        doc.add(new StoredField(FIELD_CHUNK_INDEX, chunk.chunkIndex()));
        
        // Indexed field (analyzed, for search)
        doc.add(new TextField(FIELD_CONTENT, chunk.content(), Field.Store.NO));
        
        // Also store chunk_id as a string field for exact matching in updates/deletes
        doc.add(new StringField(FIELD_CHUNK_ID, String.valueOf(chunk.chunkId()), Field.Store.YES));
        
        return doc;
    }

    /**
     * Recursively delete a directory and its contents.
     */
    private void deleteIndexDirectory(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                stream.forEach(child -> {
                    try {
                        deleteIndexDirectory(child);
                    } catch (IOException e) {
                        logger.error("Failed to delete {}", child, e);
                    }
                });
            }
        }
        
        Files.deleteIfExists(path);
    }

    /**
     * Record representing a chunk document for indexing.
     */
    public record ChunkDocument(
            int chunkId,
            int documentId,
            int topicId,
            int chunkIndex,
            String content
    ) {}
}

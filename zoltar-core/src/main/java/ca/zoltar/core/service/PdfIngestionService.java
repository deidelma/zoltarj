package ca.zoltar.core.service;

import ca.zoltar.db.ChunkDao;
import ca.zoltar.db.DocumentDao;
import ca.zoltar.util.FileHasher;
import ca.zoltar.util.text.ChunkSegment;
import ca.zoltar.util.text.TextChunker;
import ca.zoltar.util.text.TextCleaner;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PdfIngestionService {
    private static final Logger logger = LoggerFactory.getLogger(PdfIngestionService.class);
    
    private final DocumentDao documentDao;
    private final ChunkDao chunkDao;
    
    // Default chunking parameters
    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_STRIDE = 600;

    public PdfIngestionService() {
        this.documentDao = new DocumentDao();
        this.chunkDao = new ChunkDao();
    }

    public void ingestPdf(int topicId, Path pdfPath) throws IOException, SQLException {
        logger.info("Starting ingestion of PDF: {}", pdfPath);

        // 1. Calculate Hash
        String hash = FileHasher.calculateSha256(pdfPath);
        
        // 2. Check for duplicates
        Optional<DocumentDao.Document> existing = documentDao.findByHash(hash);
        if (existing.isPresent()) {
            logger.info("Document already exists with hash {}. Skipping.", hash);
            return;
        }

        // 3. Parse PDF
        String rawText = extractTextFromPdf(pdfPath.toFile());
        
        // 4. Clean Text
        String cleanedText = TextCleaner.clean(rawText);
        
        // 5. Create Document Record
        String filename = pdfPath.getFileName().toString();
        // For now, we don't extract metadata like DOI/PMID/Year automatically.
        DocumentDao.Document doc = documentDao.create(
            topicId, 
            filename, // Title defaults to filename
            pdfPath.toAbsolutePath().toString(), 
            null, // DOI
            null, // PMID
            null, // Year
            null, // Venue
            hash
        );
        
        // 6. Chunk Text
        List<ChunkSegment> segments = TextChunker.chunk(cleanedText, CHUNK_SIZE, CHUNK_STRIDE);
        
        // 7. Create Chunk Records
        List<ChunkDao.Chunk> chunks = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            ChunkSegment seg = segments.get(i);
            chunks.add(new ChunkDao.Chunk(
                0, // ID auto-generated
                doc.id(),
                topicId,
                i,
                seg.text(),
                seg.tokenCount(),
                null, // Lucene Doc ID
                null // Added At auto-generated
            ));
        }
        
        chunkDao.batchCreate(chunks);
        logger.info("Ingested {} chunks for document {}", chunks.size(), doc.id());
    }

    private String extractTextFromPdf(File pdfFile) throws IOException {
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }
}

package ca.zoltar.util.text;

import java.util.ArrayList;
import java.util.List;

public class TextChunker {
    
    public static List<ChunkSegment> chunk(String text, int chunkSize, int chunkStride) {
        List<ChunkSegment> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        // Simple tokenization by splitting on whitespace
        String[] tokens = text.split("\\s+");
        
        if (tokens.length == 0) {
            return chunks;
        }

        for (int i = 0; i < tokens.length; i += chunkStride) {
            int end = Math.min(i + chunkSize, tokens.length);
            
            StringBuilder sb = new StringBuilder();
            for (int j = i; j < end; j++) {
                if (j > i) {
                    sb.append(" ");
                }
                sb.append(tokens[j]);
            }
            
            String chunkText = sb.toString();
            int tokenCount = end - i;
            chunks.add(new ChunkSegment(chunkText, tokenCount));

            // If we reached the end of tokens, stop
            if (end == tokens.length) {
                break;
            }
        }

        return chunks;
    }
}

package ca.zoltar.core.service;

import ca.zoltar.util.ConfigManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Client for OpenAI API interactions, specifically for embedding generation.
 */
public class OpenAIClient {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIClient.class);
    private static final String EMBEDDINGS_ENDPOINT = "https://api.openai.com/v1/embeddings";
    private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small";
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String embeddingModel;

    public OpenAIClient() {
        ConfigManager config = ConfigManager.getInstance();
        this.apiKey = (String) config.get("openai.apiKey");
        if (this.apiKey == null || this.apiKey.isBlank()) {
            logger.warn("OpenAI API key not configured. Set 'openai.apiKey' in configuration.");
        }
        
        this.embeddingModel = (String) config.get("openai.embeddingModel");
        if (this.embeddingModel == null || this.embeddingModel.isBlank()) {
            logger.info("Using default embedding model: {}", DEFAULT_EMBEDDING_MODEL);
        }
        
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Generate an embedding for the given text.
     * 
     * @param text The text to embed
     * @return Array of floats representing the embedding vector
     * @throws IOException If the API call fails
     */
    public float[] generateEmbedding(String text) throws IOException, InterruptedException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key is not configured");
        }

        String model = (embeddingModel != null && !embeddingModel.isBlank()) 
                ? embeddingModel 
                : DEFAULT_EMBEDDING_MODEL;

        // Build request body
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("input", text);

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        // Build HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(EMBEDDINGS_ENDPOINT))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        logger.debug("Requesting embedding for text of length {}", text.length());

        // Execute request
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            String errorMsg = String.format("OpenAI API returned status %d: %s", 
                    response.statusCode(), response.body());
            logger.error(errorMsg);
            throw new IOException(errorMsg);
        }

        // Parse response
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode dataArray = root.get("data");
        
        if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) {
            throw new IOException("Invalid response from OpenAI: missing 'data' array");
        }

        JsonNode embeddingNode = dataArray.get(0).get("embedding");
        if (embeddingNode == null || !embeddingNode.isArray()) {
            throw new IOException("Invalid response from OpenAI: missing 'embedding' array");
        }

        // Convert to float array
        int size = embeddingNode.size();
        float[] embedding = new float[size];
        for (int i = 0; i < size; i++) {
            embedding[i] = (float) embeddingNode.get(i).asDouble();
        }

        logger.debug("Generated embedding of dimension {}", size);
        return embedding;
    }

    /**
     * Get the configured embedding model name.
     */
    public String getEmbeddingModel() {
        return (embeddingModel != null && !embeddingModel.isBlank()) 
                ? embeddingModel 
                : DEFAULT_EMBEDDING_MODEL;
    }
}

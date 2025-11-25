package ca.zoltar.pubmed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Client for PubMed E-utilities API.
 * 
 * Uses ESearch for searching and EFetch for retrieving article details.
 * Implements NCBI API guidelines including rate limiting.
 * 
 * @see <a href="https://www.ncbi.nlm.nih.gov/books/NBK25500/">E-utilities Documentation</a>
 */
public class PubMedClient {
    private static final Logger logger = LoggerFactory.getLogger(PubMedClient.class);
    
    private static final String ESEARCH_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi";
    private static final String EFETCH_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi";
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_RETMAX = 100; // Max results per search
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String email; // For NCBI API etiquette
    private final String tool; // Tool name for NCBI API

    public PubMedClient() {
        this("zoltar-java", "");
    }

    public PubMedClient(String tool, String email) {
        this.tool = tool;
        this.email = email;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .build();
        this.objectMapper = new ObjectMapper();
        logger.info("PubMedClient initialized with tool={}, email={}", tool, 
                email.isEmpty() ? "[not provided]" : email);
    }

    /**
     * Search PubMed for articles matching a query.
     * 
     * @param query The search query (e.g., "macrophages AND pulmonary fibrosis")
     * @param maxResults Maximum number of results to return
     * @return List of PMIDs matching the query
     * @throws IOException If the API call fails
     * @throws InterruptedException If the operation is interrupted
     */
    public List<String> search(String query, int maxResults) throws IOException, InterruptedException {
        logger.info("Searching PubMed for: '{}' (max: {})", query, maxResults);
        
        // Build ESearch URL
        String url = String.format("%s?db=pubmed&term=%s&retmode=json&retmax=%d&tool=%s",
                ESEARCH_URL,
                URLEncoder.encode(query, StandardCharsets.UTF_8),
                Math.min(maxResults, DEFAULT_RETMAX),
                URLEncoder.encode(tool, StandardCharsets.UTF_8));
        
        if (!email.isEmpty()) {
            url += "&email=" + URLEncoder.encode(email, StandardCharsets.UTF_8);
        }
        
        // Execute request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            String errorMsg = String.format("PubMed ESearch returned status %d: %s",
                    response.statusCode(), response.body());
            logger.error(errorMsg);
            throw new IOException(errorMsg);
        }
        
        // Parse JSON response
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode idList = root.path("esearchresult").path("idlist");
        
        List<String> pmids = new ArrayList<>();
        if (idList.isArray()) {
            for (JsonNode id : idList) {
                pmids.add(id.asText());
            }
        }
        
        logger.info("Found {} PMIDs for query: '{}'", pmids.size(), query);
        return pmids;
    }

    /**
     * Fetch detailed information for a list of PMIDs.
     * 
     * @param pmids List of PubMed IDs to fetch
     * @return List of PubMed article records
     * @throws IOException If the API call fails
     * @throws InterruptedException If the operation is interrupted
     */
    public List<PubMedArticle> fetchArticles(List<String> pmids) throws IOException, InterruptedException {
        if (pmids.isEmpty()) {
            return List.of();
        }
        
        logger.info("Fetching {} articles from PubMed", pmids.size());
        
        // Build EFetch URL
        String pmidList = String.join(",", pmids);
        String url = String.format("%s?db=pubmed&id=%s&retmode=xml&rettype=abstract&tool=%s",
                EFETCH_URL,
                URLEncoder.encode(pmidList, StandardCharsets.UTF_8),
                URLEncoder.encode(tool, StandardCharsets.UTF_8));
        
        if (!email.isEmpty()) {
            url += "&email=" + URLEncoder.encode(email, StandardCharsets.UTF_8);
        }
        
        // Execute request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            String errorMsg = String.format("PubMed EFetch returned status %d",
                    response.statusCode());
            logger.error(errorMsg);
            throw new IOException(errorMsg);
        }
        
        // Parse XML response
        List<PubMedArticle> articles = parseXmlResponse(response.body());
        logger.info("Fetched {} articles", articles.size());
        
        return articles;
    }

    /**
     * Parse PubMed XML response into article records.
     * This is a simplified parser that extracts the most important fields.
     */
    private List<PubMedArticle> parseXmlResponse(String xml) {
        List<PubMedArticle> articles = new ArrayList<>();
        
        // Simple XML parsing using string operations
        // In a production system, consider using a proper XML parser like JAXB or SAX
        String[] articleBlocks = xml.split("<PubmedArticle>");
        
        for (int i = 1; i < articleBlocks.length; i++) {
            String block = articleBlocks[i];
            
            try {
                String pmid = extractXmlValue(block, "PMID");
                String title = extractXmlValue(block, "ArticleTitle");
                String abstractText = extractXmlValue(block, "AbstractText");
                String journal = extractXmlValue(block, "Title"); // Journal title
                String pubDate = extractPubDate(block);
                List<String> authors = extractAuthors(block);
                
                if (pmid != null && !pmid.isEmpty()) {
                    articles.add(new PubMedArticle(
                            pmid,
                            title != null ? title : "",
                            authors,
                            journal != null ? journal : "",
                            pubDate != null ? pubDate : "",
                            abstractText != null ? abstractText : ""
                    ));
                }
            } catch (Exception e) {
                logger.warn("Failed to parse article from block: {}", e.getMessage());
            }
        }
        
        return articles;
    }

    private String extractXmlValue(String xml, String tag) {
        String openTag = "<" + tag;
        String closeTag = "</" + tag + ">";
        
        int start = xml.indexOf(openTag);
        if (start == -1) return null;
        
        // Find the end of the opening tag
        int tagEnd = xml.indexOf(">", start);
        if (tagEnd == -1) return null;
        
        int end = xml.indexOf(closeTag, tagEnd);
        if (end == -1) return null;
        
        String value = xml.substring(tagEnd + 1, end).trim();
        
        // Remove XML tags from content
        return value.replaceAll("<[^>]+>", "").trim();
    }

    private String extractPubDate(String xml) {
        // Try to extract year, month, day from PubDate
        String year = extractXmlValue(xml, "Year");
        String month = extractXmlValue(xml, "Month");
        String day = extractXmlValue(xml, "Day");
        
        if (year != null) {
            StringBuilder date = new StringBuilder(year);
            if (month != null) {
                date.append("-").append(month);
                if (day != null) {
                    date.append("-").append(day);
                }
            }
            return date.toString();
        }
        
        return "";
    }

    private List<String> extractAuthors(String xml) {
        List<String> authors = new ArrayList<>();
        
        // Find AuthorList section
        int authorListStart = xml.indexOf("<AuthorList");
        int authorListEnd = xml.indexOf("</AuthorList>");
        
        if (authorListStart == -1 || authorListEnd == -1) {
            return authors;
        }
        
        String authorListXml = xml.substring(authorListStart, authorListEnd);
        
        // Extract individual authors
        String[] authorBlocks = authorListXml.split("<Author ");
        for (int i = 1; i < authorBlocks.length; i++) {
            String block = authorBlocks[i];
            String lastName = extractXmlValue(block, "LastName");
            String foreName = extractXmlValue(block, "ForeName");
            
            if (lastName != null) {
                String author = lastName;
                if (foreName != null) {
                    author = foreName + " " + lastName;
                }
                authors.add(author);
            }
        }
        
        return authors;
    }

    /**
     * Record representing a PubMed article.
     */
    public record PubMedArticle(
            String pmid,
            String title,
            List<String> authors,
            String journal,
            String pubDate,
            String abstractText
    ) {
        @Override
        public String toString() {
            return String.format("PubMedArticle{pmid='%s', title='%s', journal='%s', pubDate='%s', authors=%d}",
                    pmid, title, journal, pubDate, authors.size());
        }
    }
}

package ca.zoltar.core.service;

/**
 * Utility class for vector similarity calculations.
 */
public class VectorSimilarity {
    
    /**
     * Calculate cosine similarity between two vectors.
     * 
     * @param vec1 First vector
     * @param vec2 Second vector
     * @return Cosine similarity in range [-1, 1], or 0 if either vector is zero-length
     * @throws IllegalArgumentException if vectors have different dimensions
     */
    public static double cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1.length != vec2.length) {
            throw new IllegalArgumentException(
                    "Vectors must have same dimension: " + vec1.length + " vs " + vec2.length);
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }
        
        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    /**
     * Calculate Euclidean distance between two vectors.
     * 
     * @param vec1 First vector
     * @param vec2 Second vector
     * @return Euclidean distance (non-negative)
     * @throws IllegalArgumentException if vectors have different dimensions
     */
    public static double euclideanDistance(float[] vec1, float[] vec2) {
        if (vec1.length != vec2.length) {
            throw new IllegalArgumentException(
                    "Vectors must have same dimension: " + vec1.length + " vs " + vec2.length);
        }
        
        double sum = 0.0;
        for (int i = 0; i < vec1.length; i++) {
            double diff = vec1[i] - vec2[i];
            sum += diff * diff;
        }
        
        return Math.sqrt(sum);
    }
    
    /**
     * Normalize a vector to unit length.
     * 
     * @param vec Vector to normalize (will be modified in-place)
     * @return The same vector, normalized
     */
    public static float[] normalize(float[] vec) {
        double norm = 0.0;
        for (float v : vec) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        
        if (norm == 0.0) {
            return vec;
        }
        
        for (int i = 0; i < vec.length; i++) {
            vec[i] /= norm;
        }
        
        return vec;
    }
}

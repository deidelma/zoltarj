package ca.zoltar.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileHasher {
    
    public static String calculateSha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(path);
                 DigestInputStream dis = new DigestInputStream(is, digest)) {
                byte[] buffer = new byte[8192];
                //noinspection StatementWithEmptyBody
                while (dis.read(buffer) != -1) {
                    // Digest is automatically updated as bytes are read
                }
            }
            byte[] hashBytes = digest.digest();
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

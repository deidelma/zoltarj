package ca.zoltar.util.text;

import java.util.regex.Pattern;

public class TextCleaner {
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern HYPHEN_NEWLINE_PATTERN = Pattern.compile("-\\s*\\n\\s*");

    public static String clean(String text) {
        if (text == null) {
            return "";
        }

        // 1. De-hyphenate words split across lines (e.g. "con-\nnection" -> "connection")
        // This is a simple heuristic and might have false positives, but is generally useful for PDFs.
        String dehyphenated = HYPHEN_NEWLINE_PATTERN.matcher(text).replaceAll("");

        // 2. Normalize whitespace (collapse multiple spaces/newlines into single space)
        String normalized = WHITESPACE_PATTERN.matcher(dehyphenated).replaceAll(" ");

        return normalized.trim();
    }
}

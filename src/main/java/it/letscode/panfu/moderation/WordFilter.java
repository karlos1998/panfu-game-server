package it.letscode.panfu.moderation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public final class WordFilter {

    private final List<String> blockedWords;

    public WordFilter() {
        this.blockedWords = loadWords();
    }

    public boolean containsBlockedWord(String text) {
        String normalized = normalize(text);
        return blockedWords.stream().anyMatch(word -> !word.isBlank() && normalized.contains(word));
    }

    private List<String> loadWords() {
        InputStream stream = WordFilter.class.getResourceAsStream("/word-filter.txt");
        if (stream == null) {
            throw new IllegalStateException("Missing word-filter.txt");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::strip)
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .map(this::normalize)
                    .distinct()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load word filter", exception);
        }
    }

    private String normalize(String value) {
        String lower = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        return lower
                .replace('0', 'o').replace('1', 'l').replace('2', 'z').replace('3', 'e')
                .replace('4', 'a').replace('5', 's').replace('6', 'b').replace('7', 't')
                .replace('8', 'b').replace('9', 'p')
                .replaceAll("[^\\p{L}\\p{N}]+", "");
    }
}

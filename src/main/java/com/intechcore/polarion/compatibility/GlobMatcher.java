package com.intechcore.polarion.compatibility;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Matches jar names against exclusion globs such as {@code fop-core-*.jar}.
 *
 * <p>{@code *} matches within one path segment, {@code **} crosses segments, {@code ?} matches
 * one character. A pattern without a slash is matched against the file name alone, so
 * {@code fop-core-*.jar} excludes the jar wherever it is nested.</p>
 */
public final class GlobMatcher {

    private final List<Pattern> patterns = new ArrayList<>();
    private final List<Boolean> fileNameOnly = new ArrayList<>();

    /**
     * Compiles the given globs. An empty list matches nothing.
     */
    public GlobMatcher(@NotNull List<String> globs) {
        for (String glob : globs) {
            String trimmed = glob.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            patterns.add(Pattern.compile(toRegex(trimmed)));
            fileNameOnly.add(trimmed.indexOf('/') < 0);
        }
    }

    static @NotNull String toRegex(@NotNull String glob) {
        StringBuilder regex = new StringBuilder();
        int i = 0;
        while (i < glob.length()) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> {
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        regex.append(".*");
                        i++;
                    } else {
                        regex.append("[^/]*");
                    }
                }
                case '?' -> regex.append("[^/]");
                default -> regex.append(Pattern.quote(String.valueOf(c)));
            }
            i++;
        }
        return regex.toString();
    }

    /**
     * Returns true when the path or its file name matches one of the globs.
     */
    public boolean matches(@NotNull String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        for (int i = 0; i < patterns.size(); i++) {
            String subject = Boolean.TRUE.equals(fileNameOnly.get(i)) ? fileName : path;
            if (patterns.get(i).matcher(subject).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true when no glob was configured.
     */
    public boolean isEmpty() {
        return patterns.isEmpty();
    }
}

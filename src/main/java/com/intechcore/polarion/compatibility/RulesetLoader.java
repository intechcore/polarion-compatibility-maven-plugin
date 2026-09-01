package com.intechcore.polarion.compatibility;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads rulesets, either the ones bundled with the plugin or a file supplied by the project.
 *
 * <p>Line format:</p>
 * <pre>
 * javax.servlet -&gt; jakarta.servlet   forbidden, with a replacement
 * javax.servlet                      forbidden, no replacement
 * !javax.transaction.xa              allowed, overrides a forbidden parent
 * # comment
 * </pre>
 */
public final class RulesetLoader {

    private static final String ARROW = "->";

    private RulesetLoader() {
    }

    /**
     * Loads a ruleset bundled with the plugin, for example {@code jakarta}.
     *
     * @param name    the ruleset name
     * @param builder collects the rules the ruleset declares
     * @throws IOException when no ruleset with that name exists
     */
    public static void loadBuiltin(@NotNull String name, @NotNull PackageRules.Builder builder) throws IOException {
        String resource = "ruleset-" + name + ".txt";
        try (InputStream in = RulesetLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Unknown ruleset '" + name + "'. Bundled rulesets: jakarta, jakarta-extended");
            }
            read(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)), builder, resource);
        }
    }

    /**
     * Loads a ruleset from a file in the project.
     *
     * @param file    the ruleset file
     * @param builder collects the rules the file declares
     * @throws IOException when the file cannot be read
     */
    public static void loadFile(@NotNull Path file, @NotNull PackageRules.Builder builder) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            read(reader, builder, file.toString());
        }
    }

    /**
     * Adds configuration entries which use the same line format as a ruleset file.
     *
     * @param lines   the entries
     * @param builder collects the rules the entries declare
     * @param origin  names the source of the entries in an error message
     */
    public static void loadLines(@NotNull List<String> lines, @NotNull PackageRules.Builder builder, @NotNull String origin) {
        int number = 0;
        for (String line : lines) {
            number++;
            parse(line, builder, origin, number);
        }
    }

    private static void read(@NotNull BufferedReader reader, @NotNull PackageRules.Builder builder, @NotNull String origin) throws IOException {
        String line;
        int number = 0;
        while ((line = reader.readLine()) != null) {
            number++;
            parse(line, builder, origin, number);
        }
    }

    private static void parse(@NotNull String rawLine, @NotNull PackageRules.Builder builder, @NotNull String origin, int number) {
        String line = rawLine.trim();
        if (line.isEmpty() || line.startsWith("#")) {
            return;
        }
        if (line.startsWith("!")) {
            builder.allow(requireValid(line.substring(1).trim(), origin, number));
            return;
        }
        int arrow = line.indexOf(ARROW);
        if (arrow < 0) {
            builder.forbid(requireValid(line, origin, number), null);
            return;
        }
        String packageName = requireValid(line.substring(0, arrow).trim(), origin, number);
        String replacement = line.substring(arrow + ARROW.length()).trim();
        builder.forbid(packageName, replacement.isEmpty() ? null : replacement);
    }

    private static @NotNull String requireValid(@NotNull String packageName, @NotNull String origin, int number) {
        if (packageName.isEmpty()) {
            throw new IllegalArgumentException("Empty package name in " + origin + " line " + number);
        }
        for (int i = 0; i < packageName.length(); i++) {
            char c = packageName.charAt(i);
            boolean valid = Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '$';
            if (!valid) {
                throw new IllegalArgumentException("Invalid package name '" + packageName + "' in " + origin + " line " + number);
            }
        }
        return packageName;
    }
}

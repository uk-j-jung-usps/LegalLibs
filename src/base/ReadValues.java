package base;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Reads a template file, performs bulk key/value token substitution, and
 * writes the result to the matter's output folder.
 *
 * <p>The {@code substitutions} array is a flat, alternating sequence of
 * {@code %KEY%} tokens and their replacement values:
 * <pre>
 *   [ "%KEY1%", "value1", "%KEY2%", "value2", ... ]
 * </pre>
 *
 * <p>Substitutions are applied sequentially; each pass operates on the output
 * of the previous one, preserving the original behaviour.
 *
 * <p>Input path : {@code <folder>/<templateFile>}
 * <p>Output path: {@code processed/<matterNumber>/<templateFile>}
 */
public class ReadValues {

    private static final Logger LOG = Logger.getLogger(ReadValues.class.getName());

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Replaces all token occurrences in the template file and writes the result
     * to the processed output folder.
     *
     * @param matterNumber  matter number used to build the output path
     * @param folder        folder containing the source template file
     * @param templateFile  template file name (used for both source and destination)
     * @param substitutions flat alternating array of {@code %token%} / value pairs
     * @throws IOException  if the template cannot be read or the output cannot be written
     * @throws IllegalArgumentException if {@code substitutions} has an odd length
     */
    public static void ReplaceStr(String matterNumber, String folder,
                                  String templateFile, String[] substitutions)
            throws IOException {

        if (substitutions.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "substitutions array must have an even length (key/value pairs); got "
                    + substitutions.length);
        }

        var sourcePath = Path.of(folder, templateFile);
        var outputPath = Path.of("processed", matterNumber, templateFile);

        if (Load_File.lTest) {
            LOG.info("ReadValues — reading : " + sourcePath);
            LOG.info("ReadValues — writing : " + outputPath);
            LOG.info("ReadValues — pairs   : " + substitutions.length / 2);
        }

        // Read entire template into memory
        var content = readTemplate(sourcePath);

        // Apply substitutions sequentially
        content = applySubstitutions(content, substitutions);

        // Ensure the output directory exists before writing
        Files.createDirectories(outputPath.getParent());

        // Append a trailing newline (preserved from original) and write
        Files.writeString(outputPath, content + System.lineSeparator(), StandardCharsets.UTF_8);

        if (Load_File.lTest) {
            LOG.info("ReadValues — output written successfully");
        }
    }

    // ── Substitution logic ────────────────────────────────────────────────────

    /**
     * Iterates over the key/value pairs in {@code substitutions} and replaces
     * every occurrence of each {@code %KEY%} token in {@code content}.
     *
     * <p>Each pass works on the result of the previous one so that earlier
     * replacements are visible to later ones — matching the original behaviour.
     *
     * @param content       template text to process
     * @param substitutions flat alternating array of token / replacement pairs
     * @return the fully substituted string
     */
    private static String applySubstitutions(String content, String[] substitutions) {
        for (int i = 0; i < substitutions.length; i += 2) {
            var token       = substitutions[i];
            var replacement = substitutions[i + 1];

            content = replaceToken(content, token, replacement);

            if (Load_File.lTest) {
                LOG.info("  substituted: %s → %s".formatted(token, replacement));
            }
        }
        return content;
    }

    /**
     * Replaces every occurrence of {@code token} in {@code text} using a
     * manual index-walk (identical algorithm to the original), avoiding regex
     * so that tokens containing regex metacharacters (e.g. {@code %}) are
     * treated as plain literals.
     *
     * @param text        source text
     * @param token       literal token to find
     * @param replacement replacement value
     * @return text with all occurrences of {@code token} replaced
     */
    private static String replaceToken(String text, String token, String replacement) {
        var result = new StringBuilder(text.length());
        int searchFrom = 0;
        int matchAt;

        while ((matchAt = text.indexOf(token, searchFrom)) >= 0) {
            result.append(text, searchFrom, matchAt);
            result.append(replacement);
            searchFrom = matchAt + token.length();
        }

        result.append(text, searchFrom, text.length());
        return result.toString();
    }

    // ── File I/O ──────────────────────────────────────────────────────────────

    /**
     * Reads the entire template file into a {@code String} using UTF-8 encoding.
     *
     * @param path path to the template file
     * @return file contents as a single string
     * @throws IOException if the file cannot be read
     */
    private static String readTemplate(Path path) throws IOException {
        return Files.readString(path.toAbsolutePath(), StandardCharsets.UTF_8);
    }
}


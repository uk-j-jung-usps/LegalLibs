package base;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

/**
 * Merges a fixed set of RTF fragment files into a single consolidated
 * Discovery response document.
 *
 * <p>The fragment list is hard-coded (as in the original) and represents the
 * maximum possible set of interrogatories, admission requests, and document
 * requests. Counter headers are injected automatically for files whose name
 * contains {@code more_inters}, {@code more_request}, or {@code more_documents}.
 *
 * <p>Output: {@code templates/dynamic/Discovery - Agency's Standard Discovery
 * Responses Consolidated.rtf}
 *
 * <p><b>Note:</b> This class is not used in the main processing path (it is
 * commented out in the original switch). It is retained here for completeness.
 */
public class Merge_Files {

    private static final Logger LOG = Logger.getLogger(Merge_Files.class.getName());

    // ── Path constants ────────────────────────────────────────────────────────
    private static final Path DYNAMIC_FOLDER = Path.of("templates", "dynamic");
    private static final Path OUTPUT_PATH    = DYNAMIC_FOLDER.resolve(
            "Discovery - Agency's Standard Discovery Responses Consolidated.rtf");

    // ── RTF counter header templates ──────────────────────────────────────────
    private static final String ITER_HEADER     = "\\line {\\pard\\cf1\\ul INTERROGATORY NO. %NO%:\\par}";
    private static final String REQUEST_HEADER  = "\\line {\\pard\\cf1\\ul REQUEST FOR ADMISSION NO. %NO%:\\par}";
    private static final String DOCUMENT_HEADER = "\\line {\\pard\\cf1\\ul REQUEST DOCUMENTS NO. %NO%:\\par}";

    // ── Fixed fragment list ───────────────────────────────────────────────────
    //
    // Repeated entries represent the maximum number of each section type.
    // To adjust counts, add or remove entries from the relevant group.
    private static final List<Path> FRAGMENTS = List.of(
            dynPath("Top_Discovery - Agency's Standard Discovery Responses.txt"),

            dynPath("first_inter.txt"),
            dynPath("more_inters.txt"), dynPath("more_inters.txt"), dynPath("more_inters.txt"),
            dynPath("more_inters.txt"), dynPath("more_inters.txt"), dynPath("more_inters.txt"),
            dynPath("more_inters.txt"), dynPath("more_inters.txt"), dynPath("more_inters.txt"),
            dynPath("more_inters.txt"), dynPath("more_inters.txt"), dynPath("more_inters.txt"),
            dynPath("more_inters.txt"), dynPath("more_inters.txt"), dynPath("more_inters.txt"),
            dynPath("more_inters.txt"), dynPath("more_inters.txt"), dynPath("more_inters.txt"),
            dynPath("more_inters.txt"), dynPath("more_inters.txt"),   // 20 interrogatories

            dynPath("first_request.txt"),
            dynPath("more_requests.txt"), dynPath("more_requests.txt"), dynPath("more_requests.txt"),
            dynPath("more_requests.txt"), dynPath("more_requests.txt"), dynPath("more_requests.txt"),
            dynPath("more_requests.txt"), dynPath("more_requests.txt"), dynPath("more_requests.txt"),
            dynPath("more_requests.txt"),                              // 10 admission requests

            dynPath("first_document.txt"),
            dynPath("more_documents.txt"), dynPath("more_documents.txt"), dynPath("more_documents.txt"),
            dynPath("more_documents.txt"), dynPath("more_documents.txt"), dynPath("more_documents.txt"),
            dynPath("more_documents.txt"), dynPath("more_documents.txt"), dynPath("more_documents.txt"),
            dynPath("more_documents.txt"),                             // 10 document requests

            dynPath("Bot_Discovery - Agency's Standard Discovery Responses.txt")
    );

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        if (Load_File.lTest) {
            LOG.info("NewMerge_Files — merging " + FRAGMENTS.size() + " fragments → " + OUTPUT_PATH);
        }
        mergeFragments();
    }

    // ── Merge logic ───────────────────────────────────────────────────────────

    /**
     * Iterates over {@link #FRAGMENTS}, concatenates their content, and injects
     * numbered counter headers for interrogatory, request, and document sections.
     */
    private static void mergeFragments() throws Exception {
        try (var writer = Files.newBufferedWriter(OUTPUT_PATH, StandardCharsets.UTF_8)) {
            int iters     = 0;
            int requests  = 0;
            int documents = 0;

            for (int i = 0; i < FRAGMENTS.size(); i++) {
                var path     = FRAGMENTS.get(i);
                var fileName = path.getFileName().toString();
                boolean firstLine = true;  // counter header injected only on the first line of each fragment

                if (Load_File.lTest) {
                    LOG.info("Merging fragment [%d]: %s".formatted(i, path));
                }

                try (BufferedReader reader = Files.newBufferedReader(path.toAbsolutePath(), StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (firstLine) {
                            if (fileName.contains("more_inters")) {
                                iters++;
                                line = ITER_HEADER.replace("%NO%", Integer.toString(iters)) + line;
                            } else if (fileName.contains("more_request")) {
                                requests++;
                                line = REQUEST_HEADER.replace("%NO%", Integer.toString(requests)) + line;
                            } else if (fileName.contains("more_documents")) {
                                documents++;
                                line = DOCUMENT_HEADER.replace("%NO%", Integer.toString(documents)) + line;
                            }
                            firstLine = false;
                        }
                        writer.write(line);
                        writer.newLine();
                    }
                } catch (IOException e) {
                    throw new Exception("Failed to read fragment: " + path, e);
                }
            }
        } catch (IOException e) {
            throw new Exception("Failed to write output file: " + OUTPUT_PATH, e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Resolves a file name relative to the dynamic fragment folder. */
    private static Path dynPath(String fileName) {
        return DYNAMIC_FOLDER.resolve(fileName);
    }
}

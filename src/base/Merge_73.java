package base;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import base.DbConn;

/**
 * Dynamic template merge for template key 73 — SF Advice /
 * Final Subpoena State Court.
 *
 * <p>Reads question/answer pairs from {@code cmft_dynamic_ans} for the given
 * matter, assembles an ordered list of RTF fragment files, concatenates them,
 * and writes the merged document to
 * {@code templates/advice/<matterNumber>_Final_Subpoena_State_Court.rtf}.
 *
 * <p>Counter headers are injected automatically for files whose name contains
 * {@code more_inters}, {@code more_request}, or {@code more_documents}.
 */
public class Merge_73 {

    private static final Logger LOG = Logger.getLogger(Merge_73.class.getName());

    private static final int TEMPLATE_KEY = 73;

    // ── Path constants ────────────────────────────────────────────────────────
    private static final Path BASE_FOLDER    = Path.of("templates", "advice");
    private static final Path DYNAMIC_FOLDER = BASE_FOLDER.resolve(Path.of("dynamic_FSSC"));

    // ── RTF counter header templates (the literal %NO% token is replaced at runtime) ──
    private static final String ITER_HEADER     = "\\line {\\pard\\cf1\\ul INTERROGATORY NO. %NO%:\\par}";
    private static final String REQUEST_HEADER  = "\\line {\\pard\\cf1\\ul REQUEST FOR ADMISSION NO. %NO%:\\par}";
    private static final String DOCUMENT_HEADER = "\\line {\\pard\\cf1\\ul REQUEST DOCUMENTS NO. %NO%:\\par}";

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Merge_73 <matterNumber> <matterKey>");
        }
        var matterNumber = args[0];
        var sMatterKey    = args[1];

        if (Load_File.lTest) {
            LOG.info("Merge_73 — matter: " + matterNumber);
        }

        var dbConn = new DbConn();
        try {
        	dbConn.getConnection();
        } catch (SQLException e) {
            LOG.severe("Database connection failed in Merge_73: " + e.getMessage());
            return;
        }

        var fragmentPaths = buildFragmentList(sMatterKey);
        mergeFragments(matterNumber, fragmentPaths);
    }

    // ── Fragment list construction ────────────────────────────────────────────

    /**
     * Queries the dynamic Q&A table and returns the ordered list of RTF
     * fragment {@link Path}s that should be concatenated.
     */
    private static List<Path> buildFragmentList(String sMatterKey) throws SQLException {
        var sql = """
                SELECT a.dynamic_quest_key, a.answer
                  FROM cmft_dynamic_ans   a,
                       cmft_dynamic_quest  b
                 WHERE a.matter_key         = '%s'
                   AND a.template_key       = %d
                   AND a.dynamic_quest_key  = b.dynamic_quest_key
                   AND b.inter_txt_quest    = 'Y'
                 ORDER BY a.dynamic_quest_key
                """.formatted(sMatterKey, TEMPLATE_KEY);

        var fragments = new ArrayList<Path>();
        fragments.add(dynPath("Top_Final_Subpoena_State_Court_template.txt"));
        fragments.add(dynPath("inter1.txt"));

        try (ResultSet rs = DbConn.execSQL(sql)) {
            while (rs.next()) {
                int questKey = rs.getInt("dynamic_quest_key");
                int answer   = rs.getInt("answer");

                if (Load_File.lTest) {
                    LOG.info("Q%d  answer=%d".formatted(questKey, answer));
                }

                appendFragmentsForQuestion(fragments, questKey, answer);
            }
        }

        return fragments;
    }

    /**
     * Appends the appropriate fragment file(s) based on a single question/answer pair.
     * All conditional logic from the original switch-of-ifs is preserved exactly.
     */
    private static void appendFragmentsForQuestion(List<Path> fragments, int questKey, int answer) {
        switch (questKey) {
            case 1 -> {
                if (answer == 1) fragments.add(dynPath("inter2.txt"));
                fragments.add(dynPath("inter3.txt")); // always included
            }
            case 2 -> {
                if (answer == 6) fragments.add(dynPath("inter4.txt"));
                if (answer == 7) fragments.add(dynPath("inter5.txt"));
                if (answer == 8) fragments.add(dynPath("inter6.txt"));
                fragments.add(dynPath("inter7.txt")); // always included
            }
            case 3 -> { if (answer == 1) fragments.add(dynPath("inter8.txt")); }
            case 4 -> { if (answer == 1) fragments.add(dynPath("inter9.txt")); }
            case 5 -> { if (answer == 1) fragments.add(dynPath("inter10.txt")); }
            case 6 -> { if (answer == 1) fragments.add(dynPath("inter11.txt")); }
            case 7 -> { if (answer == 1) fragments.add(dynPath("inter12.txt")); }
            case 8 -> {
                if (answer == 1) fragments.add(dynPath("inter13.txt"));
                if (Load_File.lTest) LOG.info("  Adding bottom fragments");
                fragments.add(dynPath("inter14.txt")); // always included
                fragments.add(dynPath("Bottom_Final_Subpoena_State_Court_template.txt"));
            }
            default -> LOG.warning("Unrecognised question key: " + questKey);
        }
    }

    // ── File merge ────────────────────────────────────────────────────────────

    /**
     * Concatenates all fragment files into the final output RTF, injecting
     * numbered counter headers for interrogatories, requests, and documents.
     */
    private static void mergeFragments(String matterNumber, List<Path> fragments) throws Exception {
        var outputPath = BASE_FOLDER.resolve(matterNumber + "_Final_Subpoena_State_Court.rtf");

        try (var writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            int iters     = 0;
            int requests  = 0;
            int documents = 0;

            for (int i = 0; i < fragments.size(); i++) {
                var path     = fragments.get(i);
                var fileName = path.getFileName().toString();

                if (Load_File.lTest) {
                    LOG.info("Merging fragment [%d]: %s".formatted(i, path));
                }

                boolean firstLine = true;   // reset per file; tracks whether the counter header has been injected

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
            throw new Exception("Failed to write output file: " + outputPath, e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Resolves a file name relative to the dynamic fragment folder. */
    private static Path dynPath(String fileName) {
        return DYNAMIC_FOLDER.resolve(fileName);
    }
}


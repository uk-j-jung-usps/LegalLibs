package base;

import java.io.BufferedReader;
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
 * Dynamic template merge for template key 77 — EEOC Offer of Resolution.
 *
 * <p>Reads question/answer pairs from {@code cmft_dynamic_ans} for the given
 * matter, assembles an ordered list of RTF fragment files, concatenates them,
 * and writes the merged document to
 * {@code templates/eeoc/<matterNumber>_Offer_of_Resolution.rtf}.
 */
public class Merge_77 {

    private static final Logger LOG = Logger.getLogger(Merge_77.class.getName());

    private static final int TEMPLATE_KEY = 77;

    // ── Path constants ────────────────────────────────────────────────────────
    private static final Path BASE_FOLDER    = Path.of("templates", "eeoc");
    private static final Path DYNAMIC_FOLDER = BASE_FOLDER.resolve("offer");

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Merge_77 <matterNumber> <matterKey>");
        }
        var matterNumber = args[0];
        var cMatterKey = args[1];

        if (LoadFile.lTest) {
            LOG.info("Merge_77 — matter: " + matterNumber);
        }

        var dbConn = new DbConn();
        try {
            dbConn.getConnection();
        } catch (SQLException e) {
            LOG.severe("Database connection failed in NewMerge_77: " + e.getMessage());
            return;
        }

        var fragmentPaths = buildFragmentList(cMatterKey);
        mergeFragments(matterNumber, fragmentPaths);
    }

    // ── Fragment list construction ────────────────────────────────────────────

    /**
     * Queries the dynamic Q&A table and returns the ordered list of RTF
     * fragment {@link Path}s to concatenate.
     */
    private static List<Path> buildFragmentList(String cMatterKey) throws SQLException {
        var sql = """
                SELECT a.dynamic_quest_key, a.answer
                  FROM lawmanager.lawmanager.cmft_dynamic_ans   a,
                       lawmanager.cmft_dynamic_quest  b
                 WHERE a.matter_key        = '%s'
                   AND a.template_key      = %d
                   AND a.dynamic_quest_key = b.dynamic_quest_key
                   AND b.inter_txt_quest   = 'Y'
                 ORDER BY a.dynamic_quest_key
                """.formatted(cMatterKey, TEMPLATE_KEY);

        var fragments = new ArrayList<Path>();
        fragments.add(dynPath("Top_Offer_of_Resolution.rtf"));

        try (ResultSet rs = DbConn.execSQL(sql)) {
            while (rs.next()) {
                int questKey = rs.getInt("dynamic_quest_key");
                int answer   = rs.getInt("answer");

                if (LoadFile.lTest) {
                    LOG.info("  Q%d  answer=%d".formatted(questKey, answer));
                }

                appendFragmentsForQuestion(fragments, questKey, answer);
            }
        }

        return fragments;
    }

    /**
     * Appends the appropriate fragment file(s) for a single question/answer pair.
     * All conditional logic from the original is preserved exactly.
     */
    private static void appendFragmentsForQuestion(List<Path> fragments, int questKey, int answer) {
        switch (questKey) {
            case 12 -> {
                // Answer 1 means all inter1–inter7 are included as a block
                if (answer == 1) {
                    for (int i = 1; i <= 7; i++) {
                        fragments.add(dynPath("inter" + i + ".txt"));
                    }
                }
            }
            case 13 -> { if (answer == 1) fragments.add(dynPath("inter2.txt")); }
            case 14 -> { if (answer == 1) fragments.add(dynPath("inter3.txt")); }
            case 15 -> { if (answer == 1) fragments.add(dynPath("inter4.txt")); }
            case 16 -> { if (answer == 1) fragments.add(dynPath("inter5.txt")); }
            case 17 -> { if (answer == 1) fragments.add(dynPath("inter6.txt")); }
            case 18 -> { if (answer == 1) fragments.add(dynPath("inter7.txt")); }
            case 19 -> { if (answer == 1) fragments.add(dynPath("inter8.txt")); }
            case 20 -> { if (answer == 1) fragments.add(dynPath("inter9.txt")); }
            case 21 -> {
                if (answer == 1) fragments.add(dynPath("inter10.txt"));
                fragments.add(dynPath("inter11.txt")); // always included
            }
            case 22 -> {
                if (answer == 1) fragments.add(dynPath("inter12.txt"));
                if (LoadFile.lTest) LOG.info("  Adding bottom fragment");
                fragments.add(dynPath("Bottom_Offer_of_Resolution.rtf")); // always included
            }
            default -> LOG.warning("Unrecognised question key: " + questKey);
        }
    }

    // ── File merge ────────────────────────────────────────────────────────────

    /**
     * Concatenates all fragment files into the final output RTF.
     * Unlike template 73, this merge has no counter-header injection.
     */
    private static void mergeFragments(String matterNumber, List<Path> fragments) throws Exception {
        var outputPath = BASE_FOLDER.resolve(matterNumber + "_Offer_of_Resolution.rtf");

        try (var writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            for (int i = 0; i < fragments.size(); i++) {
                var path = fragments.get(i);

                if (LoadFile.lTest) {
                    LOG.info("Merging fragment [%d]: %s".formatted(i, path));
                }

                try (BufferedReader reader = Files.newBufferedReader(path.toAbsolutePath(), StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
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

package base;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import ldis.legallibs.db.DbConn;

/**
 * Dynamic template merge for template key 80 — MSPB Ltr Applnt Rep Req Auth final.
 *
 * <p>Processing steps:
 * <ol>
 *   <li>Read Q&A pairs to determine the "SENTENCE" substitution variable (question 24).</li>
 *   <li>Read the representative fields; fall back to appellant fields when no
 *       representative exists.</li>
 *   <li>Delete and re-insert dynamic key/value pairs in {@code cmft_matterkey_pairs}
 *       (keys 147, 149–155).</li>
 *   <li>Concatenate RTF fragments and write the output document.</li>
 * </ol>
 *
 * <p><b>Security note:</b> {@code matterKey} is concatenated into SQL because
 * {@code DbBean.execSQL} accepts a plain {@code String}. Refactor {@code DbBean}
 * to expose {@code PreparedStatement} support to eliminate the SQL-injection risk.
 */
public class Merge_80 {

    private static final Logger LOG = Logger.getLogger(Merge_80.class.getName());

    private static final int TEMPLATE_KEY = 80;

    // ── Path constants ────────────────────────────────────────────────────────
    private static final Path   BASE_FOLDER   = Path.of("templates", "mspb");
    private static final Path   DYNAMIC_FOLDER = BASE_FOLDER.resolve("temp80");
    private static final String OUTPUT_SUFFIX  = "_MSPB Template Ltr Applnt Rep Req Auth final.rtf";

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: NewMerge_80 <matterNumber> <matterKey>");
        }
        var matterNumber = args[0];
        var matterKey    = args[1];

        if (LoadFile.lTest) {
            LOG.info("NewMerge_80 — matter: " + matterNumber);
        }

        var dbConn = new DbConn();
        try {
            dbConn.getConnection();
        } catch (SQLException e) {
            LOG.severe("Database connection failed in Merge_80: " + e.getMessage());
            return;
        }

        // Step 1 — resolve "SENTENCE" from dynamic Q&A (question 24)
        var sentence = resolveSentence(matterKey);

        // Step 2 — resolve address fields (rep, or fall back to appellant)
        var addr = resolveAddressFields(matterKey);

        // Step 3 — persist dynamic key/value pairs to cmft_matterkey_pairs
        persistDynamicPairs(matterKey, addr, sentence);

        // Step 4 — merge RTF fragments
        var fragments = buildFragmentList(addr.hasRepresentative());
        mergeFragments(matterNumber, fragments);
    }

    // ── Step 1: sentence resolution ───────────────────────────────────────────

    /**
     * Returns the y_text (prepended) or n_text from question 24, or empty string
     * if the question row is not found.
     *
     * <p>Note: the original code does {@code y_text + cSentence} (prepend) for
     * answer==1 and plain {@code n_text} otherwise — that behaviour is preserved.
     */
    private static String resolveSentence(String matterKey) throws SQLException {
        var sql = """
                SELECT a.dynamic_quest_key, a.answer, b.y_text, b.n_text
                  FROM cmft_dynamic_ans   a,
                       cmft_dynamic_quest  b
                 WHERE a.matter_key        = '%s'
                   AND a.template_key      = %d
                   AND a.dynamic_quest_key = b.dynamic_quest_key
                   AND b.inter_txt_quest   = 'Y'
                 ORDER BY a.dynamic_quest_key
                """.formatted(matterKey, TEMPLATE_KEY);

        try (ResultSet rs = DbConn.execSQL(sql)) {
            while (rs.next()) {
                if (rs.getInt("dynamic_quest_key") == 24) {
                    // Original: answer==1 → y_text + cSentence (cSentence was "" at this point,
                    // so effectively just y_text); answer!=1 → n_text.
                    return rs.getInt("answer") == 1
                            ? rs.getString("y_text")
                            : rs.getString("n_text");
                }
            }
        }
        return "";
    }

    // ── Step 2: address field resolution ─────────────────────────────────────

    /**
     * Immutable carrier for the resolved address fields and the representative flag.
     */
    private record AddressFields(
            String mrMs,
            String firstName,
            String lastName,
            String company,
            String address,
            String cityStZip,
            boolean hasRepresentative) {

        /** "have your client sign" when a rep exists, otherwise "sign". */
        String signClient() {
            return hasRepresentative ? "have your client sign" : "sign";
        }
    }

    /**
     * Deletes stale dynamic pairs, then attempts to read representative fields.
     * If the representative's first or last name is absent, falls back to the
     * appellant's own fields.
     */
    private static AddressFields resolveAddressFields(String cMatterKey) throws SQLException {
        deleteDynamicPairs(cMatterKey);

        var repSql = """
                SELECT t.tempvar_key_name, t.tempvar_value
                  FROM cmft_matterkey_pairs t
                 WHERE t.matter_key       = %s
                   AND t.tempvar_key_name IN (
                         'REP_CITYSTZIP','REP_MR_MS','REP_FN',
                         'REP_LN','REP_ADD','REP_COMPANY')
                """.formatted(cMatterKey);

        if (LoadFile.lTest) LOG.info("SQL (rep query): " + repSql);

        Map<String, String> rep = new HashMap<>();
        try (ResultSet rs = DbConn.execSQL(repSql)) {
            while (rs.next()) {
                var key = rs.getString("tempvar_key_name");
                var val = rs.getString("tempvar_value"); // may be null
                rep.put(key, val);
                if (LoadFile.lTest) {
                    LOG.info("  Rep field %s = %s".formatted(key, val));
                }
            }
        }

        String fn      = safeGet(rep, "REP_FN");
        String ln      = safeGet(rep, "REP_LN");
        boolean nameNull = fn.isEmpty() || ln.isEmpty();

        if (!nameNull) {
            if (LoadFile.lTest) LOG.info("Representative exists — adding CC");
            return new AddressFields(
                    safeGet(rep, "REP_MR_MS"),
                    fn,
                    ln,
                    safeGet(rep, "REP_COMPANY"),
                    safeGet(rep, "REP_ADD"),
                    safeGet(rep, "REP_CITYSTZIP"),
                    true);
        }

        // Fall back to appellant fields
        if (LoadFile.lTest) LOG.info("No representative — using appellant fields");

        var appSql = """
                SELECT t.tempvar_key_name, t.tempvar_value
                  FROM cmft_matterkey_pairs t
                 WHERE t.matter_key       = %s
                   AND t.tempvar_key_name IN (
                         'APPELLANT_MR_MS','APPELLANT_FN','APPELLANT_LN',
                         'APPELLANT_ADD','APPELLANT_CITYSTZIP')
                """.formatted(cMatterKey);

        if (LoadFile.lTest) LOG.info("SQL (appellant query): " + appSql);

        Map<String, String> app = new HashMap<>();
        try (ResultSet rs = DbConn.execSQL(appSql)) {
            while (rs.next()) {
                var key = rs.getString("tempvar_key_name");
                var val = rs.getString("tempvar_value");
                app.put(key, val);
                if (LoadFile.lTest) {
                    LOG.info("  Appellant field %s = %s".formatted(key, val));
                }
            }
        }

        return new AddressFields(
                safeGet(app, "APPELLANT_MR_MS"),
                safeGet(app, "APPELLANT_FN"),
                safeGet(app, "APPELLANT_LN"),
                "",   // no company for appellant
                safeGet(app, "APPELLANT_ADD"),
                safeGet(app, "APPELLANT_CITYSTZIP"),
                false);
    }

    /** Returns the map value for {@code key}, or an empty string if absent/null. */
    private static String safeGet(Map<String, String> map, String key) {
        var val = map.get(key);
        return (val == null) ? "" : val;
    }

    // ── Step 3: persist dynamic pairs ────────────────────────────────────────

    private static void deleteDynamicPairs(String matterKey) {
        var sql = """
                DELETE cmft_matterkey_pairs t
                 WHERE t.matter_key  = %s
                   AND (t.tempvar_key BETWEEN 150 AND 155
                        OR t.tempvar_key IN (147, 149))
                """.formatted(matterKey);
        try {
            DbConn.execSQL(sql);
            if (LoadFile.lTest) {
                LOG.info("Deleted stale dynamic pairs for matter " + matterKey);
            }
        } catch (SQLException e) {
            LOG.severe("Failed to delete dynamic pairs: " + e.getMessage());
        }
    }

    /**
     * Inserts the eight dynamic key/value pairs into {@code cmft_matterkey_pairs}.
     * Each entry is: {tempvar_key_name, value, tempvar_key}.
     */
    private static void persistDynamicPairs(String matterKey, AddressFields addr, String sentence)
            throws SQLException {

        Object[][] inserts = {
            {"APP_DYN_FN",         addr.firstName(),  150},
            {"APP_DYN_LN",         addr.lastName(),   151},
            {"APP_DYN_COMPANY",    addr.company(),    152},
            {"APP_DYN_ADD",        addr.address(),    153},
            {"APP_DYN_MR_MS",      addr.mrMs(),       154},
            {"APP_DYN_CITYSTZIP",  addr.cityStZip(),  155},
            {"SIGN_CLIENT",        addr.signClient(), 147},
            {"SENTENCE",           sentence,          149},
        };

        var baseSql = """
                INSERT INTO cmft_matterkey_pairs
                    (matter_key, tempvar_key_name, tempvar_value, tempvar_key, date_added, added_by)
                VALUES (%s, '%s', '%s', %d, SYSDATE, 100000)
                """;

        for (var row : inserts) {
            var sql = baseSql.formatted(matterKey, row[0], row[1], row[2]);
            if (LoadFile.lTest) LOG.info("Insert: " + sql);
            try {
                DbConn.execSQL(sql);
            } catch (SQLException e) {
                LOG.severe("Failed to insert dynamic pair '%s': %s".formatted(row[0], e.getMessage()));
            }
        }
    }

    // ── Step 4: fragment list and merge ──────────────────────────────────────

    private static List<Path> buildFragmentList(boolean hasRepresentative) {
        var fragments = new ArrayList<Path>();
        fragments.add(dynPath("top_text.txt"));
        if (hasRepresentative) {
            fragments.add(dynPath("middle_text.txt"));
        }
        fragments.add(dynPath("bottom_text.txt"));
        return fragments;
    }

    /** Concatenates all fragment files into the final output RTF. */
    private static void mergeFragments(String matterNumber, List<Path> fragments) throws Exception {
        var outputPath = BASE_FOLDER.resolve(matterNumber + OUTPUT_SUFFIX);

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

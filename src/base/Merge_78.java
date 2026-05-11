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
 * Dynamic template merge for template key 78 — EEOC Ltr Applnt Rep Req Auth
 * (Discovery - OWCP Release Request).
 *
 * <p>Processing steps:
 * <ol>
 *   <li>Read Q&A pairs to determine the "SENTENCE" substitution variable.</li>
 *   <li>Read the complainant representative fields (or fall back to complainant
 *       fields when no representative exists) to populate dynamic address vars.</li>
 *   <li>Delete and re-insert those dynamic key/value pairs in
 *       {@code cmft_matterkey_pairs} (keys 141–148).</li>
 *   <li>Concatenate RTF fragments and write the output document.</li>
 * </ol>
 *
 * <p><b>Security note:</b> {@code matterKey} is currently concatenated into SQL
 * because {@code DbBean.execSQL} accepts a plain {@code String}. Refactor
 * {@code DbBean} to expose {@code PreparedStatement} support to eliminate
 * the SQL-injection risk.
 */
public class Merge_78 {

    private static final Logger LOG = Logger.getLogger(Merge_78.class.getName());

    private static final int TEMPLATE_KEY = 78;

    // ── Path constants ────────────────────────────────────────────────────────
    private static final Path BASE_FOLDER    = Path.of("templates", "eeoc");
    private static final Path DYNAMIC_FOLDER = BASE_FOLDER.resolve("temp78");
    private static final String OUTPUT_FILE  = "Discovery - OWCP Release Request.rtf";

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: NewMerge_78 <matterNumber> <matterKey>");
        }
        var matterNumber = args[0];
        var matterKey    = args[1];

        if (LoadFile.TEST_MODE) {
            LOG.info("Merge_78 — matter: " + matterNumber);
        }

        var dbConn = new DbConn();
        try {
        	dbConn.getConnection();
        } catch (SQLException e) {
            LOG.severe("Database connection failed in NewMerge_78: " + e.getMessage());
            return;
        }

        // Step 1 — resolve "SENTENCE" from dynamic Q&A
        var sentence = resolveSentence(matterKey);

        // Step 2 — resolve address fields (rep, or fall back to complainant)
        var addr = resolveAddressFields(matterKey);

        // Step 3 — persist dynamic key/value pairs to cmft_matterkey_pairs
        persistDynamicPairs(matterKey, addr, sentence);

        // Step 4 — merge RTF fragments
        var fragments = buildFragmentList(addr.hasRepresentative());
        mergeFragments(fragments);
    }

    // ── Step 1: sentence resolution ───────────────────────────────────────────

    /** Returns the y_text or n_text from question 23, or empty string if not found. */
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
                if (rs.getInt("dynamic_quest_key") == 23) {
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
     * Immutable carrier for the resolved address fields and the flag that
     * controls which fragment list and sign-client text to use.
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
     * First attempts to read the complainant's representative fields.
     * If none exist (name is null or "PRO SE"), falls back to the complainant's
     * own fields. The returned record carries {@code hasRepresentative} to
     * drive downstream branching.
     */
    private static AddressFields resolveAddressFields(String matterKey) throws SQLException {
        // Always delete stale dynamic pairs before re-querying
        deleteDynamicPairs(matterKey);

        var repSql = """
                SELECT t.tempvar_key_name, t.tempvar_value
                  FROM cmft_matterkey_pairs t
                  WHERE t.matter_key       = %s
                  AND t.tempvar_key_name IN (
                  REP_CITYSTZIP','COMP_REP_MR_MS','COMP_REP_FN',
                  'COMP_REP_LN','COMP_REP_ADD','COMP_REP_COMPANY')
                """.formatted(matterKey);

        if (LoadFile.TEST_MODE) LOG.info("SQL (rep query): " + repSql);

        var rep = new java.util.HashMap<String, String>();
        try (ResultSet rs = DbConn.execSQL(repSql)) {
            while (rs.next()) {
                var key = rs.getString("tempvar_key_name");
                var val = rs.getString("tempvar_value"); // may be null
                rep.put(key, val);
                if (LoadFile.TEST_MODE) LOG.info("Rep field %s = %s".formatted(key, val));
            }
        }

        String fn = safeGet(rep, "COMP_REP_FN");
        String ln = safeGet(rep, "COMP_REP_LN");
        boolean nameNull = fn == null || "PRO SE".equalsIgnoreCase(fn)
                        || ln == null || "PRO SE".equalsIgnoreCase(ln);

        if (!nameNull) {
            if (LoadFile.TEST_MODE) LOG.info("Representative exists — adding CC");
            return new AddressFields(
                    safeGet(rep, "COMP_REP_MR_MS"),
                    fn,
                    ln,
                    safeGet(rep, "COMP_REP_COMPANY"),
                    safeGet(rep, "COMP_REP_ADD"),
                    safeGet(rep, "REP_CITYSTZIP"),
                    true);
        }

        // Fall back to complainant fields
        if (LoadFile.TEST_MODE) LOG.info("No representative — using complainant fields");

        var compSql = """
                SELECT t.tempvar_key_name, t.tempvar_value
                  FROM cmft_matterkey_pairs t
                 WHERE t.matter_key       = %s
                   AND t.tempvar_key_name IN (
                         'COMP_MR_MS','COMP_FN','COMP_LN','COMP_ADD','COMP_CITYSTZIP')
                """.formatted(matterKey);

        if (LoadFile.TEST_MODE) LOG.info("SQL (comp query): " + compSql);

        var comp = new java.util.HashMap<String, String>();
        try (ResultSet rs = DbConn.execSQL(compSql)) {
            while (rs.next()) {
                var key = rs.getString("tempvar_key_name");
                var val = rs.getString("tempvar_value");
                comp.put(key, val);
                if (LoadFile.TEST_MODE) LOG.info("  Comp field %s = %s".formatted(key, val));
            }
        }

        return new AddressFields(
                safeGet(comp, "COMP_MR_MS"),
                safeGet(comp, "COMP_FN"),
                safeGet(comp, "COMP_LN"),
                "",   // no company for complainant
                safeGet(comp, "COMP_ADD"),
                safeGet(comp, "COMP_CITYSTZIP"),
                false);
    }

    /** Returns the map value for {@code key}, or an empty string if absent/null. */
    private static String safeGet(java.util.Map<String, String> map, String key) {
        var val = map.get(key);
        return (val == null) ? "" : val;
    }

    // ── Step 3: persist dynamic pairs ────────────────────────────────────────

    private static void deleteDynamicPairs(String matterKey) {
        var sql = """
                DELETE cmft_matterkey_pairs t
                 WHERE t.matter_key  = %s
                   AND t.tempvar_key BETWEEN 141 AND 147
                """.formatted(matterKey);
        try {
            DbConn.execSQL(sql);
            if (LoadFile.TEST_MODE) LOG.info("Deleted stale dynamic pairs for matter " + matterKey);
        } catch (SQLException e) {
            LOG.severe("Failed to delete dynamic pairs: " + e.getMessage());
        }
    }

    /**
     * Inserts the eight dynamic key/value pairs (keys 141–148) into
     * {@code cmft_matterkey_pairs}.
     */
    private static void persistDynamicPairs(String matterKey, AddressFields addr, String sentence)
            throws SQLException {

        // Each entry: {tempvar_key_name, value, tempvar_key}
        Object[][] inserts = {
            {"COMP_DYN_FN",         addr.firstName(),  141},
            {"COMP_DYN_LN",         addr.lastName(),   142},
            {"COMP_DYN_COMPANY",    addr.company(),    143},
            {"COMP_DYN_ADD",        addr.address(),    144},
            {"COMP_DYN_CITYSTZIP",  addr.cityStZip(),  145},
            {"COMP_DYN_MR_MS",      addr.mrMs(),       146},
            {"SIGN_CLIENT",         addr.signClient(), 147},
            {"SENTENCE",            sentence,          148},
        };

        var base = """
                INSERT INTO cmft_matterkey_pairs
                    (matter_key, tempvar_key_name, tempvar_value, tempvar_key, date_added, added_by)
                VALUES (%s, '%s', '%s', %d, SYSDATE, 100000)
                """;

        for (var row : inserts) {
            var sql = base.formatted(matterKey, row[0], row[1], row[2]);
            if (LoadFile.TEST_MODE) LOG.info("Insert: " + sql);
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

    /** Concatenates all fragment files into the output RTF in the dynamic folder. */
    private static void mergeFragments(List<Path> fragments) throws Exception {
        var outputPath = DYNAMIC_FOLDER.resolve(OUTPUT_FILE);

        try (var writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            for (int i = 0; i < fragments.size(); i++) {
                var path = fragments.get(i);
                if (LoadFile.TEST_MODE) {
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


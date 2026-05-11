package base;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import base.DbConn;

/**
 * Retrieves key/value substitution pairs for a matter from the database
 * and delegates template variable replacement to {@link ReadValues#ReplaceStr}.
 *
 * <p>Expected contents of {@code templateData} (by index):
 * <ol start="0">
 *   <li>template_folder</li>
 *   <li>template_name</li>
 *   <li>template_key</li>
 *   <li>matter_number</li>
 *   <li>matter_name</li>
 *   <li>matter_key</li>
 * </ol>
 */
public class ProcessBase {

    private static final Logger LOG = Logger.getLogger(ProcessBase.class.getName());

    // Index constants make intent clear and guard against positional mistakes.
    private static final int IDX_FOLDER        = 0;
    private static final int IDX_TEMPLATE_NAME = 1;
    private static final int IDX_MATTER_NUMBER = 3;
    private static final int IDX_MATTER_KEY    = 5;

    public static void process(List<String> templateData) {
        var folder       = templateData.get(IDX_FOLDER);
        var templateName = templateData.get(IDX_TEMPLATE_NAME);
        var matterNumber = templateData.get(IDX_MATTER_NUMBER);
        var matterKey    = templateData.get(IDX_MATTER_KEY);

        if (LoadFile.lTest) {
            LOG.info("ProcessBase — folder: %s | template: %s | matter: %s"
                    .formatted(folder, templateName, matterNumber));
        }

        var dbConn = new DbConn();
        try {
            dbConn.getConnection();
        } catch (SQLException e) {
            LOG.severe("Database connection failed in ProcessBase: " + e.getMessage());
            return;
        }

        String[] substitutions = loadSubstitutions(matterKey);
        if (substitutions == null) return;  // error already logged

        try {
            ReadValues.ReplaceStr(matterNumber, folder, templateName, substitutions);
        } catch (IOException e) {
            LOG.severe("Template replacement failed for %s/%s: %s"
                    .formatted(folder, templateName, e.getMessage()));
        }
    }

    /**
     * Queries {@code cmft_matterkey_pairs} and returns a flat array of
     * alternating {@code %key%} / value pairs ready for substitution,
     * or {@code null} on SQL failure.
     *
     * <p>Null and the sentinel value {@code "BLANK"} are both normalised to
     * an empty string so templates never receive a literal {@code null}.
     */
    private static String[] loadSubstitutions(String matterKey) {
        var sql = """
                SELECT a.tempvar_key_name, a.tempvar_value
                  FROM cmft_matterkey_pairs a
                 WHERE a.matter_key = '%s'
                """.formatted(matterKey);

        var pairs = new ArrayList<String>();

        try (ResultSet rs = DbConn.execSQL(sql)) {
            while (rs.next()) {
                var keyName = rs.getString("tempvar_key_name");
                var rawValue = rs.getString("tempvar_value");

                // Normalise null and the legacy "BLANK" sentinel to empty string.
                var value = (rawValue == null || "BLANK".equals(rawValue)) ? "" : rawValue;

                pairs.add("%" + keyName + "%");
                pairs.add(value);

                if (LoadFile.lTest) {
                    LOG.info("  substitution: %%%s%% = %s".formatted(keyName, value));
                }
            }
        } catch (SQLException e) {
            LOG.severe("Failed to load substitutions for matter_key %s: %s"
                    .formatted(matterKey, e.getMessage()));
            return null;
        }

        return pairs.toArray(String[]::new);
    }
}

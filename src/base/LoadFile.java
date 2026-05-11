package base;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import base.DbConn;

/**
 * Entry point for the Legal Libs template processing pipeline.
 *
 * <p>Processing flow:
 * <ol>
 *   <li>Query {@code CMFT_LOAD_FILE_BASE_ONE} to obtain all base records marked
 *       for processing ({@code process = 'Y'}).</li>
 *   <li>For each base record, query {@code CMFT_LOAD_FILE_BASE_TWO} to load the
 *       full list of selected templates associated with that record.</li>
 *   <li>Query {@code CMFT_LOAD_FILE_BASE_THREE} to find any <em>dynamic</em>
 *       templates ({@code dynamic IN ('Y','M')}) for the same base record.</li>
 *   <li>Route each dynamic template to the appropriate {@link Merge_73},
 *       {@link Merge_77}, {@link Merge_78}, {@link Merge_80}, or
 *       {@link Merge_84} class via an enhanced switch expression.</li>
 *   <li>Prepare the output directory (create or clean), then delegate each
 *       template entry to {@link ProcessBase#process} for variable substitution.</li>
 * </ol>
 *
 * <p>The public flags {@link #lTest} and {@link #lEmail} are referenced by
 * other classes in this package as {@code newLoadFile.lTest} /
 * {@code newLoadFile.lEmail} and control verbose logging and email dispatch
 * respectively.
 */
public class LoadFile {

    // ── Global flags (referenced by other classes as newLoadFile.lTest) ────────
    /** When {@code true}, verbose informational log messages are emitted. */
    public static final boolean lTest  = true;
    /** When {@code false}, email sending is suppressed (useful during testing). */
    public static final boolean lEmail = true;

    private static final Logger LOG = Logger.getLogger(LoadFile.class.getName());

    // ── Dynamic template key constants ────────────────────────────────────────
    private static final int TEMPLATE_SF_ADVICE_73 = 73;
    private static final int TEMPLATE_SF_EEOC_77   = 77;
    private static final int TEMPLATE_SF_EEOC_78   = 78;
    private static final int TEMPLATE_SF_MSPB_80   = 80;
    private static final int TEMPLATE_SF_MSPB_84   = 84;

    // ── Logging constants ─────────────────────────────────────────────────────
    private static final String LOG_FILE_NAME = "CMFT_Mail.log";

    // ── Entry point ───────────────────────────────────────────────────────────

    /**
     * Application entry point.  Sets up file logging, opens the database
     * connection, and drives the full base-record processing loop.
     *
     * @param args command-line arguments (not used)
     * @throws IOException  if the log file handler cannot be created
     * @throws SQLException if an unrecoverable database error occurs
     */
    public static void main(String[] args) throws IOException, SQLException {
        setupFileLogger();
        LOG.info("Running Legal Libs");

        if (LoadFile.lTest) LOG.info("Begin newLoadFile");

        var db = new DbConn();
        try {
            db.getConnection();
        } catch (SQLException e) {
            LOG.severe("Database connection failed: " + e.getMessage());
            return;
        }

        processAllBaseRecords(db);
    }

    // ── Logger setup ──────────────────────────────────────────────────────────

    /**
     * Attaches a {@link FileHandler} to {@link #LOG} so that all messages are
     * also written to {@value #LOG_FILE_NAME} in plain-text format.
     */
    private static void setupFileLogger() {
        try {
            // 10 MB per file, up to 5 rotating files, append to the current file.
            var fh = new FileHandler(LOG_FILE_NAME, 10 * 1024 * 1024, 5, /* append = */ true);
            fh.setFormatter(new SimpleFormatter());
            LOG.addHandler(fh);
        } catch (IOException e) {
            LOG.warning("Could not attach file handler for %s: %s"
                    .formatted(LOG_FILE_NAME, e.getMessage()));
        }
    }

    // ── Base record processing ────────────────────────────────────────────────

    /**
     * Iterates over all base records returned by
     * {@link CmftSqlQueries#CMFT_LOAD_FILE_BASE_ONE} and processes each one.
     *
     * @param db open {@link DbConn} instance
     * @throws SQLException if the query cannot be executed
     */
    private static void processAllBaseRecords(DbConn db) throws SQLException {
        var baseRecords = db.cmftQueryExecute(CmftSqlQueries.CMFT_LOAD_FILE_BASE_ONE);

        if (LoadFile.lTest) {
            LOG.info("Base records to process: " + baseRecords.size());
        }

        for (var row : baseRecords) {
            int baseKey = ((Number) row[0]).intValue();
            processBaseRecord(db, baseKey);
        }
    }

    /**
     * Processes a single base record:
     * <ol>
     *   <li>Loads all associated template rows (CMFT_LOAD_FILE_BASE_TWO).</li>
     *   <li>Finds dynamic templates (CMFT_LOAD_FILE_BASE_THREE).</li>
     *   <li>For each dynamic template, routes it and processes all template rows.</li>
     * </ol>
     *
     * @param db      open {@link DbConn} instance
     * @param baseKey the base record key to process
     * @throws SQLException if any database query fails
     */
    private static void processBaseRecord(DbConn db, int baseKey) throws SQLException {
        var templateRows = db.cmftQueryExecute(CmftSqlQueries.CMFT_LOAD_FILE_BASE_TWO, baseKey);
        var dynamicRows  = db.cmftQueryExecute(CmftSqlQueries.CMFT_LOAD_FILE_BASE_THREE, baseKey);

        if (dynamicRows.isEmpty()) {
            if (LoadFile.lTest) {
                LOG.info("No dynamic templates found for baseKey: " + baseKey);
            }
            return;
        }

        for (var dynamicRow : dynamicRows) {
            processDynamicTemplate(dynamicRow, templateRows);
        }
    }

    // ── Dynamic template handling ─────────────────────────────────────────────

    /**
     * Handles one dynamic-template row: logs it, routes it to the correct
     * {@code Merge_*} class, prepares the output directory (first pass only),
     * and invokes {@link ProcessBase#process} for every template in the base
     * template list.
     *
     * @param dynamicRow   one row from {@code CMFT_LOAD_FILE_BASE_THREE}
     * @param templateRows all rows from {@code CMFT_LOAD_FILE_BASE_TWO} for the
     *                     same base key
     */
    private static void processDynamicTemplate(Object[] dynamicRow, List<Object[]> templateRows) {
        int    templateKey  = ((Number) dynamicRow[2]).intValue();
        int    matterKey    = ((Number) dynamicRow[8]).intValue();
        String matterNumber = (String) dynamicRow[6];
        String matterName   = (String) dynamicRow[7];
        String cMatterKey   = String.valueOf(matterKey);

        if (LoadFile.lTest) {
            LOG.info("Dynamic template found — matter: %s | templateKey: %d"
                    .formatted(matterNumber, templateKey));
        }

        // ── Route to the appropriate Merge class ──────────────────────────────
        String[] mergeArgs = {matterNumber, cMatterKey};
        routeDynamicTemplate(templateKey, mergeArgs);

        // ── Process all templates in the base list ────────────────────────────
        if (templateRows.isEmpty()) return;

        boolean firstRow = true;
        for (var templateRow : templateRows) {
            String dynamic        = (String) templateRow[1];
            String templateFolder = (String) templateRow[5];
            String templateName   = (String) templateRow[6];
            String cTemplateKey   = String.valueOf(((Number) templateRow[4]).intValue());

            if ("Y".equals(dynamic)) {
                if (LoadFile.lTest) {
                    LOG.info("Dynamic template entry: %s - %s".formatted(matterNumber, templateName));
                }
            } else {
                if (LoadFile.lTest) {
                    LOG.info("Normal template entry: " + templateName);
                }
            }

            var entry = buildTemplateEntry(
                    dynamic, templateFolder, templateName,
                    cTemplateKey, matterNumber, matterName, cMatterKey);

            if (firstRow) {
                firstRow = false;
                prepareOutputDirectory(Path.of("processed", matterNumber));
            }

            ProcessBase.process(entry);
        }
    }

    // ── Template entry builder ────────────────────────────────────────────────

    /**
     * Builds the {@code List<String>} passed to {@link ProcessBase#process}.
     *
     * <p>Index layout (mirrors {@link ProcessBase}'s index constants):
     * <ol start="0">
     *   <li>template folder</li>
     *   <li>template file name (prefixed with {@code matterNumber_} when dynamic)</li>
     *   <li>template key</li>
     *   <li>matter number</li>
     *   <li>matter name</li>
     *   <li>matter key</li>
     * </ol>
     *
     * @param dynamic        {@code "Y"} if this is a dynamic template
     * @param templateFolder source folder of the template
     * @param templateName   base file name of the template
     * @param templateKey    template key as a string
     * @param matterNumber   matter number string
     * @param matterName     matter name string
     * @param matterKey      matter key as a string
     * @return ordered list ready to pass to {@link ProcessBase#process}
     */
    private static List<String> buildTemplateEntry(String dynamic,
                                                    String templateFolder,
                                                    String templateName,
                                                    String templateKey,
                                                    String matterNumber,
                                                    String matterName,
                                                    String matterKey) {
        var entry = new ArrayList<String>();
        entry.add(templateFolder);
        entry.add("Y".equals(dynamic) ? matterNumber + "_" + templateName : templateName);
        entry.add(templateKey);
        entry.add(matterNumber);
        entry.add(matterName);
        entry.add(matterKey);
        return entry;
    }

    // ── Dynamic template routing ──────────────────────────────────────────────

    /**
     * Routes a dynamic template to the correct {@code Merge_*} class using an
     * enhanced switch expression.
     *
     * @param templateKey the template key identifying the merge handler
     * @param mergeArgs   {@code [matterNumber, matterKey]} passed to the Merge class
     */
    private static void routeDynamicTemplate(int templateKey, String[] mergeArgs) {
        try {
            switch (templateKey) {
                case TEMPLATE_SF_ADVICE_73 -> {
                    if (LoadFile.lTest) LOG.info("Merging SF Advice / Final Subpoena State Court (73)");
                    Merge_73.main(mergeArgs);
                }
                case TEMPLATE_SF_EEOC_77 -> {
                    if (LoadFile.lTest) LOG.info("Merging EEOC Offer of Resolution (77)");
                    Merge_77.main(mergeArgs);
                }
                case TEMPLATE_SF_EEOC_78 -> {
                    if (LoadFile.lTest) LOG.info("Merging EEOC Ltr Applnt Rep Req Auth final (78)");
                    Merge_78.main(mergeArgs);
                }
                case TEMPLATE_SF_MSPB_80 -> {
                    if (LoadFile.lTest) LOG.info("Merging MSPB Ltr Applnt Rep Req Auth final (80)");
                    Merge_80.main(mergeArgs);
                }
                case TEMPLATE_SF_MSPB_84 -> {
                    if (LoadFile.lTest) LOG.info("Merging MSPB Ltr Applnt re refuse release (84)");
                    Merge_84.main(mergeArgs);
                }
                default -> LOG.warning("No merge handler registered for template key: " + templateKey);
            }
        } catch (Exception e) {
            LOG.severe("Error merging template %d: %s".formatted(templateKey, e.getMessage()));
        }
    }

    // ── Output directory preparation ──────────────────────────────────────────

    /**
     * Ensures the output directory is ready to receive processed files.
     *
     * <ul>
     *   <li>If the directory <em>exists</em> it is emptied (existing files deleted).</li>
     *   <li>If it does <em>not</em> exist it is created (including any missing
     *       parent directories).</li>
     * </ul>
     *
     * @param dir the target output directory path
     */
    private static void prepareOutputDirectory(Path dir) {
        try {
            if (Files.exists(dir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                    for (var file : stream) {
                        Files.delete(file);
                        if (LoadFile.lTest) {
                            LOG.info("Deleted existing output file: " + file);
                        }
                    }
                }
            } else {
                Files.createDirectories(dir);
                if (LoadFile.lTest) {
                    LOG.info("Created output directory: " + dir);
                }
            }
        } catch (IOException e) {
            LOG.severe("Error preparing output directory '%s': %s"
                    .formatted(dir, e.getMessage()));
        }
    }
}
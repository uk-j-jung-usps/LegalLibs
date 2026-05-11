package base;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Stream;

/**
 * Batch loader for Legal Libs template processing.
 *
 * <p>This process reads template work items from {@code lawmanager.cmft_base},
 * executes dynamic merges, processes selected templates, zips outputs, sends
 * email, and updates process status.
 */
public class LoadFile {

    private static final Logger LOGGER = Logger.getLogger(LoadFile.class.getName());

    private enum RuntimeOption {
        TEST_MODE(true),
        EMAIL_ENABLED(true);

        private final boolean enabled;

        RuntimeOption(boolean enabled) {
            this.enabled = enabled;
        }

        boolean isEnabled() {
            return enabled;
        }
    }

    private enum ProcessStatus {
        SUCCESS("N"),
        FAILED("F");

        private final String value;

        ProcessStatus(String value) {
            this.value = value;
        }

        String getValue() {
            return value;
        }
    }

    private record TemplateRow(
            String dynamic,
            String templateFolder,
            String templateName,
            String templateKey,
            String matterNumber,
            String matterName,
            String matterKey,
            String emailAddress
    ) {}

    private record DynamicTemplateRow(String matterNumber, String matterKey, int templateKey) {}

    private record MatterProcessResult(
            boolean runProcess,
            String emailAddress,
            String matterNumber,
            String matterName,
            List<String> templateNames
    ) {}

    private static final class LoggerContext implements AutoCloseable {
        private final FileHandler fileHandler;

        private LoggerContext(FileHandler fileHandler) {
            this.fileHandler = fileHandler;
        }

        @Override
        public void close() {
            LOGGER.removeHandler(fileHandler);
            fileHandler.close();
        }
    }

    /**
     * Entry point for the batch process.
     *
     * @param args unused command line arguments
     */
    public static void main(String[] args) {
        try (LoggerContext ignored = initializeLogger()) {
            LOGGER.info("Running Legal Libs");
            if (RuntimeOption.TEST_MODE.isEnabled()) {
                System.out.println("Begin LoadFile");
            }

            var dbConn = new DbConn();
            try (Connection connection = dbConn.getConnection()) {
                processMatters(connection);
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Database connection failed", e);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Unable to initialize logger", e);
        }
    }

    /**
     * Configures file-based logging.
     */
    private static LoggerContext initializeLogger() throws IOException {
        FileHandler fileHandler = new FileHandler("CMFT_Mail.log", true);
        fileHandler.setFormatter(new SimpleFormatter());
        LOGGER.addHandler(fileHandler);
        return new LoggerContext(fileHandler);
    }

    /**
     * Processes all base keys with process status {@code Y}.
     */
    private static void processMatters(Connection connection) throws SQLException {
        for (String baseKey : fetchBaseKeys(connection)) {
            if (RuntimeOption.TEST_MODE.isEnabled()) {
                System.out.println("Matter to process base key: " + baseKey);
            }

            processDynamicTemplates(connection, baseKey);
            MatterProcessResult result = processStaticTemplates(connection, baseKey);
            if (result.runProcess()) {
                zipAndEmail(connection, baseKey, result);
            }
        }
    }

    private static List<String> fetchBaseKeys(Connection connection) throws SQLException {
        List<String> baseKeys = new ArrayList<>();
        String sql = """
                select a.base_key, a.updated_by, a.date_updated
                  from lawmanager.cmft_base a
                 where a.process = 'Y'
                 order by a.date_updated
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                baseKeys.add(resultSet.getString("base_key"));
            }
        }
        return baseKeys;
    }

    /**
     * Processes dynamic templates by dispatching to template-specific merge classes.
     */
    private static void processDynamicTemplates(Connection connection, String baseKey) throws SQLException {
        String sql = """
                select a.base_key, a.updated_by, b.template_key, c.matter_type_key,
                       c.template_folder, c.template_name, d.matter_number, d.matter_name,
                       d.matter_key, f.eaddress
                  from lawmanager.cmft_base a,
                       lawmanager.cmft_selected_templates b,
                       lawmanager.cmft_templates c,
                       lawmanager.matter d,
                       lawmanager.personnel e,
                       lawmanager.eaddress f
                 where a.process = 'Y'
                   and a.base_key = ?
                   and a.matter_key = d.matter_key
                   and a.base_key = b.base_key
                   and b.template_key = c.template_key
                   and c.dynamic in ('Y','M')
                   and a.updated_by = e.personnel_key
                   and e.object_key = f.entity_key
                 order by a.base_key
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, baseKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    DynamicTemplateRow row = new DynamicTemplateRow(
                            resultSet.getString("matter_number"),
                            Integer.toString(resultSet.getInt("matter_key")),
                            resultSet.getInt("template_key")
                    );
                    dispatchDynamicMerge(row);
                }
            }
        }
    }

    private static void dispatchDynamicMerge(DynamicTemplateRow row) {
        if (RuntimeOption.TEST_MODE.isEnabled()) {
            System.out.println("Dynamic Template Found");
            System.out.println(row.matterNumber());
        }

        List<String> mergeArgs = List.of(row.matterNumber(), row.matterKey());
        try {
            switch (row.templateKey()) {
                case 73 -> {
                    if (RuntimeOption.TEST_MODE.isEnabled()) {
                        System.out.println("Merging Dynamic Template");
                    }
                    Merge_73.main(mergeArgs.toArray(String[]::new));
                }
                case 77 -> {
                    if (RuntimeOption.TEST_MODE.isEnabled()) {
                        System.out.println("Merging EEO Office of Resolution Dynamic Template");
                    }
                    Merge_77.main(mergeArgs.toArray(String[]::new));
                }
                case 78 -> {
                    if (RuntimeOption.TEST_MODE.isEnabled()) {
                        System.out.println("EEOC Template Ltr Applnt Rep Req Auth final");
                    }
                    Merge_78.main(mergeArgs.toArray(String[]::new));
                }
                case 80 -> {
                    if (RuntimeOption.TEST_MODE.isEnabled()) {
                        System.out.println("EEOC Template Ltr Applnt Rep Req Auth final");
                    }
                    Merge_80.main(mergeArgs.toArray(String[]::new));
                }
                case 84 -> {
                    if (RuntimeOption.TEST_MODE.isEnabled()) {
                        System.out.println("MSPB Ltr Applnt re refuse release");
                    }
                    Merge_84.main(mergeArgs.toArray(String[]::new));
                }
                default -> {
                    // no-op; only known dynamic template keys are processed
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Dynamic merge failed for template key " + row.templateKey(), e);
        }
    }

    /**
     * Processes selected templates for a base key and returns data needed for zip/email.
     */
    private static MatterProcessResult processStaticTemplates(Connection connection, String baseKey) throws SQLException {
        String sql = """
                select a.base_key, c.dynamic, c.matter_type_key, a.updated_by, b.template_key,
                       c.template_folder, c.template_name, d.matter_number, d.matter_name,
                       d.matter_key, f.eaddress
                  from lawmanager.cmft_base a,
                       lawmanager.cmft_selected_templates b,
                       lawmanager.cmft_templates c,
                       lawmanager.matter d,
                       lawmanager.personnel e,
                       lawmanager.lawmanager.eaddress f
                 where a.process = 'Y'
                   and a.base_key = ?
                   and a.matter_key = d.matter_key
                   and a.base_key = b.base_key
                   and b.template_key = c.template_key
                   and a.updated_by = e.personnel_key
                   and e.object_key = f.entity_key
                 order by a.base_key
                """;

        boolean runProcess = false;
        boolean firstRow = true;
        String emailAddress = "";
        String matterNumber = "";
        String matterName = "";
        List<String> templateNames = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, baseKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    runProcess = true;
                    TemplateRow row = new TemplateRow(
                            resultSet.getString("dynamic"),
                            resultSet.getString("template_folder"),
                            resultSet.getString("template_name"),
                            resultSet.getString("template_key"),
                            resultSet.getString("matter_number"),
                            resultSet.getString("matter_name"),
                            resultSet.getString("matter_key"),
                            resultSet.getString("eaddress")
                    );

                    emailAddress = row.emailAddress();
                    matterNumber = row.matterNumber();
                    matterName = row.matterName();

                    String processedTemplateName = "Y".equals(row.dynamic())
                            ? row.matterNumber() + "_" + row.templateName()
                            : row.templateName();

                    if (RuntimeOption.TEST_MODE.isEnabled()) {
                        System.out.println("Y".equals(row.dynamic()) ? "Dynamic" : "Normal");
                    }

                    templateNames.add(processedTemplateName);

                    if (firstRow) {
                        firstRow = false;
                        prepareMatterDirectory(matterNumber);
                    }

                    List<String> processBaseArgs = new ArrayList<>();
                    processBaseArgs.add(row.templateFolder());
                    processBaseArgs.add(processedTemplateName);
                    processBaseArgs.add(row.templateKey());
                    processBaseArgs.add(row.matterNumber());
                    processBaseArgs.add(row.matterName());
                    processBaseArgs.add(row.matterKey());

                    ProcessBase.process(processBaseArgs);
                }
            }
        }

        return new MatterProcessResult(runProcess, emailAddress, matterNumber, matterName, templateNames);
    }

    /**
     * Ensures a clean output folder exists for the current matter.
     */
    private static void prepareMatterDirectory(String matterNumber) {
        Path directory = Path.of("processed", matterNumber);
        try {
            Files.createDirectories(directory);
            try (Stream<Path> paths = Files.list(directory)) {
                for (Path path : (Iterable<Path>) paths::iterator) {
                    if (RuntimeOption.TEST_MODE.isEnabled()) {
                        System.out.println("|||||||||||||||||File Deleted: " + path + "|||||||||||||||||");
                    }
                    Files.delete(path);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to prepare processed directory for matter " + matterNumber, e);
        }
    }

    /**
     * Creates zip output, sends mail, and updates process status.
     */
    private static void zipAndEmail(Connection connection, String baseKey, MatterProcessResult result) {
        List<String> zipArgs = new ArrayList<>();
        zipArgs.add("processed\\" + result.matterNumber());
        zipArgs.add("processed\\" + result.matterNumber() + ".zip");
        zipArgs.add("LL" + result.matterNumber().substring(0, 6));

        ZipPassFolder.main(zipArgs.toArray(String[]::new), result.templateNames());

        List<String> mailArgs = new ArrayList<>();
        mailArgs.add(result.emailAddress());
        mailArgs.add(result.matterNumber());
        mailArgs.add(result.matterName());
        mailArgs.add(zipArgs.get(1));

        String success = "";
        if (RuntimeOption.EMAIL_ENABLED.isEnabled()) {
            success = SendMail.SendMail(mailArgs.toArray(String[]::new), result.templateNames());
            LOGGER.info("Status of Email: " + success);
        }

        ProcessStatus status = "Failed".equals(success) ? ProcessStatus.FAILED : ProcessStatus.SUCCESS;
        updateProcessStatus(connection, baseKey, status);
        result.templateNames().clear();
    }

    /**
     * Updates {@code cmft_base.process} to N (success) or F (mail failure).
     */
    private static void updateProcessStatus(Connection connection, String baseKey, ProcessStatus status) {
        String sql = """
                update lawmanager.cmft_base
                   set process = ?,
                       date_processed = sysdate,
                       processed_by = 100000
                 where base_key = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.getValue());
            statement.setString(2, baseKey);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to update process status for base key " + baseKey, e);
        }
    }
}

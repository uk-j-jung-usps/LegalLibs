package base;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

/**
 * Creates an AES-256 password-protected ZIP archive from a source folder.
 *
 * <p>Uses Zip4j 2.x API. Upgrade from 1.x requires replacing
 * {@code net.lingala.zip4j.core.ZipFile} with {@code net.lingala.zip4j.ZipFile}
 * and the {@code Zip4jConstants} int-constants with the corresponding enums
 * ({@link CompressionMethod}, {@link CompressionLevel}, {@link EncryptionMethod},
 * {@link AesKeyStrength}).
 *
 * <p>Expected {@code args}:
 * <ol start="0">
 *   <li>Source folder path</li>
 *   <li>Output ZIP file path</li>
 *   <li>ZIP password</li>
 * </ol>
 *
 * <p><b>Security:</b> The password is handled as a {@code char[]} and cleared
 * from memory immediately after the ZIP is created to minimise its lifetime on
 * the heap.
 */
public class ZipPassFolder {

    private static final Logger LOG = Logger.getLogger(ZipPassFolder.class.getName());

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args, List<String> templateNames) {
        if (args.length < 3) {
            throw new IllegalArgumentException(
                    "Usage: NewZipPassFolder <sourceFolder> <outputZipPath> <password>");
        }
        var sourceFolder  = Path.of(args[0]);
        var outputZipPath = Path.of(args[1]);
        var password      = args[2].toCharArray();  // char[] avoids long-lived String on heap

        if (LoadFile.lTest) {
            LOG.info("NewZipPassFolder — source  : " + sourceFolder);
            LOG.info("NewZipPassFolder — output  : " + outputZipPath);
            LOG.info("NewZipPassFolder — password: " + args[2]);   // only logged in test mode
        }

        try {
            zipFolder(sourceFolder, outputZipPath, password);
        } catch (IOException e) {
            LOG.severe("Failed to create ZIP archive: " + e.getMessage());
        } finally {
            java.util.Arrays.fill(password, '\0');  // clear password from memory
        }
    }

    // ── Core zip logic ────────────────────────────────────────────────────────

    /**
     * Compresses the entire {@code sourceFolder} into a password-protected ZIP
     * at {@code outputZipPath}, deleting any pre-existing file at that path first.
     *
     * @param sourceFolder  folder whose contents are to be zipped
     * @param outputZipPath destination ZIP file path
     * @param password      ZIP password (caller is responsible for clearing after use)
     * @throws IOException  if the folder cannot be read or the ZIP cannot be written
     */
    private static void zipFolder(Path sourceFolder, Path outputZipPath, char[] password)
            throws IOException {

        // Validate source folder
        if (!Files.isDirectory(sourceFolder)) {
            throw new IOException("Source is not a directory: " + sourceFolder);
        }

        // Delete any pre-existing ZIP to avoid Zip4j's split-archive restriction
        if (Files.exists(outputZipPath)) {
            Files.delete(outputZipPath);
            if (LoadFile.lTest) {
                LOG.info("Deleted existing ZIP: " + outputZipPath.getFileName());
            }
        }

        // Build compression + encryption parameters
        var parameters = new ZipParameters();
        parameters.setCompressionMethod(CompressionMethod.DEFLATE);
        parameters.setCompressionLevel(CompressionLevel.NORMAL);
        parameters.setEncryptFiles(true);
        parameters.setEncryptionMethod(EncryptionMethod.AES);
        parameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);

        // ZipFile implements Closeable in Zip4j 2.x — use try-with-resources
        try (var zipFile = new ZipFile(outputZipPath.toFile(), password)) {
            zipFile.addFolder(sourceFolder.toFile(), parameters);
            if (LoadFile.lTest) {
                LOG.info("ZIP created successfully: " + outputZipPath);
            }
        }
    }
}

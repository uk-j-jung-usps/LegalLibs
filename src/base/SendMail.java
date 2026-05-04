package base;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Sends a password-protected ZIP archive by email via SMTP with STARTTLS.
 *
 * <p>Expected {@code args} layout:
 * <ol start="0">
 *   <li>Recipient email address</li>
 *   <li>Matter number</li>
 *   <li>Matter name</li>
 *   <li>Path to the ZIP attachment</li>
 * </ol>
 *
 * <p>Returns {@code "Failed"} if all retry attempts are exhausted, otherwise
 * a string describing how many retries were needed (e.g. {@code "Success after 0 loop(s)"}).
 *
 * <p><b>Security note:</b> SMTP credentials are currently hard-coded. Move them
 * to a properties file, environment variable, or secrets manager before deploying
 * to production.
 *
 * <p>Requires JavaMail. Dependency: {@code com.sun.mail:javax.mail:1.6.x}
 */
public class SendMail {

    private static final Logger LOG = Logger.getLogger(SendMail.class.getName());

    // ── SMTP configuration ────────────────────────────────────────────────────
    private static final String SMTP_HOST     = "auth-mailrelay.usps.gov";
    private static final int    SMTP_PORT     = 587;
    private static final String SMTP_USER     = "SMTP_NONPROD_LDIS";
    private static final String SMTP_PASSWORD = "2wsx#EDC4rfv%TGB";  // move to secrets manager
    private static final String FROM_ADDRESS  = "BPJGF0@usps.gov";

    // ── Retry configuration ───────────────────────────────────────────────────
    private static final int      MAX_RETRIES    = 60;
    private static final Duration RETRY_INTERVAL = Duration.ofSeconds(10);

    // ── Email appearance ──────────────────────────────────────────────────────
    private static final String LOGO_PATH       = "LLSmall.gif";
    private static final String LOGO_CONTENT_ID = "the-img-1";

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Builds and sends the email with the processed templates ZIP attached.
     *
     * @param args         [0] to address, [1] matter number, [2] matter name, [3] zip path
     * @param strListTemps list of template file names included in the ZIP
     * @return status string — {@code "Failed"} or {@code "Success after N loop(s)"}
     */
    public static String SendMail(String[] args, List<String> strListTemps) {
        if (args.length < 4) {
            throw new IllegalArgumentException(
                    "SendMail requires 4 args: toAddress, matterNumber, matterName, zipPath");
        }

        var toAddress    = args[0];
        var matterNumber = args[1];
        var matterName   = args[2];
        var zipPath      = args[3];

        var session = buildSession();
        var message = buildMessage(session, toAddress, matterNumber, matterName, zipPath, strListTemps);
        if (message == null) {
            return "Failed";   // message construction failed; error already logged
        }

        return sendWithRetry(message);
    }

    // ── Session ───────────────────────────────────────────────────────────────

    private static Session buildSession() {
        var props = new Properties();
        props.put("mail.smtp.host",              SMTP_HOST);
        props.put("mail.smtp.port",              String.valueOf(SMTP_PORT));
        props.put("mail.smtp.auth",              "true");
        props.put("mail.smtp.starttls.enable",   "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.ssl.protocols",     "TLSv1.2");

        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SMTP_USER, SMTP_PASSWORD);
            }
        });
    }

    // ── Message construction ──────────────────────────────────────────────────

    /**
     * Builds the full MIME message (HTML body + embedded logo + ZIP attachment).
     * Returns {@code null} if construction fails.
     */
    private static MimeMessage buildMessage(Session session, String toAddress,
                                            String matterNumber, String matterName,
                                            String zipPath, List<String> strListTemps) {
        try {
            var msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(FROM_ADDRESS));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddress));
            msg.setSubject(matterNumber + " - Legal Libs Templates Processed");
            msg.setSentDate(new Date());

            // ── Multipart/related: body + embedded image + attachment ────────
            var multipart = new MimeMultipart("related");

            // 1. HTML body
            var bodyPart = new MimeBodyPart();
            bodyPart.setContent(buildHtmlBody(matterNumber, matterName, strListTemps), "text/html");
            multipart.addBodyPart(bodyPart);

            // 2. Embedded logo image
            var logoPart = new MimeBodyPart();
            DataSource logoDs = new FileDataSource(LOGO_PATH);
            logoPart.setDataHandler(new DataHandler(logoDs));
            logoPart.setHeader("Content-ID", "<" + LOGO_CONTENT_ID + ">");
            logoPart.setDisposition(MimeBodyPart.INLINE);
            multipart.addBodyPart(logoPart);

            // 3. ZIP attachment
            var attachPart = new MimeBodyPart();
            DataSource zipDs = new FileDataSource(zipPath);
            attachPart.setDataHandler(new DataHandler(zipDs));
            attachPart.setFileName(zipDs.getName());
            multipart.addBodyPart(attachPart);

            msg.setContent(multipart);
            return msg;

        } catch (MessagingException e) {
            LOG.severe("Failed to build email message: " + e.getMessage());
            return null;
        }
    }

    // ── HTML body ─────────────────────────────────────────────────────────────

    /**
     * Builds the HTML email body with the processed date, matter details,
     * and a bulleted list of template file names.
     */
    private static String buildHtmlBody(String matterNumber, String matterName,
                                        List<String> strListTemps) {
        var date = new SimpleDateFormat("MM/dd/yyyy").format(new Date());

        var sb = new StringBuilder();
        sb.append("""
                <body bgcolor='#DAE8EC'>
                <font style='font-family:Arial, Helvetica, sans-serif' color='#09AFE5' size='+1'>
                <center><img src='cid:the-img-1'/></center>
                <center><i>Templates</i></center>
                </font>
                <font style='font-family:Arial, Helvetica, sans-serif' color='#000000' size='-1'>
                <br><br>Date Processed:&nbsp;&nbsp;
                """);
        sb.append(date);
        sb.append("""
                <br><br>
                The attached password protected zip file contains the following templates
                that have been processed for matter number <b>
                """);
        sb.append(matterNumber);
        sb.append("</b> with the matter name of <b>");
        sb.append(matterName);
        sb.append(":</b><br><br>");

        for (var name : strListTemps) {
            sb.append(name).append("<br>");
        }

        sb.append("</font></body>");
        return sb.toString();
    }

    // ── Send with retry ───────────────────────────────────────────────────────

    /**
     * Attempts to send the message, retrying up to {@link #MAX_RETRIES} times
     * with a {@link #RETRY_INTERVAL} pause between attempts.
     *
     * @param message the fully built MIME message
     * @return {@code "Failed"} or {@code "Success after N loop(s)"}
     */
    private static String sendWithRetry(MimeMessage message) {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                Transport.send(message);
                var status = "Success after %d loop(s)".formatted(attempt);
                LOG.info("Email sent: " + status);
                return status;
            } catch (MessagingException e) {
                LOG.warning("Send attempt %d failed: %s".formatted(attempt + 1, e.getMessage()));

                if (attempt == MAX_RETRIES) {
                    LOG.severe("All %d send attempts exhausted — giving up".formatted(MAX_RETRIES));
                    return "Failed";
                }

                try {
                    Thread.sleep(RETRY_INTERVAL.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();   // restore interrupted status
                    LOG.warning("Retry sleep interrupted — aborting send");
                    return "Failed";
                }
            }
        }
        return "Failed";  // unreachable, but satisfies the compiler
    }
}
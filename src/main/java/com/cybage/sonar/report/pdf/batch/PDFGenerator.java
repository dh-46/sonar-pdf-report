package com.cybage.sonar.report.pdf.batch;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import com.cybage.sonar.report.pdf.entity.LeakPeriodConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.FileSystem;

import com.cybage.sonar.report.pdf.ExecutivePDFReporter;
import com.cybage.sonar.report.pdf.PDFReporter;
import com.cybage.sonar.report.pdf.entity.exception.ReportException;
import com.cybage.sonar.report.pdf.util.Credentials;
import com.itextpdf.text.DocumentException;

/**
 * The type Pdf generator is responsible to configure and launch the PDF report generation.
 */
public class PDFGenerator {

    public static final String SONAR_BASE_URL  = "sonar.base.url";
    public static final String FRONT_PAGE_LOGO = "front.page.logo";
    public static final String DATE_PATTERN    = "yyyy.MM.dd.HH.mm.ss";

    private static final Logger                  LOGGER = LoggerFactory.getLogger(PDFGenerator.class);
    private final        String                  username;
    private final        String                  password;
    private final        String                  reportType;
    private final        String                  projectKey;
    private final        String                  projectVersion;
    private final        List<String>            sonarLanguage;
    private final        Set<String>             otherMetrics;
    private final        Set<String>             typesOfIssue;
    private final        LeakPeriodConfiguration leakPeriod;
    private final        FileSystem              fs;
    private              String                  sonarHostUrl;

    public PDFGenerator(final String projectKey,
                        final String projectVersion,
                        final List<String> sonarLanguage,
                        final Set<String> otherMetrics,
                        final Set<String> typesOfIssue,
                        final LeakPeriodConfiguration leakPeriod,
                        final FileSystem fs,
                        final String sonarHostUrl,
                        final String username,
                        final String password,
                        final String reportType) {
        this.projectKey     = projectKey;
        this.projectVersion = projectVersion;
        this.sonarLanguage  = sonarLanguage;
        this.otherMetrics   = otherMetrics;
        this.typesOfIssue   = typesOfIssue;
        this.leakPeriod     = leakPeriod;
        this.fs             = fs;
        this.sonarHostUrl   = sonarHostUrl;
        this.username       = username;
        this.password       = password;
        this.reportType     = reportType;
    }

    public void execute() {
        Properties config     = new Properties();
        Properties configLang = new Properties();

        try {
            configureAndLaunchReports(config, configLang);


        } catch (IOException e) {
            e.printStackTrace();
        } catch (DocumentException e) {
            LOGGER.error("Problem in generating PDF file.");
            e.printStackTrace();
        } catch (ReportException e) {
            LOGGER.error(e.getMessage());
            e.printStackTrace();
        }
    }

    private void configureAndLaunchReports(Properties config, Properties configLang) throws IOException, ReportException, DocumentException {
        if (sonarHostUrl != null) {
            if (sonarHostUrl.endsWith("/")) {
                sonarHostUrl = sonarHostUrl.substring(0, sonarHostUrl.length() - 1);
            }
            config.put(SONAR_BASE_URL, sonarHostUrl);
            config.put(FRONT_PAGE_LOGO, "sonar.png");
        } else {
            config.load(this.getClass().getResourceAsStream("/report.properties"));
        }
        configLang.load(this.getClass().getResourceAsStream("/report-texts-en.properties"));

        Credentials credentials = new Credentials(config.getProperty(SONAR_BASE_URL), username, password);

        final String                  sonarProjectId      = projectKey;
        String                        sonarProjectVersion = projectVersion;
        final List<String>            sonarLanguage       = this.sonarLanguage;
        final Set<String>             otherMetrics        = this.otherMetrics;
        final Set<String>             typesOfIssue        = this.typesOfIssue;
        final LeakPeriodConfiguration leakPeriod          = this.leakPeriod;

        final SimpleDateFormat sdf = new SimpleDateFormat(DATE_PATTERN);

        final String path = computePdfReportPath(sonarProjectId, sdf);

        PDFReporter reporter = initializeReporter(config, configLang, credentials, sonarProjectId, sonarProjectVersion, sonarLanguage, otherMetrics, typesOfIssue, leakPeriod);

        if (reporter == null) {
            LOGGER.warn("Could not initialize the reporting plugin");
            return;
        }
        writePdfReport(sonarProjectId, sdf, path, reporter);
    }

    private String computePdfReportPath(String sonarProjectId, SimpleDateFormat sdf) {
        return fs.workDir().getAbsolutePath() + "/" + sonarProjectId.replace(':', '-') + "-"
                + sdf.format(new Timestamp(System.currentTimeMillis())) + ".pdf";
    }

    private PDFReporter initializeReporter(Properties config, Properties configLang, Credentials credentials, String sonarProjectId, String sonarProjectVersion, List<String> sonarLanguage, Set<String> otherMetrics, Set<String> typesOfIssue, LeakPeriodConfiguration leakPeriod) {
        ExecutivePDFReporter reporter = null;
        if (reportType != null) {
            if (reportType.equals("pdf")) {
                // LOGGER.info("PDF report type selected");
                reporter = new ExecutivePDFReporter(
                        credentials,
                        this.getClass().getResource("/sonar.png"),
                        sonarProjectId,
                        sonarProjectVersion,
                        sonarLanguage,
                        otherMetrics,
                        typesOfIssue,
                        leakPeriod,
                        config,
                        configLang);
            }
        } else {
            LOGGER.info("No report type provided. Default report type selected (PDF)");
        }
        return reporter;
    }

    private static void writePdfReport(String sonarProjectId, SimpleDateFormat sdf, String path, PDFReporter reporter) throws IOException, ReportException, DocumentException {
        try (ByteArrayOutputStream baos = reporter.getReport();
             FileOutputStream fos = new FileOutputStream(new File(path))) {
            baos.writeTo(fos);
            fos.flush();
            String sonarProjectIdConverted = sonarProjectId.replace(':', '-');
            LOGGER.info("PDF report generated (see {}-{}.pdf on build output directory)", sonarProjectIdConverted, sdf.format(new Timestamp(System.currentTimeMillis())));
        }
    }

}

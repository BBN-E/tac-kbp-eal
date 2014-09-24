package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.bue.common.symbols.SymbolUtils;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.validation.TypeAndRoleValidator;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.codehaus.plexus.archiver.tar.TarUnArchiver;
import org.codehaus.plexus.archiver.zip.AbstractZipUnArchiver;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * This is a wrapper around the validator to make it work nicely for NIST's online
 * submission system. Other users should just us {@link com.bbn.kbp.events2014.bin.ValidateSystemOutput} directly.
 */
public final class NISTValidator {
    private static final Logger log = LoggerFactory.getLogger(NISTValidator.class);
    private static final int ERROR_CODE = 255;
    private static final int MAX_ERRORS = 10;

    public enum Verbosity { VERBOSE, COMPACT}

    private static void usage() {
        log.error("usage: NISTValidator rolesFile docIdToOriginalTextMap [VERBOSE|COMPACT] submissionFile");
        System.exit(ERROR_CODE);
    }

    public static void main(String[] argv) throws IOException {
        if (argv.length != 4) {
            usage();
        }

        final File rolesFile = new File(argv[0]);
        log.info("Loading valid roles from {}", rolesFile);
        if (!rolesFile.exists()) {
            throw new FileNotFoundException(String.format("Roles file not found: %s", rolesFile));
        }

        final File docIdMapFile = new File(argv[1]);
        if (!docIdMapFile.exists() && docIdMapFile.isFile()) {
            throw new FileNotFoundException(String.format(
                    "DocID-->original text mapping not found: %s", docIdMapFile));
        }
        final Map<Symbol, File> docIdMap = FileUtils.loadSymbolToFileMap(docIdMapFile);

        final ValidateSystemOutput validator = ValidateSystemOutput.create(
                TypeAndRoleValidator.create(SymbolUtils.setFrom("Time", "Place"),
                        FileUtils.loadSymbolMultimap(Files.asCharSource(rolesFile, Charsets.UTF_8))));

        Verbosity verbosity = null;

        final String verbosityParam = argv[2];
        try {
            verbosity = Verbosity.valueOf(verbosityParam);
        } catch (Exception e) {
            log.error("Invalid verbosity {}", verbosityParam);
            usage();
        }

        final File submitFile = new File(argv[3]);
        final File workingDirectory = new File(System.getProperty("user.dir"));
        log.info("Got submission file {} and working directory {}", submitFile, workingDirectory);
        if (!workingDirectory.exists()) {
            log.error("Working directory {} does not exist (!?)", workingDirectory);
            System.exit(ERROR_CODE);
        }

        final File errorFile = new File(workingDirectory, submitFile.getName() + ".errlog");
        log.info("Will write errrors to {}", errorFile);

        try {
            if (!submitFile.exists()) {
                throw new FileNotFoundException(String.format(
                        "Submission file %s does not exist", submitFile));
            }

            File uncompressedDirectory = uncompressToTempDirectory(submitFile);
            checkForCommonProblems(uncompressedDirectory);
            log.info("Uncompressed submission to {}", uncompressedDirectory);

            logErrorsAndExit(errorFile, validator.validateOnly(uncompressedDirectory, MAX_ERRORS,
                    docIdMap, AssessmentSpecFormats.Format.KBP2014),  verbosity);
        } catch (Exception e) {
            logErrorsAndExit(errorFile, ImmutableList.of(e), verbosity);
        }
    }

    private static void checkForCommonProblems(File dir) throws IOException {
        for (final File f : dir.listFiles()) {
            if (f.isDirectory()) {
                throw new IOException(String.format("The supplied archive contains a sub-directory %s. This probably means your archive contains was created containing your system output directory. Instead, it should contain the *contents* of your system output directory.",
                        f.getName()));
            }
        }
    }

    private static void logErrorsAndExit(File errorFile, List<? extends Throwable> errors,
                                         Verbosity verbosity) throws IOException {
        final StringBuilder sb = new StringBuilder();
        sb.append("If you get any errors which are difficult to understand, please send the full stack trace to rgabbard@bbn.com for help.\n");
        for (final Throwable error : errors) {
            if (verbosity == Verbosity.VERBOSE) {
                sb.append(Throwables.getStackTraceAsString(error)).append("\n");
            } else if (verbosity == Verbosity.COMPACT) {
                sb.append(error.getMessage());
                Throwable cause = error.getCause();
                while (cause != null) {
                    sb.append("\n\tCaused by: ").append(cause.getMessage());
                    cause = cause.getCause();
                }
                sb.append("\n");
            } else {
                throw new RuntimeException(String.format("Invalid verbosity %s", verbosity));
            }
        }
        final String errorString = sb.toString();

        Files.asCharSink(errorFile, Charsets.UTF_8).write(errorString);

        if (errors.isEmpty()) {
            System.exit(0);
        } else {
            log.error(errorString);
            System.exit(ERROR_CODE);
        }
    }

    private static File uncompressToTempDirectory(File compressedFile) throws IOException {
        checkArgument(compressedFile.exists(), "File to decompress {} does not exist", compressedFile);
        final UnArchiver unarchiver;
        final ConsoleLogger consoleLogger = new ConsoleLogger( org.codehaus.plexus.logging.Logger.LEVEL_INFO, "console" );
        if (compressedFile.getAbsolutePath().endsWith(".tgz")
                || compressedFile.getAbsolutePath().endsWith(".tar.gz"))
        {
            unarchiver = new TarGZipUnArchiver();
            ((TarUnArchiver)unarchiver).enableLogging(consoleLogger);
        } else if (compressedFile.getAbsolutePath().endsWith(".zip")) {
            unarchiver = new ZipUnArchiver();
            ((AbstractZipUnArchiver)unarchiver).enableLogging(consoleLogger);
        } else {
            throw new RuntimeException(String.format("Compressed file must end in .zip, .tgz, or .tar.gz, but path is %s", compressedFile));
        }

        unarchiver.setSourceFile(compressedFile);

        final File destDir = Files.createTempDir();
        // the contents of the temp directory
        // will not be deleted on exit, because this
        // is actually quite tricky in Java.  But the size
        // of this data should not be so large as to
        // make this a problem
        unarchiver.setDestDirectory(destDir);
        unarchiver.extract();
        return destDir;
    }
}

package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public final class ImportSystemOutputToAnnotationStore {
    private static final Logger log =
            LoggerFactory.getLogger(ImportSystemOutputToAnnotationStore.class);

    public static void main(final String[] argv) {
        try {
            trueMain(argv);
        } catch (Exception e) {
            // ensure non-zero return on failure
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static final void usage() {
        log.error("\nusage: importSystemOutputToAnnotationStore paramFile\n" +
        "Parameters are:\n"+
                "\tsystemOutput: system output store to import\n" +
                "\tannotationStore: destination annotation store (existing or new)"
        );
    }

    private static void trueMain(final String[] argv) throws IOException {
        final File systemOutputDir;
        final File annStoreDir;

        if (argv.length == 1) {
            final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
            log.info(params.dump());

            importSystemOutputToAnnotationStore(params.getExistingDirectory("systemOutput"),
                    params.getCreatableDirectory("annotationStore"));
        } else {
            usage();
        }
    }

    private static void importSystemOutputToAnnotationStore(File systemOutputDir, File annStoreDir) throws IOException {
        log.info("Loading system output from {}", systemOutputDir);
        final SystemOutputStore systemOutput = AssessmentSpecFormats.openSystemOutputStore(
                systemOutputDir);


        log.info("Using assessment store at {}", annStoreDir);
        final AnnotationStore annStore = AssessmentSpecFormats.openOrCreateAnnotationStore(
                annStoreDir);

        for (final Symbol docid : systemOutput.docIDs()) {
            log.info("Pushing responses for {} into assessment store", docid);
            final SystemOutput docOutput = systemOutput.read(docid);
            final AnswerKey currentAnnotation = annStore.readOrEmpty(docid);
            final AnswerKey newAnswerKey = currentAnnotation.copyAddingPossiblyUnannotated(
                    docOutput.responses());
            annStore.write(newAnswerKey);
        }
    }
}

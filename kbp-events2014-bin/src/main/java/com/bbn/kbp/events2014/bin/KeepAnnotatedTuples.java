package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public final class KeepAnnotatedTuples {
    private static final Logger log = LoggerFactory.getLogger(KeepAnnotatedTuples.class);

    private KeepAnnotatedTuples() {
        throw new UnsupportedOperationException();
    }

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
        log.error("\nusage: KeepAnnotatedTuples paramFile\n" +
        "Parameters are:\n"+
                "\tinputAnnotationStore: annotation store to read in\n" +
                "\toutputAnnotationStore: annotation store to write out to\n"
        );
    }

    private static void trueMain(final String[] argv) throws IOException {
        if (argv.length == 1) {
            final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
            log.info(params.dump());

            keepAnnotatedTuples(params.getExistingDirectory("inputAnnotationStore"),
                    params.getCreatableDirectory("outputAnnotationStore"),
                    params.getEnum("fileFormat", AssessmentSpecFormats.Format.class));
        } else {
            usage();
        }
    }

    private static void keepAnnotatedTuples(File inputDir, File outputDir, AssessmentSpecFormats.Format fileFormat)
            throws IOException
    {
        final AnnotationStore inputAssessmentStore = AssessmentSpecFormats.openAnnotationStore(inputDir, fileFormat);
        final AnnotationStore outputAssessmentStore = AssessmentSpecFormats.openOrCreateAnnotationStore(outputDir, fileFormat);

        for (final Symbol docid: inputAssessmentStore.docIDs()) {
            final AnswerKey answerKey = inputAssessmentStore.read(docid);
            outputAssessmentStore.write(AnswerKey.from(docid, answerKey.annotatedResponses(), ImmutableSet.<Response>of(),
                    answerKey.corefAnnotation()));
        }
    }
}

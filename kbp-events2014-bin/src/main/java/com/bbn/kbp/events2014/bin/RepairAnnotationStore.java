package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.assessmentCreators.RecoveryAssessmentCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The pilot assessment annotation store does not entirely meet the specification.  This program will
 * transform such an annotation store to a compliant one, at the cost of throwing away some malformed
 * response annotations.
 */
public final class RepairAnnotationStore {
    private static final Logger log = LoggerFactory.getLogger(RepairAnnotationStore.class);
    private RepairAnnotationStore() {
        throw new UnsupportedOperationException();
    }

    private static void trueMain(String[] argv) throws IOException {
        if (argv.length != 1) {
            usage();
        }
        final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
        log.info(params.dump());

        final File potentiallyBrokenStore = params.getExistingDirectory("brokenStore");
        final File outputStorePath = params.getCreatableDirectory("pathToWriteFixedStore");

        log.info("Source annotation store: {}", potentiallyBrokenStore);
        log.info("Target annotation store: {}", outputStorePath);

        final Random rng = new Random(params.getInteger("randomSeed"));
        final RecoveryAssessmentCreator assessmentCreator = RecoveryAssessmentCreator.createWithRandomSeed(rng);
        final AnnotationStore annStore = AssessmentSpecFormats.recoverPossiblyBrokenAnnotationStore(
                potentiallyBrokenStore, assessmentCreator);
        final AnnotationStore outStore = AssessmentSpecFormats.createAnnotationStore(outputStorePath);

        for (final Symbol docid : annStore.docIDs()) {
            outStore.write(annStore.read(docid));
        }
        log.info(assessmentCreator.report());
    }

    private static void usage() {
        System.err.println("usage: RepairAnnotationStore paramFile\n" +
                "Parameters are: \n"
                + "brokenStore: the broken store to fix\n"
                + "pathToWriteFixedStore: pathToWriteFixedStore");
        System.exit(1);
    }

    public static void main(String[] argv) {
        try {
            trueMain(argv);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}

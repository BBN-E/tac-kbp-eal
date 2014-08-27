package com.bbn.kbp.events2014.transformers;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Merges two answer keys together. By default, conflicts are resolved by taking
 * the assessment in the baseline answer key.  For coref, if a CAS is present in baseline,
 * the baseline coref cluster is used. If not, whatever "baseline cluster" contains the most
 * CASes from its cluster in "additional" in used. If none exists, a new cluster is created.
 * Tie breaking is undefined but deterministic.
 *
 */
public final  class MergeAssessmentStores {
    private static Logger log = LoggerFactory.getLogger(MergeAssessmentStores.class);
    private MergeAssessmentStores() {}

    private int numAdditionalMerged = 0;

    public static MergeAssessmentStores create() {
        return new MergeAssessmentStores();
    }

    public void logReport() {
        log.info("Merged in a total of {} new assessments", numAdditionalMerged);
    }

    /**
     * The two answer keys must match in document ID or an {@link java.lang.IllegalArgumentException}
     * will be thrown.
     *
     * @param baseline
     * @param additional
     * @return
     */
    public AnswerKey mergeAnswerKeys(AnswerKey baseline, AnswerKey additional) {
        checkArgument(baseline.docId() == additional.docId());
        log.info("For {}, merging an answer key with {} assessed and {} unassessed into a"
            + " baseline with {} assessed and {} unassessed", baseline.docId(),
                additional.annotatedResponses().size(), additional.unannotatedResponses().size(),
                baseline.annotatedResponses().size(), baseline.unannotatedResponses().size());

        return baseline.copyMerging(additional);
    }



    private static void usage() {
        System.err.println("usage: MergeAssessmentStores <paramFile>\n"+
                "Where parameters are:\n"+
                "\tbaseline: the assessment store to merge into. Has priority in conflicts.\n"+
                "\tadditional: the new assessments to merge in.\n"+
                "\toutput: where to write the merged store\n");
        System.exit(1);
    }

    private static void trueMain(String[] argv) throws IOException {
        if (argv.length != 1) {
            usage();
        }

        final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
        log.info(params.dump());

        final File baselinePath = params.getExistingDirectory("baseline");
        final File additionalPath = params.getExistingDirectory("additional");
        final File outputPath = params.getCreatableDirectory("output");

        log.info("Baseline assessment store: {}", baselinePath);
        log.info("Assessment store to be merged in: {}", additionalPath);
        log.info("Merged store will be written to: {}", outputPath);

        final MergeAssessmentStores merger = MergeAssessmentStores.create();

        final AnnotationStore baseline = AssessmentSpecFormats.openAnnotationStore(baselinePath);
        final AnnotationStore additional = AssessmentSpecFormats.openAnnotationStore(additionalPath);
        final AnnotationStore outStore = AssessmentSpecFormats.createAnnotationStore(outputPath);

        for (final Symbol docid : Sets.union(baseline.docIDs(), additional.docIDs())) {
            outStore.write(
                    merger.mergeAnswerKeys(
                            baseline.readOrEmpty(docid),
                            additional.readOrEmpty(docid)));
        }
        baseline.close();
        additional.close();
        outStore.close();
        merger.logReport();
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

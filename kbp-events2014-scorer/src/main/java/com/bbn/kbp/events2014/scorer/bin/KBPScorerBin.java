package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.kbp.events2014.scorer.KBPScorer;
import com.bbn.kbp.events2014.scorer.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.scorer.observers.*;
import com.bbn.kbp.events2014.scorer.observers.errorloggers.HTMLErrorRecorder;
import com.bbn.kbp.events2014.scorer.observers.errorloggers.NullHTMLErrorRecorder;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

public final class KBPScorerBin {
    private static Logger log = LoggerFactory.getLogger(KBPScorerBin.class);

    private KBPScorerBin() {
        throw new UnsupportedOperationException();
    }

    private static void usage() {
        log.warn("usage: kbpScorer param_file\n" +
            "parameters are:\n" +
            "\tannotationComplete: whether to require answer key to have no unannotated items. 'true' for evaluation.\n"+
            "\tscoringOutput: directory to write scoring observer logs to\n"+
            "\tsystemOutput: system output store to score\n"+
            "\tanswerKey: annotation store to score against");
        System.exit(1);
    }

    private static List<KBPScoringObserver<TypeRoleFillerRealis>> getCorpusObservers(Parameters params) {
        final List<KBPScoringObserver<TypeRoleFillerRealis>> corpusObservers = Lists.newArrayList();

        if (params.getBoolean("annotationComplete")) {
            corpusObservers.add(ExitOnUnannotatedAnswerKey.<TypeRoleFillerRealis>create());
        }


        final HTMLErrorRecorder dummyRecorder = NullHTMLErrorRecorder.getInstance();
        // Lax scorer
        corpusObservers.add(BuildConfusionMatrix.<TypeRoleFillerRealis>forAnswerFunctions(
                "Lax", KBPScorer.IsPresent, KBPScorer.AnyAnswerSemanticallyCorrect));
        corpusObservers.add(StrictStandardScoringObserver.strictScorer(dummyRecorder));
        corpusObservers.add(StrictStandardScoringObserver.standardScorer(dummyRecorder));
        corpusObservers.add(SkipIncompleteAnnotations.wrap(StrictStandardScoringObserver.strictScorer(dummyRecorder)));
        corpusObservers.add(SkipIncompleteAnnotations.wrap(StrictStandardScoringObserver.standardScorer(dummyRecorder)));
        return corpusObservers;
    }

    public static void main(final String[] argv) throws IOException {
        if (argv.length != 1) {
            usage();
        }
        final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
        log.info(params.dump());
        final KBPScorer scorer = KBPScorer.createFrom(params, getCorpusObservers(params));
        scorer.run();
    }

}

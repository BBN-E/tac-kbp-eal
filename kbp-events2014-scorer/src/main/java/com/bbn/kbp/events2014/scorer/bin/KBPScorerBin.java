package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.events2014.scorer.KBPScorer;
import com.bbn.kbp.events2014.scorer.observers.BuildConfusionMatrix;
import com.bbn.kbp.events2014.scorer.observers.ExitOnUnannotatedAnswerKey;
import com.bbn.kbp.events2014.scorer.observers.KBPScoringObserver;
import com.bbn.kbp.events2014.scorer.observers.StrictStandardScoringObserver;
import com.bbn.kbp.events2014.scorer.observers.errorloggers.HTMLErrorRecorder;
import com.bbn.kbp.events2014.scorer.observers.errorloggers.NullHTMLErrorRecorder;
import com.bbn.kbp.events2014.transformers.MakeAllRealisActual;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Sets.union;

public final class KBPScorerBin {
    private static final Logger log = LoggerFactory.getLogger(KBPScorerBin.class);

    private KBPScorerBin() {
        throw new UnsupportedOperationException();
    }

    private static void usage() {
        log.warn("usage: kbpScorer param_file\n" +
            "parameters are:\n" +
            "\tanswerKey: annotation store to score against\n" +
            "\tneutralizeRealis: whther to change all realises in both system output and answer key to Actual\n" +
            "If running on a single output store:\n" +
                "\tscoringOutput: directory to write scoring observer logs to\n"+
                "\tsystemOutput: system output store to score\n"+
                "\tdocumentsToScore: (optional) file listing which documents to score. If not present, scores all in either store."+
            "If running on multiple stores:\n" +
                "\tscoringOutputRoot: directory to write scoring observer logs to. A subdirectory will be created for each input store.\n"+
                "\tsystemOutputsDir: each subdirectory of this is expected to be a system output store to score\n"+
                "\tdocumentsToScore: file listing which documents to score."
        );
        System.exit(1);
    }

    private static KBPScorer.ScoringConfiguration getScoringConfiguration(Parameters params) {
        final List<KBPScoringObserver<TypeRoleFillerRealis>> scoringObservers = getCorpusObservers(params);

        final List<Function<AnswerKey, AnswerKey>> answerKeyTransformations = Lists.newArrayList();
        final List<Function<SystemOutput, SystemOutput>> systemOutputTransformations = Lists.newArrayList();

        if (params.getBoolean("neutralizeRealis")) {
            answerKeyTransformations.add(MakeAllRealisActual.forAnswerKey());
            systemOutputTransformations.add(MakeAllRealisActual.forSystemOutput());
        }

        return KBPScorer.ScoringConfiguration.create(answerKeyTransformations,
                systemOutputTransformations, scoringObservers);
    }

    private static List<KBPScoringObserver<TypeRoleFillerRealis>> getCorpusObservers(Parameters params) {
        final List<KBPScoringObserver<TypeRoleFillerRealis>> corpusObservers = Lists.newArrayList();

        if (params.getBoolean("annotationComplete")) {
            corpusObservers.add(ExitOnUnannotatedAnswerKey.<TypeRoleFillerRealis>create());
        }



        final HTMLErrorRecorder dummyRecorder = NullHTMLErrorRecorder.getInstance();
        final ImmutableList<StrictStandardScoringObserver.Outputter> outputters = getOutputters(params);
        // Lax scorer
        corpusObservers.add(BuildConfusionMatrix.<TypeRoleFillerRealis>forAnswerFunctions(
                "Lax", KBPScorer.IsPresent, KBPScorer.AnyAnswerSemanticallyCorrect));
        corpusObservers.add(StrictStandardScoringObserver.strictScorer(dummyRecorder, outputters));
        corpusObservers.add(StrictStandardScoringObserver.standardScorer(dummyRecorder, outputters));

        return corpusObservers;
    }

    private static final String NUM_BOOTSTRAP_SAMPLES = "numBootstrapSamples";
    // package-private so BBN's internal scorer can borrow it
    static ImmutableList<StrictStandardScoringObserver.Outputter> getOutputters(Parameters params) {
        final ImmutableList.Builder<StrictStandardScoringObserver.Outputter> ret = ImmutableList.builder();
        ret.add(StrictStandardScoringObserver.createStandardOutputter());
        if (params.isPresent(NUM_BOOTSTRAP_SAMPLES)) {
            final int bootstrapSeed = params.getInteger("bootstrapSeed");
            final int numBootstrapSamples = params.getPositiveInteger("numBootstrapSamples");
            ret.add(StrictStandardScoringObserver.createBootstrapOutputter(bootstrapSeed, numBootstrapSamples));
        }
        return ret.build();
    }

    public static final String SYSTEM_OUTPUT_PARAM = "systemOutput";
    public static final String SYSTEM_OUTPUTS_DIR_PARAM = "systemOutputsDir";

    public static void main(final String[] argv) throws IOException {
        if (argv.length != 1) {
            usage();
        }
        final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
        log.info(params.dump());

        final KBPScorer scorer = KBPScorer.create();
        final AnnotationStore goldAnswerStore = AssessmentSpecFormats.openAnnotationStore(params
                .getExistingDirectory("answerKey"), params.getEnum("goldFileFormat", AssessmentSpecFormats.Format.class));

        checkArgument(params.isPresent(SYSTEM_OUTPUT_PARAM) != params.isPresent(SYSTEM_OUTPUTS_DIR_PARAM),
                "Exactly one of systemOutput and systemOutputsDir must be specified");
        if (params.isPresent(SYSTEM_OUTPUT_PARAM)) {
            scoreSingleSystemOutputStore(goldAnswerStore, scorer, params);
        } else if (params.isPresent("systemOutputsDir")) {
            scoreMultipleSystemOutputStores(goldAnswerStore, scorer, params);
        } else {
            throw new RuntimeException("Can't happen");
        }
    }

    // this and the single output store version can't be easily refactored together because
    // of their differing behavior wrt the list of documents to score
    private static void scoreMultipleSystemOutputStores(AnnotationStore goldAnswerStore, KBPScorer scorer, Parameters params) throws IOException {
        final File systemOutputsDir = params.getExistingDirectory("systemOutputsDir");
        final File scoringOutputRoot = params.getCreatableDirectory("scoringOutputRoot");

        log.info("Scoring all subdirectories of {}", systemOutputsDir);

        final Set<Symbol> documentsToScore = loadDocumentsToScore(params);
        final AssessmentSpecFormats.Format fileFormat = params.getEnum("systemFormat", AssessmentSpecFormats.Format.class);

        for (File subDir : systemOutputsDir.listFiles()) {
            if (subDir.isDirectory()) {
                final SystemOutputStore systemOutputStore = AssessmentSpecFormats.openSystemOutputStore(subDir, fileFormat);
                final File outputDir = new File(scoringOutputRoot, subDir.getName());

                outputDir.mkdirs();
                scorer.run(systemOutputStore, goldAnswerStore, documentsToScore,
                        getScoringConfiguration(params), outputDir);
            }
        }
    }

    private static void scoreSingleSystemOutputStore(AnnotationStore goldAnswerStore, KBPScorer scorer, Parameters params) throws IOException {
        final File systemOutputDir = params.getExistingDirectory(SYSTEM_OUTPUT_PARAM);
        final AssessmentSpecFormats.Format systemFormat = params.getEnum("systemFormat", AssessmentSpecFormats.Format.class);
        log.info("Scoring single system output {}", systemOutputDir);
        final SystemOutputStore systemOutputStore = AssessmentSpecFormats.openSystemOutputStore(systemOutputDir, systemFormat);
        final Set<Symbol> documentsToScore;
        if (params.isPresent("documentsToScore")) {
            documentsToScore = loadDocumentsToScore(params);
        } else {
            documentsToScore = union(systemOutputStore.docIDs(), goldAnswerStore.docIDs());
        }

        scorer.run(systemOutputStore, goldAnswerStore, documentsToScore,
                getScoringConfiguration(params),
                params.getCreatableDirectory("scoringOutput"));
    }

    private static ImmutableSet<Symbol> loadDocumentsToScore(Parameters params) throws IOException {
        final File docsToScoreList = params.getExistingFile("documentsToScore");
        final ImmutableSet<Symbol> ret = ImmutableSet.copyOf(FileUtils.loadSymbolList(docsToScoreList));
        log.info("Scoring over {} documents specified in {}", ret.size(), docsToScoreList);
        return ret;
    }

}

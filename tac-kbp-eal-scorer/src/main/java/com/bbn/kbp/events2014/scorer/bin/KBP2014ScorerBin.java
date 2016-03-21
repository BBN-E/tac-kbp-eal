package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.ArgumentStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.scorer.EventArgumentScorer;
import com.bbn.kbp.events2014.scorer.EventArgumentScorerBin;
import com.bbn.kbp.events2014.scorer.observers.EAScoringObserver;
import com.bbn.kbp.events2014.scorer.observers.ExitOnUnannotatedAnswerKey;
import com.bbn.kbp.events2014.scorer.observers.KBPScoringObserver;
import com.bbn.kbp.events2014.scorer.observers.errorloggers.HTMLErrorRecorder;
import com.bbn.kbp.events2014.scorer.observers.errorloggers.NullHTMLErrorRecorder;

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

public final class KBP2014ScorerBin {
  private static final Logger log = LoggerFactory.getLogger(KBP2014ScorerBin.class);

  private final ImmutableList<KBPScoringObserver<TypeRoleFillerRealis>> corpusObservers;

  /* package-private */ KBP2014ScorerBin(
      final Iterable<KBPScoringObserver<TypeRoleFillerRealis>> corpusObservers) {
    this.corpusObservers = ImmutableList.copyOf(corpusObservers);
  }

  private static void usage() {
    log.warn("usage: kbpScorer param_file\n" +
            "parameters are:\n" +
            "\tanswerKey: annotation store to score against\n" +
            "\tneutralizeRealis: whther to change all realises in both system output and answer key to Actual\n"
            +
            "If running on a single output store:\n" +
            "\tscoringOutput: directory to write scoring observer logs to\n" +
            "\tsystemOutput: system output store to score\n" +
            "\tdocumentsToScore: (optional) file listing which documents to score. If not present, scores all in either store."
            +
            "If running on multiple stores:\n" +
            "\tscoringOutputRoot: directory to write scoring observer logs to. A subdirectory will be created for each input store.\n"
            +
            "\tsystemOutputsDir: each subdirectory of this is expected to be a system output store to score\n"
            +
            "\tdocumentsToScore: file listing which documents to score."
    );
    System.exit(1);
  }

  public static final String SYSTEM_OUTPUT_PARAM = "systemOutput";
  public static final String SYSTEM_OUTPUTS_DIR_PARAM = "systemOutputsDir";

  public void runOnParameters(Parameters params) throws IOException {
    log.info(params.dump());

    log.info("Creating event argument scorer..");
    final EventArgumentScorer
        innerScorer = EventArgumentScorer.create(Preprocessors.for2014FromParameters(params),
        corpusObservers);
    final EventArgumentScorerBin scorer = new EventArgumentScorerBin(innerScorer, corpusObservers);
    final AnnotationStore goldAnswerStore = AssessmentSpecFormats.openAnnotationStore(params
            .getExistingDirectory("answerKey"),
        params.getEnum("goldFileFormat", AssessmentSpecFormats.Format.class));

    checkArgument(
        params.isPresent(SYSTEM_OUTPUT_PARAM) != params.isPresent(SYSTEM_OUTPUTS_DIR_PARAM),
        "Exactly one of systemOutput and systemOutputsDir must be specified");
    final AssessmentSpecFormats.Format systemFileFormat =
        params.getEnum("systemFormat", AssessmentSpecFormats.Format.class);

    final ImmutableSet<Symbol> docsToScore = loadDocumentsToScore(params);

    if (params.isPresent(SYSTEM_OUTPUT_PARAM)) {
      scoreSingleSystemOutputStore(goldAnswerStore, scorer, params, systemFileFormat, docsToScore);
    } else if (params.isPresent("systemOutputsDir")) {
      scoreMultipleSystemOutputStores(goldAnswerStore, scorer, params, systemFileFormat,
          docsToScore);
    } else {
      throw new RuntimeException("Can't happen");
    }

    innerScorer.logStats();
  }

  // this and the single output store version can't be easily refactored together because
  // of their differing behavior wrt the list of documents to score
  private void scoreMultipleSystemOutputStores(AnnotationStore goldAnswerStore,
      EventArgumentScorerBin scorer, Parameters params, final AssessmentSpecFormats.Format fileFormat,
      final Set<Symbol> docsToScore)
      throws IOException {
    final File systemOutputsDir = params.getExistingDirectory("systemOutputsDir");
    final File scoringOutputRoot = params.getCreatableDirectory("scoringOutputRoot");

    log.info("Scoring all subdirectories of {}", systemOutputsDir);

    for (File subDir : systemOutputsDir.listFiles()) {
      if (subDir.isDirectory()) {
        final ArgumentStore argumentStore =
            AssessmentSpecFormats.openSystemOutputStore(subDir, fileFormat);
        final File outputDir = new File(scoringOutputRoot, subDir.getName());

        outputDir.mkdirs();
        scorer.run(argumentStore, goldAnswerStore, docsToScore, outputDir);
      }
    }
  }

  private void scoreSingleSystemOutputStore(AnnotationStore goldAnswerStore,
      EventArgumentScorerBin scorer, Parameters params, final AssessmentSpecFormats.Format systemFormat,
      final Set<Symbol> docsToScore)
      throws IOException {
    final File systemOutputDir = params.getExistingDirectory(SYSTEM_OUTPUT_PARAM);
    log.info("Scoring single system output {}", systemOutputDir);
    final ArgumentStore argumentStore =
        AssessmentSpecFormats.openSystemOutputStore(systemOutputDir, systemFormat);

    scorer.run(argumentStore, goldAnswerStore, docsToScore,
        params.getCreatableDirectory("scoringOutput"));
  }

  private static ImmutableSet<Symbol> loadDocumentsToScore(Parameters params) throws IOException {
    final File docsToScoreList = params.getExistingFile("documentsToScore");
    final ImmutableSet<Symbol> ret = ImmutableSet.copyOf(FileUtils.loadSymbolList(docsToScoreList));
    log.info("Scoring over {} documents specified in {}", ret.size(), docsToScoreList);
    return ret;
  }

  private static final String NUM_BOOTSTRAP_SAMPLES = "numBootstrapSamples";

  // package-private so BBN's internal scorer can borrow it
  static ImmutableList<EAScoringObserver.Outputter> getOutputters(Parameters params) {
    final ImmutableList.Builder<EAScoringObserver.Outputter> ret =
        ImmutableList.builder();
    ret.add(EAScoringObserver.createStandardOutputter());
    ret.add(EAScoringObserver.createEquivalenceClassIDOutputter());
    if (params.isPresent(NUM_BOOTSTRAP_SAMPLES)) {
      final int bootstrapSeed = params.getInteger("bootstrapSeed");
      final int numBootstrapSamples = params.getPositiveInteger("numBootstrapSamples");
      ret.add(EAScoringObserver
          .createBootstrapOutputter(bootstrapSeed, numBootstrapSamples));
    }
    return ret.build();
  }

  // Below is configuration related to the standard public version of the scorer

  private static ImmutableList<KBPScoringObserver<TypeRoleFillerRealis>> getCorpusObservers(
      Parameters params) {
    final List<KBPScoringObserver<TypeRoleFillerRealis>> corpusObservers = Lists.newArrayList();

    if (params.getBoolean("annotationComplete")) {
      corpusObservers.add(ExitOnUnannotatedAnswerKey.create());
    }

    final HTMLErrorRecorder dummyRecorder = NullHTMLErrorRecorder.getInstance();
    final ImmutableList<EAScoringObserver.Outputter> outputters = getOutputters(params);
    corpusObservers.add(EAScoringObserver.standardScorer(dummyRecorder, outputters));

    return ImmutableList.copyOf(corpusObservers);
  }

  public static void main(final String[] argv) throws IOException {
    if (argv.length != 1) {
      usage();
    }
    final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
    final ImmutableList<KBPScoringObserver<TypeRoleFillerRealis>> corpusObservers =
        getCorpusObservers(params);

    final KBP2014ScorerBin scorerBin = new KBP2014ScorerBin(corpusObservers);
    scorerBin.runOnParameters(params);
  }
}

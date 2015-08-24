package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ScoringData;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.ArgumentStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.LinkingSpecFormats;
import com.bbn.kbp.events2014.io.LinkingStore;
import com.bbn.kbp.events2014.linking.SameEventTypeLinker;
import com.bbn.kbp.linking.EALScorer2015Style;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class KBP2015Scorer {
  private static final Logger log = LoggerFactory.getLogger(KBP2015Scorer.class);

  private KBP2015Scorer(final EALScorer2015Style documentScorer,
      Map<String, SimpleResultWriter> additionalResultWriters) {
    this.documentScorer = checkNotNull(documentScorer);
    this.additionalResultWriters = ImmutableMap.copyOf(additionalResultWriters);
  }

  public static KBP2015Scorer fromParameters(Parameters params) {
    return new KBP2015Scorer(EALScorer2015Style.create(params),
        ImmutableMap.<String, SimpleResultWriter>of());
  }

  public static KBP2015Scorer fromParameters(Parameters params,
      Map<String, SimpleResultWriter> additionalResultWriters) {
    return new KBP2015Scorer(EALScorer2015Style.create(params), additionalResultWriters);
  }

  private static void usage() {
    log.warn("usage: KBP2015Scorer param_file\n" +
            "parameters are:\n" +
            "\tanswerKey: argument annotation store to score against\n" +
            "\treferenceLinking: linking store to score against\n" +
            "\tdocumentsToScore: (optional) file listing which documents to score.\n" +
            "\nIf running on a single output store:\n" +
            "\tscoringOutput: directory to write scoring observer logs to\n" +
            "\tsystemOutput: system output to score.\n" +
            "\nIf running on multiple stores:\n" +
            "\tscoringOutputRoot: directory to write scoring observer logs to. A subdirectory will be created for each input store.\n"
            +
            "\tsystemOutputsDir: each subdirectory of this is expected to be a system's output to score\n" +
            "\n\nEach system's output directory should have two sub-directories. \"arguments\" " +
            " and \"linking\""

    );
    System.exit(1);
  }

  public static void main(String[] argv) {
    // we wrap the main method in this way to
    // ensure a non-zero return value on failure
    try {
      trueMain(argv, ImmutableMap.<String, SimpleResultWriter>of());
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  public static void trueMain(String[] argv,
      Map<String, SimpleResultWriter> additionalResultWriters)
      throws IOException {
    if (argv.length != 1) {
      usage();
      System.exit(1);
    }
    final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
    log.info(params.dump());
    final KBP2015Scorer scorer = KBP2015Scorer.fromParameters(params, additionalResultWriters);

    final AnnotationStore goldAnswerStore = AssessmentSpecFormats.openAnnotationStore(params
            .getExistingDirectory("answerKey"), AssessmentSpecFormats.Format.KBP2015);
    final Set<Symbol> docsToScore = loadDocumentsToScore(params);
    final LinkingStore referenceLinkingStore = getReferenceLinkingStore(goldAnswerStore, params);

    checkArgument(
        params.isPresent(SYSTEM_OUTPUT_PARAM) != params.isPresent(SYSTEM_OUTPUTS_DIR_PARAM),
        "Exactly one of systemOutput and systemOutputsDir must be specified");
    if (params.isPresent(SYSTEM_OUTPUT_PARAM)) {
      final File scoringOutputDir = params.getCreatableDirectory("scoringOutputDir");
      final File systemOutputDir = params.getExistingDirectory(SYSTEM_OUTPUT_PARAM);
      log.info("Scoring single system output {}", systemOutputDir);
      final ArgumentStore argumentStore = getSystemOutputStore(params, systemOutputDir);
      final LinkingStore systemLinkingStore =
          getLinkingStore(params, systemOutputDir, argumentStore);

      scorer.score(goldAnswerStore, referenceLinkingStore, argumentStore, systemLinkingStore,
          docsToScore, scoringOutputDir);
    } else {
      final File systemOutputsDir = params.getExistingDirectory("systemOutputsDir");
      final File scoringOutputRoot = params.getCreatableDirectory("scoringOutputRoot");

      log.info("Scoring all subdirectories of {}", systemOutputsDir);

      for (File subDir : systemOutputsDir.listFiles()) {
        try {
          if (subDir.isDirectory()) {
            log.info("Scoring system {}", subDir);
            final File outputDir = new File(scoringOutputRoot, subDir.getName());
            outputDir.mkdirs();
            final ArgumentStore argumentStore = getSystemOutputStore(params, subDir);
            final LinkingStore systemLinkingStore =
                getLinkingStore(params, subDir, argumentStore);

            scorer.score(goldAnswerStore, referenceLinkingStore, argumentStore,
                systemLinkingStore,
                docsToScore, outputDir);
          }
        } catch (Exception e) {
          throw new RuntimeException("Exception while processing " + subDir, e);
        }
      }
    }
  }

  private static LinkingStore getReferenceLinkingStore(AnnotationStore annStore,
      final Parameters params)
      throws IOException {
    if (useDefaultLinkingHack(params)) {
      return SameEventTypeLinker.create(
          ImmutableSet.of(KBPRealis.Actual, KBPRealis.Other)).wrap(annStore.docIDs());
    } else {
      return LinkingSpecFormats.openOrCreateLinkingStore(
          params.getExistingDirectory("referenceLinking"));
    }
  }

  private final EALScorer2015Style documentScorer;
  private final ImmutableMap<String, SimpleResultWriter> additionalResultWriters;

  private void score(final AnnotationStore goldAnswerStore,
      final LinkingStore referenceLinkingStore, final ArgumentStore argumentStore,
      final LinkingStore systemLinkingStore, Set<Symbol> docsToScore, final File outputDir)
      throws IOException {

    final List<EALScorer2015Style.Result> perDocResults = Lists.newArrayList();

    for (final Symbol docID : docsToScore) {
      try {
        final AnswerKey argumentKey = goldAnswerStore.read(docID);
        final ArgumentOutput argumentOutput = argumentStore.readOrEmpty(docID);

        final Optional<ResponseLinking> referenceLinking = referenceLinkingStore.read(argumentKey);
        final Optional<ResponseLinking> systemLinking = systemLinkingStore.read(argumentOutput);

        if (!referenceLinking.isPresent()) {
          throw new RuntimeException("Reference linking missing for " + docID);
        }

        if (!systemLinking.isPresent()) {
          throw new RuntimeException("System linking missing for " + docID);
        }

        final ScoringData scoringData = ScoringData.builder()
            .withAnswerKey(argumentKey)
            .withArgumentOutput(argumentOutput)
            .withReferenceLinking(referenceLinking.get())
            .withSystemLinking(systemLinking.get())
            .build();

        perDocResults.add(documentScorer.score(scoringData));
      } catch (Exception e) {
        throw new RuntimeException("Exception while processing " + docID, e);
      }
    }

    writeOutput(perDocResults, outputDir);
  }

  private void writeOutput(final List<EALScorer2015Style.Result> perDocResults,
      final File baseOutputDir) throws IOException {

    // write scores over everything
    (new AggregateAndPerDocResultWriter(documentScorer.lambda())).writeResult(perDocResults,
        baseOutputDir);

    // write scores broken down by event type
    (new ByEventTypeResultWriter())
        .writeResult(perDocResults, new File(baseOutputDir, "byEventType"));

    for (final Map.Entry<String, SimpleResultWriter> additionalResultWriter : additionalResultWriters
        .entrySet()) {
      additionalResultWriter.getValue().writeResult(perDocResults,
          new File(baseOutputDir, additionalResultWriter.getKey()));
    }
  }

  interface SimpleResultWriter {
    void writeResult(final List<EALScorer2015Style.Result> perDocResults,
        final File baseOutputDir) throws IOException;
  }

  interface BootstrappedResultWriter {

    void observeSample(final Set<EALScorer2015Style.Result> perDocResults);

    void writeResult(File baseOutputDir);
  }


  private static final String SYSTEM_OUTPUT_PARAM = "systemOutput";
  private static final String SYSTEM_OUTPUTS_DIR_PARAM = "systemOutputsDir";

  private static ImmutableSet<Symbol> loadDocumentsToScore(Parameters params) throws IOException {
    final File docsToScoreList = params.getExistingFile("documentsToScore");
    final ImmutableSet<Symbol> ret = ImmutableSet.copyOf(FileUtils.loadSymbolList(docsToScoreList));
    log.info("Scoring over {} documents specified in {}", ret.size(), docsToScoreList);
    return ret;
  }

  // the three methods below are a hack where the user can provide only a KBP-2014 style argument store
  // and still use this scorer by having a default linking strategy applied


  private static LinkingStore getLinkingStore(final Parameters params, final File systemOutputDir,
      final ArgumentStore argumentStore) throws IOException {
    final LinkingStore systemLinkingStore;
    if (useDefaultLinkingHack(params)) {
      systemLinkingStore = SameEventTypeLinker.create(
          ImmutableSet.of(KBPRealis.Actual, KBPRealis.Other)).wrap(argumentStore.docIDs());
    } else {
      systemLinkingStore = LinkingSpecFormats
          .openOrCreateLinkingStore(new File(systemOutputDir, "linking"));
    }
    return systemLinkingStore;
  }

  private static ArgumentStore getSystemOutputStore(final Parameters params,
      final File systemOutputDir) {
    final ArgumentStore argumentStore;
    if (useDefaultLinkingHack(params)) {
      argumentStore =
          AssessmentSpecFormats
              .openSystemOutputStore(systemOutputDir, AssessmentSpecFormats.Format.KBP2015);
    } else {
      argumentStore =
          AssessmentSpecFormats.openSystemOutputStore(new File(systemOutputDir, "arguments"),
              AssessmentSpecFormats.Format.KBP2015);
    }
    return argumentStore;
  }

  private static boolean useDefaultLinkingHack(final Parameters params) {
    return params.isPresent("createDefaultLinking") && params.getBoolean("createDefaultLinking");
  }

}

package com.bbn.kbp.events2014.scorer.bin;

import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.io.LinkingSpecFormats;
import com.bbn.kbp.events2014.io.LinkingStore;
import com.bbn.kbp.events2014.io.SystemOutputStore;
import com.bbn.kbp.linking.EALScorer2015Style;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

public final class KBP2015Scorer {
  private static final Logger log = LoggerFactory.getLogger(KBP2015Scorer.class);

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
      trueMain(argv);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static void trueMain(String[] argv) throws IOException {
    if (argv.length != 1) {
      usage();
      System.exit(1);
    }
    final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
    final KBP2015Scorer scorer = new KBP2015Scorer();

    final AnnotationStore goldAnswerStore = AssessmentSpecFormats.openAnnotationStore(params
            .getExistingDirectory("answerKey"), AssessmentSpecFormats.Format.KBP2015);
    final Set<Symbol> docsToScore = loadDocumentsToScore(params);
    final LinkingStore referenceLinkingStore = LinkingSpecFormats.openOrCreateLinkingStore(
        params.getExistingDirectory("referenceLinking"));

    checkArgument(
        params.isPresent(SYSTEM_OUTPUT_PARAM) != params.isPresent(SYSTEM_OUTPUTS_DIR_PARAM),
        "Exactly one of systemOutput and systemOutputsDir must be specified");
    if (params.isPresent(SYSTEM_OUTPUT_PARAM)) {
      final File scoringOutputDir = params.getCreatableDirectory("scoringOutputDir");
      final File systemOutputDir = params.getExistingDirectory(SYSTEM_OUTPUT_PARAM);
      log.info("Scoring single system output {}", systemOutputDir);
      final SystemOutputStore systemOutputStore =
          AssessmentSpecFormats.openSystemOutputStore(new File(systemOutputDir, "arguments"),
              AssessmentSpecFormats.Format.KBP2015);
      final LinkingStore systemLinkingStore =
          LinkingSpecFormats.openOrCreateLinkingStore(new File(systemOutputDir, "linking"));

      scorer.score(goldAnswerStore, referenceLinkingStore, systemOutputStore, systemLinkingStore,
          docsToScore, scoringOutputDir);
    } else {
      final File systemOutputsDir = params.getExistingDirectory("systemOutputsDir");
      final File scoringOutputRoot = params.getCreatableDirectory("scoringOutputRoot");

      log.info("Scoring all subdirectories of {}", systemOutputsDir);

      for (File subDir : systemOutputsDir.listFiles()) {
        if (subDir.isDirectory()) {
          log.info("Scoring {}", subDir);
          final File outputDir = new File(scoringOutputRoot, subDir.getName());
          outputDir.mkdirs();
          final SystemOutputStore systemOutputStore =
              AssessmentSpecFormats.openSystemOutputStore(new File(subDir, "arguments"),
                  AssessmentSpecFormats.Format.KBP2015);
          final LinkingStore systemLinkingStore = LinkingSpecFormats.openOrCreateLinkingStore(
              new File(subDir, "linking"));
          scorer.score(goldAnswerStore, referenceLinkingStore, systemOutputStore, systemLinkingStore,
              docsToScore, outputDir);
        }
      }
    }
  }

  private EALScorer2015Style documentScorer = EALScorer2015Style.create();

  private void score(final AnnotationStore goldAnswerStore,
      final LinkingStore referenceLinkingStore, final SystemOutputStore systemOutputStore,
      final LinkingStore systemLinkingStore, Set<Symbol> docsToScore, final File outputDir)
      throws IOException {

    final List<EALScorer2015Style.Result> perDocResults = Lists.newArrayList();

    for (final Symbol docID : docsToScore) {
      try {
        final AnswerKey argumentKey = goldAnswerStore.read(docID);
        final SystemOutput systemOutput = systemOutputStore.readOrEmpty(docID);

        final Optional<ResponseLinking> referenceLinking = referenceLinkingStore.read(argumentKey);
        final Optional<ResponseLinking> systemLinking = systemLinkingStore.read(systemOutput);

        if (!referenceLinking.isPresent()) {
          throw new RuntimeException("Reference linking missing for " + docID);
        }

        if (!systemLinking.isPresent()) {
          throw new RuntimeException("System linking missing for " + docID);
        }

        perDocResults.add(documentScorer
            .score(argumentKey, referenceLinking.get(), systemOutput, systemLinking.get()));
      } catch (Exception e) {
        throw new RuntimeException("Exception while processing " + docID, e);
      }
    }

    final File perDocOutput = new File(outputDir, "scoresByDocument.txt");
    Files.asCharSink(perDocOutput, Charsets.UTF_8).write(
        String.format("%40s\t%10s\t%10s\t%10s\n", "Document", "Arg", "Link", "Combined") +
        Joiner.on("\n").join(
        Lists.transform(perDocResults, new Function<EALScorer2015Style.Result, String>() {
          @Override
          public String apply(final EALScorer2015Style.Result input) {
            return String.format("%40s\t%10.2f\t%10.2f\t%10.2f", input.docID(),
                100.0 * input.scaledArgumentScore(), 100.0 * input.scaledLinkingScore(),
                100.0 * input.scaledScore());
          }
        })));

    double rawArgScoreSum = 0.0;
    double argNomralizerSum = 0.0;
    double rawLinkScoreSum = 0.0;
    double linkNormalizerSum = 0.0;
    for (final EALScorer2015Style.Result perDocResult : perDocResults) {
      rawArgScoreSum += Math.max(0.0, perDocResult.unscaledArgumentScore());
      argNomralizerSum += perDocResult.argumentNormalizer();
      rawLinkScoreSum += perDocResult.unscaledLinkingScore();
      linkNormalizerSum += perDocResult.linkingNormalizer();
    }


    double aggregateArgScore = (argNomralizerSum > 0.0) ? rawArgScoreSum / argNomralizerSum : 0.0;
    double aggregateLinkScore = (linkNormalizerSum > 0.0) ? rawLinkScoreSum / linkNormalizerSum : 0.0;
    double aggregateScore = (1.0-documentScorer.lambda())*aggregateArgScore + documentScorer.lambda()*aggregateLinkScore;

    Files.asCharSink(new File(outputDir, "aggregateScore.txt"), Charsets.UTF_8).write(
        String.format("%30s:%8.2f\n", "Aggregate argument score", 100.0 * aggregateArgScore) +
            String.format("%30s:%8.2f\n", "Aggregate linking score", 100.0 * aggregateLinkScore) +
            String.format("%30s:%8.2f\n", "Overall score", 100.0 * aggregateScore));

  }

  private static final String SYSTEM_OUTPUT_PARAM = "systemOutput";
  private static final String SYSTEM_OUTPUTS_DIR_PARAM = "systemOutputsDir";


  private static ImmutableSet<Symbol> loadDocumentsToScore(Parameters params) throws IOException {
    final File docsToScoreList = params.getExistingFile("documentsToScore");
    final ImmutableSet<Symbol> ret = ImmutableSet.copyOf(FileUtils.loadSymbolList(docsToScoreList));
    log.info("Scoring over {} documents specified in {}", ret.size(), docsToScoreList);
    return ret;
  }

}

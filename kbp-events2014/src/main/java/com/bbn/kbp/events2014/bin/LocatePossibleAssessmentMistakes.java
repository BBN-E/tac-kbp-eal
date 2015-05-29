package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Created by rgabbard on 5/29/15.
 */
public final class LocatePossibleAssessmentMistakes {

  private static final Logger log = LoggerFactory.getLogger(LocatePossibleAssessmentMistakes.class);

  private LocatePossibleAssessmentMistakes() {
    throw new UnsupportedOperationException();
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
    final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));

    final File assessmentsDir = params.getExistingDirectory("annotationStore");
    log.info("Using annotation store {}", assessmentsDir);
    final AssessmentSpecFormats.Format fileFormat =
        params.getEnum("fileFormat", AssessmentSpecFormats.Format.class);
    final AnnotationStore annStore = AssessmentSpecFormats.openAnnotationStore(assessmentsDir,
        fileFormat);
    final File outputDir = params.getCreatableDirectory("outputDir");

    final File conjunctsDir = new File(outputDir, "conjuncts");
    log.info("Writing possibly conjoined CASes to {}", conjunctsDir);
    final AnnotationStore conjunctStore =
        AssessmentSpecFormats.createAnnotationStore(conjunctsDir, fileFormat);
    filterTo(annStore, new ConjunctsFilter(), conjunctStore);

    annStore.close();
    conjunctStore.close();
  }

  private static void filterTo(AnnotationStore input, AnswerKey.Filter filter,
      AnnotationStore output)
      throws IOException {
    checkArgument(output.docIDs().isEmpty(), "Output annotation store must be empty");
    for (final Symbol docID : input.docIDs()) {
      output.write(input.read(docID).filter(filter));
    }
  }

  private static final class ConjunctsFilter implements AnswerKey.Filter {

    private static final ImmutableSet<String> CONJUNCTS = ImmutableSet.of("and", "or", "both",
        "And", "Or");

    @Override
    public Predicate<AssessedResponse> assessedFilter() {
      return new Predicate<AssessedResponse>() {
        @Override
        public boolean apply(final AssessedResponse input) {
          if (!input.isCorrectUpToInexactJustifications()) {
            return false;
          }

          final Iterable<String> casTokens =
              StringUtils.OnSpaces.split(input.response().canonicalArgument().string());
          for (final String spaceSepToken : casTokens) {
            if (CONJUNCTS.contains(spaceSepToken)) {
              return true;
            }
          }
          return false;
        }
      };
    }

    @Override
    public Predicate<Response> unassessedFilter() {
      return Predicates.alwaysFalse();
    }
  }
}

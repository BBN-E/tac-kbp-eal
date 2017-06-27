package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.FieldAssessment;
import com.bbn.kbp.events2014.KBPTIMEXExpression;
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

    writeFiltered("conjuncts", new ConjunctsFilter(), fileFormat, annStore, outputDir);
    writeFiltered("temporal", new TemporalFilter(), fileFormat, annStore, outputDir);
    writeFiltered("inexactPlaces", new InexactPlaces(), fileFormat, annStore, outputDir);


    final File temporalDir = new File(outputDir, "temporals");
    log.info("Writing temporal errors to {}", temporalDir);
    final AnnotationStore temporalStore =
        AssessmentSpecFormats.createAnnotationStore(temporalDir, fileFormat);
    filterTo(annStore, new TemporalFilter(), temporalStore);

    annStore.close();
  }

  private static void writeFiltered(final String filterName,
      final AnswerKey.Filter filter,
      final AssessmentSpecFormats.Format fileFormat, final AnnotationStore annStore,
      final File outputDir) throws IOException {
    final File filteredDir = new File(outputDir, filterName);
    log.info("Writing {} to {}", filterName, filteredDir);
    final AnnotationStore outputStore =
        AssessmentSpecFormats.createAnnotationStore(filteredDir, fileFormat);
    filterTo(annStore, filter, outputStore);
    outputStore.close();
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
              StringUtils.onSpaces().split(input.response().canonicalArgument().string());
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

  private static final class TemporalFilter implements AnswerKey.Filter {

    @Override
    public Predicate<AssessedResponse> assessedFilter() {
      return new Predicate<AssessedResponse>() {
        @Override
        public boolean apply(final AssessedResponse input) {
          if (input.isCorrectUpToInexactJustifications()) {
            if (input.response().isTemporal()) {
              try {
                final KBPTIMEXExpression time =
                    KBPTIMEXExpression.parseTIMEX(input.response().canonicalArgument().string());
              } catch (KBPTIMEXExpression.KBPTIMEXException te) {
                return true;
              }
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

  private static final class InexactPlaces implements AnswerKey.Filter {

    @Override
    public Predicate<AssessedResponse> assessedFilter() {
      return new Predicate<AssessedResponse>() {
        @Override
        public boolean apply(final AssessedResponse input) {
          if (input.isCorrectUpToInexactJustifications()) {
            if (input.assessment().entityCorrectFiller().get().equals(FieldAssessment.INEXACT)
                && input.response().role().asString().equals("Place")) {
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

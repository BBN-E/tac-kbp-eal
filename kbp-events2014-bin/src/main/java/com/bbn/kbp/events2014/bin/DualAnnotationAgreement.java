package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.diff.SummaryConfusionMatrix;
import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.*;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assesses the agreement for dual annotation on the same files.
 * Parameters:
 * dualAnnotation.docIDsToCompare: a file listing documents to compare annotation for, one doc ID per line
 * dualAnnotation.{left|right}.name: a name to attach to each annotation store, for more readable output
 * dualAnnotation.{left|right}.path: the paths to the left and right annotation stores
 */
public final class DualAnnotationAgreement {
    private static final Logger log = LoggerFactory.getLogger(DualAnnotationAgreement.class);

    private DualAnnotationAgreement() {
        throw new UnsupportedOperationException();
    }

    /** Represents the degree of agreement of an assessment between
     *  the two stores.   */
    private enum AgreementState {
        /** the assessments match */
        AGREE,
        /** the assessments disagree */
        DISAGREE,
        /** the assessment was not applicable for the left annotator only */
        NA_LEFT,
        /** the assessment was not applicable for the right annotator only */
        NA_RIGHT,
        /** the assessment was not applicale for both annotation */
        NA_BOTH;

        public static ImmutableSet<AgreementState> NON_APPLICABLE_STATES =
                ImmutableSet.of(NA_LEFT, NA_RIGHT, NA_BOTH);
    };

    private static void trueMain(String[] argv) throws IOException {
        final Parameters dualAnnotationParams = Parameters.loadSerifStyle(new File(argv[0]))
                .copyNamespace("dualAnnotation");

        // determine which documents to compare
        final File docIDsToCompareFile = dualAnnotationParams.getExistingFile("docIDsToCompare");
        log.info("Comparing docIDs from {}", docIDsToCompareFile);
        final List<Symbol> docIDsToCompare = FileUtils.loadSymbolList(docIDsToCompareFile);
        log.info("Loaded {} docIDs", docIDsToCompare.size());

        final AssessmentSpecFormats.Format fileFormat =
                dualAnnotationParams.getEnum("fileFormat", AssessmentSpecFormats.Format.class);

        final Parameters leftParams = dualAnnotationParams.copyNamespace("left");
        final String leftStoreName = leftParams.getString("name");
        final File leftStorePath = leftParams.getExistingDirectory("path");
        final AnnotationStore leftAnnStore = AssessmentSpecFormats.openAnnotationStore(leftStorePath, fileFormat);
        log.info("Loading left annotation store {} from {}", leftStoreName, leftStorePath);

        final Parameters rightParams = dualAnnotationParams.copyNamespace("right");
        final String rightStoreName = rightParams.getString("name");
        final File rightStorePath = rightParams.getExistingDirectory("path");
        final AnnotationStore rightAnnStore = AssessmentSpecFormats.openAnnotationStore(rightStorePath, fileFormat);
        log.info("Loading right annotation store {} from {}", rightStoreName, rightStorePath);

        final AgreementRecorder agreementRecorder = new AgreementRecorder();

        for (final Symbol docID : docIDsToCompare) {
            final AnswerKey leftAnnotation = leftAnnStore.read(docID);
            final AnswerKey rightAnnotation = rightAnnStore.read(docID);

            final Map<Response, AssessedResponse> leftAnnotationsIndexed = Maps.uniqueIndex(
                    leftAnnotation.annotatedResponses(), AssessedResponse.Response);
            final Map<Response, AssessedResponse> rightAnnotationsIndexed = Maps.uniqueIndex(
                    rightAnnotation.annotatedResponses(), AssessedResponse.Response);

            if (leftAnnotationsIndexed.keySet().equals(rightAnnotationsIndexed.keySet())) {
                for (final Response response : leftAnnotationsIndexed.keySet()) {
                    // get will succeed by above checks
                    agreementRecorder.recordAgreement(leftAnnotation.assessment(response).get(),
                            rightAnnotation.assessment(response).get());
                }
            } else {
                reportResponseMismatch(docID, leftAnnotationsIndexed.keySet(), rightAnnotationsIndexed.keySet());
            }
        }

        agreementRecorder.dumpResults();
    }

    private static class AgreementRecorder {
        final Map<String, SummaryConfusionMatrix.Builder> assessmentNameToConfusionMatrix =
                ImmutableMap.<String, SummaryConfusionMatrix.Builder>builder()
            .put("AET", SummaryConfusionMatrix.builder())
            .put("AER", SummaryConfusionMatrix.builder())
            .put("BF", SummaryConfusionMatrix.builder())
            .put("CAS", SummaryConfusionMatrix.builder())
            .put("CAS type", SummaryConfusionMatrix.builder())
            .put("Realis", SummaryConfusionMatrix.builder()).build();
        final ImmutableSet<String> USES_CORRECT_INEXACT = ImmutableSet.of("AET", "AER", "BF", "CAS");

        public void recordAgreement(ResponseAssessment leftAssessment, ResponseAssessment rightAssessment) {
            recordAssessment(assessmentNameToConfusionMatrix.get("AET"),
                    leftAssessment.justificationSupportsEventType(), rightAssessment.justificationSupportsEventType());
            recordAssessment(assessmentNameToConfusionMatrix.get("AER"),
                    leftAssessment.justificationSupportsRole(), rightAssessment.justificationSupportsRole());
            recordAssessment(assessmentNameToConfusionMatrix.get("BF"),
                    leftAssessment.baseFillerCorrect(), rightAssessment.baseFillerCorrect());
            recordAssessment(assessmentNameToConfusionMatrix.get("CAS"),
                    leftAssessment.entityCorrectFiller(), rightAssessment.entityCorrectFiller());
            recordAssessment(assessmentNameToConfusionMatrix.get("Realis"),
                    leftAssessment.realis(), rightAssessment.realis());
            recordAssessment(assessmentNameToConfusionMatrix.get("CAS type"),
                    leftAssessment.mentionTypeOfCAS(), rightAssessment.mentionTypeOfCAS());
        }

        private static <T> void recordAssessment(SummaryConfusionMatrix.Builder confusionMatrixBuilder,
                                                 Optional<T> leftAssessment, Optional<T> rightAssessment) {
            confusionMatrixBuilder.accumulate(asSymbol(leftAssessment), asSymbol(rightAssessment), 1.0);
        }

        private static final Symbol NA = Symbol.from("n/a");
        private static final Symbol PRESENT = Symbol.from("Present");

        private static <T> Symbol asSymbol(Optional<T> assessment) {
            if (assessment.isPresent()) {
                return Symbol.from(assessment.get().toString());
            } else {
                return NA;
            }
        }

        private static SummaryConfusionMatrix.CellFilter NEITHER_IS_NA = new SummaryConfusionMatrix.CellFilter() {
            @Override
            public boolean keepCell(Symbol row, Symbol column) {
                return !row.equals(NA) && !column.equals(NA);
            }
        };

        private static final Function<Symbol, Symbol> ASSESSMENT_IS_PRESENT = new Function<Symbol, Symbol>() {
            @Override
            public Symbol apply(Symbol x) {
                if (NA.equals(x)) {
                    return NA;
                } else {
                    return PRESENT;
                }
            }
        };

        private static final Function<Symbol, Symbol> COUNT_CORRECT_AND_INEXACT_AS_THE_SAME = new Function<Symbol, Symbol>() {
            private final ImmutableSet<Symbol> IS_CORRECT_OR_INEXACT = ImmutableSet.of(
                    Symbol.from(FieldAssessment.CORRECT.toString()),
                    Symbol.from(FieldAssessment.INEXACT.toString()));
            private final Symbol NOT_INCORRECT = Symbol.from("NotIncorrect");

            @Override
            public Symbol apply(Symbol x) {
                if (IS_CORRECT_OR_INEXACT.contains(x)) {
                    return NOT_INCORRECT;
                } else {
                    return x;
                }
            }
        };

        private static final KappaCalculator kappaCalculator = KappaCalculator.create();
        public void dumpResults() {
            for (final Map.Entry<String, SummaryConfusionMatrix.Builder> assessmentResults
                    : assessmentNameToConfusionMatrix.entrySet()) {
                final String assessmentName = assessmentResults.getKey();
                final SummaryConfusionMatrix confusionMatrix = assessmentResults.getValue().build();
                System.out.println(" ============== " + assessmentName + " ==============\n\n");

                final SummaryConfusionMatrix filteredConfusionMatrix = confusionMatrix.filteredCopy(NEITHER_IS_NA);
                System.out.println("Confusion matrix when both assessments are present:\n");
                System.out.println(filteredConfusionMatrix.prettyPrint());
                System.out.format("Agreement: %.2f%%\n", 100.0 * filteredConfusionMatrix.accuracy());
                System.out.format("Agreement kappa: %.2f\n\n", 100.0 * kappaCalculator.computeKappa(filteredConfusionMatrix));


                if (USES_CORRECT_INEXACT.contains(assessmentName)) {
                    final SummaryConfusionMatrix lenientConfusionMatrix =
                            filteredConfusionMatrix.copyWithTransformedLabels(COUNT_CORRECT_AND_INEXACT_AS_THE_SAME);
                    System.out.println("Confusion matrix when both assessments are present, collapsing CORRECT and INEXACT:\n");
                    System.out.println(lenientConfusionMatrix.prettyPrint());
                    System.out.format("Agreement: %.2f%%\n", 100.0 * lenientConfusionMatrix.accuracy());
                    System.out.format("Agreement kappa: %.2f\n\n", 100.0 * kappaCalculator.computeKappa(lenientConfusionMatrix));

                }
            }

            System.out.println("\n\n\nBelow are the 'presence agreement' numbers indicating how often both annotators made "
                    + " a certain assessment at all, regardless of how they assessed.  A difference on assessment presence "
                    + " generally indicates a difference on some prior assessment this assessment depends on.");

            for (final Map.Entry<String, SummaryConfusionMatrix.Builder> assessmentResults
                    : assessmentNameToConfusionMatrix.entrySet()) {
                final String assessmentName = assessmentResults.getKey();
                final SummaryConfusionMatrix confusionMatrix = assessmentResults.getValue().build();
                System.out.println(" ============== " + assessmentName + " ==============\n\n");
                System.out.println("Agreement concerning assessment presence");
                final SummaryConfusionMatrix presenceConfusionMatrix =
                        confusionMatrix.copyWithTransformedLabels(ASSESSMENT_IS_PRESENT);
                System.out.println(presenceConfusionMatrix.prettyPrint());
                System.out.format("Presence agreement: %.2f%%\n", 100.0 * presenceConfusionMatrix.accuracy());
                System.out.format("Presence kappa: %.2f\n\n", 100.0 * kappaCalculator.computeKappa(presenceConfusionMatrix));
            }
        }
    }


    private static void reportResponseMismatch(Symbol docID, Set<Response> leftAnnotationsIndexed,
                                               Set<Response> rightAnnotationsIndexed) {
        final Set<String> leftOnly = FluentIterable.from(Sets.difference(leftAnnotationsIndexed, rightAnnotationsIndexed))
                .transform(Response.uniqueIdFunction()).toSet();
        final Set<String> rightOnly = FluentIterable.from(Sets.difference(rightAnnotationsIndexed, leftAnnotationsIndexed))
                .transform(Response.uniqueIdFunction()).toSet();
        throw new RuntimeException("For document " + docID + ", sets of assessed responses do not match. "
                + "In left only: " + leftOnly + "; in right only: " + rightOnly);
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
}

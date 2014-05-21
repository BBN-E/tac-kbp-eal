package com.bbn.com.bbn.kbp.events2014.assessmentDiff;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.com.bbn.kbp.events2014.assessmentDiff.diffLoggers.BasicDiffLogger;
import com.bbn.com.bbn.kbp.events2014.assessmentDiff.diffLoggers.DiffLogger;
import com.bbn.com.bbn.kbp.events2014.assessmentDiff.observers.*;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AsssessedResponse;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseAssessment;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.CharSink;
import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

public final class KBPAssessmentDiff {
    private static final Logger log = LoggerFactory.getLogger(KBPAssessmentDiff.class);

    private KBPAssessmentDiff() {
        throw new UnsupportedOperationException();
    }

    private static void trueMain(String[] argv) throws IOException {
        final Parameters params = Parameters.loadSerifStyle(new File(argv[0]));
        log.info(params.dump());

        final String leftName = params.getString("leftAnnotationName");
        final AnnotationStore leftAnnotationStore = AssessmentSpecFormats.openAnnotationStore(
                params.getExistingDirectory("leftAnnotationStore"));
        final String rightName = params.getString("rightAnnotationName");
        final AnnotationStore rightAnnotationStore = AssessmentSpecFormats.openAnnotationStore(
                params.getExistingDirectory("rightAnnotationStore"));
        final File outputDirectory = params.getCreatableDirectory("outputDirectory");
        // the user may optionally specify a "baseline store". Any responses which are assessed
        // in the baseline store will have their assessments ignored when diffing. This is
        // useful for diffing two "marginal" sets of annotations on top of an existing
        // repository
        final Optional<AnnotationStore> baselineAnnotationStore = getBaselineAnnotationStore(params);

        final Set<Symbol> commonDocIds = Sets.intersection(leftAnnotationStore.docIDs(), rightAnnotationStore.docIDs());
        logCoverage(leftName, leftAnnotationStore.docIDs(), rightName, rightAnnotationStore.docIDs(),
                Files.asCharSink(new File(outputDirectory, "coverage.txt"), Charsets.UTF_8));

        final AssessmentOverlapObserver overlapObserver = new AssessmentOverlapObserver();
        final Map<String, AssessmentPairObserver> observers = createObservers(outputDirectory);

        final DiffLogger diffLogger = new BasicDiffLogger();

        int totalCommonResponses = 0;
        int totalCommonResponsesNotInBaseline = 0;
        for (final Symbol docId : commonDocIds) {
            final AnswerKey leftAnswers = leftAnnotationStore.readOrEmpty(docId);
            final AnswerKey rightAnswers = rightAnnotationStore.read(docId);
            overlapObserver.observe(leftAnswers, rightAnswers);

            final Set<Response> baselineResponses = getBaselineResponses(docId, baselineAnnotationStore);

            // we want to compare assessments for responses which are
            final Set<Response> commonResponses =
                    Sets.intersection(
                            FluentIterable.from(leftAnswers.annotatedResponses())
                                    .transform(AsssessedResponse.Response)
                                    .toSet(),
                            FluentIterable.from(rightAnswers.annotatedResponses())
                                    .transform(AsssessedResponse.Response)
                                    .toSet());
            totalCommonResponses += commonResponses.size();

            final Set<Response> commonResponsesMinusBaseline =
                    Sets.difference(commonResponses, baselineResponses);
            totalCommonResponsesNotInBaseline += commonResponsesMinusBaseline.size();

            for (final Response response : commonResponsesMinusBaseline) {
                final ResponseAssessment leftAssessment = leftAnswers.assessment(response).get();
                final ResponseAssessment rightAssessment = rightAnswers.assessment(response).get();

                for (final AssessmentPairObserver observer : observers.values()) {
                    observer.observe(response, leftAssessment, rightAssessment);
                }
            }
        }

        log.info("Total common responses: {}; total not in baseline: {}.",
                totalCommonResponses, totalCommonResponsesNotInBaseline);
        
        overlapObserver.report();
        for (final Map.Entry<String, AssessmentPairObserver> entry : observers.entrySet()) {
            final File observerOutputDir = new File(outputDirectory, entry.getKey());
            observerOutputDir.mkdirs();
            entry.getValue().finish(diffLogger, observerOutputDir);
        }
    }

    private static Optional<AnnotationStore> getBaselineAnnotationStore(Parameters params) throws IOException {
        final Optional<File> baselineAnnotationStoreDir = params.getOptionalExistingDirectory(
                "baselineAnnotationStore");
        final Optional<AnnotationStore> baselineAnnotationStore;
        if (baselineAnnotationStoreDir.isPresent()) {
            baselineAnnotationStore = Optional.of(AssessmentSpecFormats.openAnnotationStore(baselineAnnotationStoreDir.get()));
        } else {
            baselineAnnotationStore = Optional.absent();
        }
        return baselineAnnotationStore;
    }

    private static Set<Response> getBaselineResponses(Symbol docId, Optional<AnnotationStore> baselineAnnotationStore) throws IOException {
        final Set<Response> baselineResponses;
        if (baselineAnnotationStore.isPresent()) {
            final AnswerKey baselineAnswers = baselineAnnotationStore.get().readOrEmpty(docId);
            baselineResponses = FluentIterable.from(baselineAnswers.annotatedResponses())
                    .transform(AsssessedResponse.Response).toSet();
        } else {
            baselineResponses = ImmutableSet.of();
        }
        return baselineResponses;
    }

    private static Map<String, AssessmentPairObserver> createObservers(File outputDirectory) {
        return ImmutableMap.<String, AssessmentPairObserver>builder()
                .put("aet", new AETObserver())
                .put("aer", new AERObserver())
                .put("baseFiller", new BaseFillerObserver())
                .put("CAS", new CASObserver())
                .put("realis", new RealisObserver())
                .put("mentionType", new MentionTypeObserver()).build();
    }

    private static void logCoverage(String leftName, Set<Symbol> leftDocIds, String rightName, Set<Symbol> rightDocIds,
                                    CharSink out) throws IOException
    {
        final String msg = String.format(
                "%d documents in %s; %d in %s. %d in common, %d left-only, %d right-only",
                leftDocIds.size(), leftName, rightDocIds.size(), rightName,
                Sets.intersection(leftDocIds, rightDocIds).size(),
                Sets.difference(leftDocIds, rightDocIds).size(),
                Sets.difference(rightDocIds, leftDocIds).size());
        log.info(msg);
        out.write(msg);
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

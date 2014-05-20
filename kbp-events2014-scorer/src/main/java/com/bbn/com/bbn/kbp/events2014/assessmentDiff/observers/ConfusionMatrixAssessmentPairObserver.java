package com.bbn.com.bbn.kbp.events2014.assessmentDiff.observers;

import com.bbn.bue.common.diff.ProvenancedConfusionMatrix;
import com.bbn.bue.common.diff.SummaryConfusionMatrix;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.com.bbn.kbp.events2014.assessmentDiff.KBPAssessmentDiff;
import com.bbn.com.bbn.kbp.events2014.assessmentDiff.diffLoggers.DiffLogger;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseAssessment;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;

public abstract class ConfusionMatrixAssessmentPairObserver implements AssessmentPairObserver {
    private final ProvenancedConfusionMatrix.Builder<Response> confusionMatrixBuilder =
            ProvenancedConfusionMatrix.builder();

    public ConfusionMatrixAssessmentPairObserver() {

    }

    protected abstract boolean filter(Response response, ResponseAssessment left, ResponseAssessment right);
    protected abstract Symbol toKey(ResponseAssessment assessment);

    @Override
    public final void observe(Response response, ResponseAssessment left, ResponseAssessment right) {
        if (filter(response, left, right)) {
            final Symbol leftKey = toKey(left);
            final Symbol rightKey = toKey(right);

            confusionMatrixBuilder.record(leftKey, rightKey, response);
        }
    }

    public final void finish(DiffLogger diffLogger, File outputDir) throws IOException {
        final ProvenancedConfusionMatrix<Response> confusionMatrix = confusionMatrixBuilder.build();

        final StringBuilder sb = new StringBuilder();

        for (final Symbol leftKey : confusionMatrix.leftLabels()) {
            for (final Symbol rightKey : confusionMatrix.rightLabels()) {
                if (leftKey != rightKey) {
                    for (final Response response : confusionMatrix.cell(leftKey, rightKey)) {
                        diffLogger.logDifference(response, leftKey, rightKey, sb);
                    }
                }
            }
        }
        Files.asCharSink(new File(outputDir, "examples.html"), Charsets.UTF_8).write(sb.toString());

        final SummaryConfusionMatrix summaryConfusionMatrix = confusionMatrix.buildSummaryMatrix();
        Files.asCharSink(new File(outputDir, "summary.html"), Charsets.UTF_8).write(summaryConfusionMatrix.prettyPrint());
    }
}

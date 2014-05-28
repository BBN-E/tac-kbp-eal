package com.bbn.kbp.events2014.assessmentDiff.observers;

import com.bbn.bue.common.collections.MapUtils;
import com.bbn.bue.common.diff.FMeasureTableRenderer;
import com.bbn.bue.common.diff.ProvenancedConfusionMatrix;
import com.bbn.bue.common.diff.SummaryConfusionMatrix;
import com.bbn.bue.common.evaluation.FMeasureCounts;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.assessmentDiff.diffLoggers.DiffLogger;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseAssessment;
import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.Map;

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
        final StringBuilder msg = new StringBuilder();
        msg.append(summaryConfusionMatrix.prettyPrint()).append("\n\n");
        final Map<String, FMeasureCounts> fMeasureCountsMap = Maps.newHashMap();
        for (final Symbol key : summaryConfusionMatrix.leftLabels()) {
            fMeasureCountsMap.put(key.toString(), summaryConfusionMatrix.FMeasureVsAllOthers(key));
        }
        final FMeasureTableRenderer tableRenderer = FMeasureTableRenderer.create()
                .setNameFieldLength(4 + MapUtils.longestKeyLength(fMeasureCountsMap));
        msg.append(tableRenderer.render(fMeasureCountsMap));
        msg.append(String.format("Accuracy: %5.2f\n", 100.0 * summaryConfusionMatrix.accuracy()));
        Files.asCharSink(new File(outputDir, "summary.html"), Charsets.UTF_8).write(msg.toString());
    }
}

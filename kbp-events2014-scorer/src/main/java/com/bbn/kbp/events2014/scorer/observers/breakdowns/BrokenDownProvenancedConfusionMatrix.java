package com.bbn.kbp.events2014.scorer.observers.breakdowns;

import com.bbn.bue.common.diff.ProvenancedConfusionMatrix;
import com.bbn.bue.common.diff.SummaryConfusionMatrix;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

public final class BrokenDownProvenancedConfusionMatrix<SignatureType, ProvenanceType> {
    private ImmutableMap<SignatureType, ProvenancedConfusionMatrix<ProvenanceType>> data;

    private BrokenDownProvenancedConfusionMatrix(Map<SignatureType, ProvenancedConfusionMatrix<ProvenanceType>> data) {
        this.data = ImmutableMap.copyOf(data);
    }

    public static <SignatureType, ProvenanceType> BrokenDownProvenancedConfusionMatrix fromMap(
            Map<SignatureType, ProvenancedConfusionMatrix<ProvenanceType>> data) {
        return new BrokenDownProvenancedConfusionMatrix(data);
    }


    public Map<SignatureType, ProvenancedConfusionMatrix<ProvenanceType>> asMap() {
        return data;
    }

    public BrokenDownSummaryConfusionMatrix toSummary() {
        final ImmutableMap.Builder<SignatureType, SummaryConfusionMatrix> ret = ImmutableMap.builder();
        for (final Map.Entry<SignatureType, ProvenancedConfusionMatrix<ProvenanceType>> entry : data.entrySet()) {
            ret.put(entry.getKey(), entry.getValue().buildSummaryMatrix());
        }
        return BrokenDownSummaryConfusionMatrix.fromMap(ret.build());
    }
}

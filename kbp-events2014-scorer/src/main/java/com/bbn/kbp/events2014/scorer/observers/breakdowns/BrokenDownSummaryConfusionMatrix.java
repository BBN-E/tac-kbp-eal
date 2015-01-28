package com.bbn.kbp.events2014.scorer.observers.breakdowns;

import com.bbn.bue.common.diff.SummaryConfusionMatrix;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public final class BrokenDownSummaryConfusionMatrix<SignatureType> {

  private ImmutableMap<SignatureType, SummaryConfusionMatrix> data;

  private BrokenDownSummaryConfusionMatrix(Map<SignatureType, SummaryConfusionMatrix> data) {
    this.data = ImmutableMap.copyOf(data);
  }

  public static <SignatureType> BrokenDownSummaryConfusionMatrix fromMap(
      Map<SignatureType, SummaryConfusionMatrix> data) {
    return new BrokenDownSummaryConfusionMatrix(data);
  }

  public Map<SignatureType, SummaryConfusionMatrix> asMap() {
    return data;
  }

  /**
   * Provides a builder for a {@link com.bbn.kbp.events2014.scorer.observers.breakdowns.BrokenDownSummaryConfusionMatrix}
   * where the keys of the result will be ordered by {@code ordering}.  We require the ordering to
   * maintain deterministic output.
   */
  public static <SignatureType> Builder<SignatureType> builder(Ordering<SignatureType> ordering) {
    return new Builder<SignatureType>(ordering);
  }

  public static final class Builder<SignatureType> {

    private final Ordering<SignatureType> keyOrdering;
    private final Map<SignatureType, SummaryConfusionMatrix.Builder> data = Maps.newHashMap();

    private Builder(final Ordering<SignatureType> keyOrdering) {
      this.keyOrdering = checkNotNull(keyOrdering);
    }

    public Builder<SignatureType> combine(
        BrokenDownSummaryConfusionMatrix<SignatureType> dataToAdd) {
      for (final Map.Entry<SignatureType, SummaryConfusionMatrix> entry : dataToAdd.asMap()
          .entrySet()) {
        final SignatureType signature = entry.getKey();
        if (!data.containsKey(signature)) {
          data.put(signature, SummaryConfusionMatrix.builder());
        }
        data.get(signature).accumulate(entry.getValue());
      }
      return this;
    }

    public BrokenDownSummaryConfusionMatrix<SignatureType> build() {
      final ImmutableMap.Builder<SignatureType, SummaryConfusionMatrix> ret =
          ImmutableMap.builder();

      for (final SignatureType key : keyOrdering.sortedCopy(data.keySet())) {
        ret.put(key, data.get(key).build());
      }

      return BrokenDownSummaryConfusionMatrix.fromMap(ret.build());
    }
  }
}

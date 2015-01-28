package com.bbn.kbp.events2014.scorer.observers;

import com.bbn.bue.common.annotations.MoveToBUECommon;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Beta
@MoveToBUECommon
public final class PercentileComputer {

  private final Algorithm algorithm;

  private PercentileComputer(Algorithm algorithm) {
    this.algorithm = checkNotNull(algorithm);
  }

  /**
   * Creates a {@code PercentileComputer} which uses the algorithm given in NIST's Engineering
   * Statistics Handbook. The relevant section is copied below:
   *
   * <blockquote> Percentiles can be estimated from {@code N} measurements as follows: for the pth
   * percentile, set p(N+1) equal to k + d for k an integer, and d, a fraction greater than or equal
   * to 0 and less than 1. <ul> <li>For 0 < k < N,  Y(p) = Y[k] + d(Y[k+1] - Y[k])</li> <li>For k =
   * 0,  Y(p) = Y[1]</li> <li>For k = N,  Y(p) = Y[N]</li> </ul> </blockquote>
   *
   * Note NIST is using 1-based indexing.
   *
   * @see http://www.itl.nist.gov/div898/handbook/prc/section2/prc252.htm
   */
  public static PercentileComputer nistPercentileComputer() {
    return new PercentileComputer(Algorithm.NIST);
  }

  /**
   * Creates a {@code PercentileComputer} which uses the "Excel" alternative algorithm given in
   * NIST's Engineering Statistics Handbook.  It is the same as the base NIST algorithm, except it
   * assigns (k,d) to the integral and fractional parts of p(N-1)+1 instead of p(N+1).
   *
   * @see http://www.itl.nist.gov/div898/handbook/prc/section2/prc252.htm
   */
  public static PercentileComputer excelPercentileComputer() {
    return new PercentileComputer(Algorithm.EXCEL);
  }

  public Percentiles calculatePercentilesAdoptingData(double[] data) {
    return new Percentiles(data);
  }

  public Percentiles calculatePercentilesCopyingData(double[] data) {
    return new Percentiles(data.clone());
  }

  // these may assume percentile is valid and data non-empty
  private enum Algorithm {
    NIST {
      @Override
      public double computePercentile(double percentile, double[] data) {
        final int N = data.length;
        final double rank = percentile * (N + 1);
        final int k = (int) rank;
        final double d = rank - k;

        if (k == 0) {
          return data[0];
        } else if (k == N) {
          return data[N - 1];
        } else {
          // we subtract 1 when looking up because NIST uses 1-based indexing
          final double yK = data[k - 1];
          final double yKPlusOne = data[k];
          return yK + d * (yKPlusOne - yK);
        }
      }
    },
    EXCEL {
      @Override
      public double computePercentile(double percentile, double[] data) {
        final int N = data.length;
        final double rank = percentile * (N - 1) + 1;
        final int k = (int) rank;
        final double d = rank - k;

        if (k == 0) {
          return data[0];
        } else if (k == N) {
          return data[N - 1];
        } else {
          // we subtract 1 when looking up because NIST uses 1-based indexing
          final double yK = data[k - 1];
          final double yKPlusOne = data[k];
          return yK + d * (yKPlusOne - yK);
        }
      }
    };

    public abstract double computePercentile(double percentile, double[] data);
  }


  public class Percentiles {

    private final double[] data;

    private Percentiles(double[] data) {
      this.data = data;
      Arrays.sort(data);
    }

    public int numObservedValues() {
      return data.length;
    }

    public double median() {
      if (data.length == 0) {
        throw new NoSuchElementException("No median exists because no data has been observed");
      }

      if (data.length % 2 == 0) {
        // if we have an event number of elements, return the mean of the two
        // middle element
        return 0.5 * ((data[data.length / 2] + data[data.length / 2 - 1]));
      } else {
        // if we have an odd number of elements, return the unique middle element
        return data[data.length / 2];
      }
    }

    public double min() {
      return data[0];
    }

    public double max() {
      return data[data.length - 1];
    }

    /**
     * Calculates the p-th percentile of the observed data.  The algorithm used varies depending on
     * what {@link com.bbn.kbp.events2014.scorer.observers.PercentileComputer} generated this data.
     * If no data was observed, this will throw a {@link java.util.NoSuchElementException}. This
     * method takes time linear in the number of observed values.
     *
     * @param p Must be in [0.0, 1.0)
     */
    public double percentile(double p) {
      checkArgument(p >= 0.0 && p < 1.0, "Percentiles must be in [0.0, 1.0)");
      if (data.length == 0) {
        throw new NoSuchElementException("Cannot query percentiles when no data has been observed");
      }
      return algorithm.computePercentile(p, data);
    }

    public List<Double> percentiles(Iterable<Double> percentilesToGet) {
      final ImmutableList.Builder<Double> ret = ImmutableList.builder();
      for (final double percentile : percentilesToGet) {
        ret.add(percentile(percentile));
      }
      return ret.build();
    }
  }

    /*public static final class Builder {
        private final List<Double> data = Lists.newArrayList();

        private Builder() {
        }

        public void observe(double d) {
            data.add(d);
        }

        public void observe(Collection<Double> values) {
            data.addAll(values);
        }
    }*/
}

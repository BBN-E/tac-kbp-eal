package com.bbn.kbp.events2014.scorer;

import com.bbn.bue.common.evaluation.BrokenDownProvenancedConfusionMatrix;
import com.bbn.bue.common.evaluation.BrokenDownSummaryConfusionMatrix;
import com.bbn.bue.common.evaluation.ProvenancedConfusionMatrix;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.bue.common.symbols.SymbolUtils;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;

import java.util.Map;

public final class BreakdownComputer<ProvenanceType> {

  private final Map<String, Function<? super ProvenanceType, Symbol>> breakdownFunctions;

  private BreakdownComputer(Map<String, Function<ProvenanceType, Symbol>>
      breakdownFunctions) {
    final ImmutableMap.Builder<String, Function<? super ProvenanceType, Symbol>> builder =
        ImmutableMap.builder();
    builder.put("Aggregate", Functions.constant(Symbol.from("Aggregate")));
    builder.putAll(breakdownFunctions);
    this.breakdownFunctions = builder.build();
  }

  public static <ProvenanceType> BreakdownComputer create(
      Map<String, Function<ProvenanceType, Symbol>>
          breakdownFunctions) {
    return new BreakdownComputer(breakdownFunctions);
  }


  private static final Ordering<Symbol> BREAKDOWN_KEY_ORDERING =
      Ordering.from(new SymbolUtils.ByString());

  public ImmutableMap<String, BrokenDownSummaryConfusionMatrix<Symbol>> computeBreakdownSummaries(
      ProvenancedConfusionMatrix<ProvenanceType> data) {
    final Map<String, BrokenDownProvenancedConfusionMatrix<Symbol, ProvenanceType>> brokenDown =
        BreakdownFunctions.computeBreakdowns(data, breakdownFunctions, BREAKDOWN_KEY_ORDERING);

    final ImmutableMap.Builder<String, BrokenDownSummaryConfusionMatrix<Symbol>> ret =
        ImmutableMap.builder();

    for (final Map.Entry<String, BrokenDownProvenancedConfusionMatrix<Symbol, ProvenanceType>> breakdown
        : brokenDown.entrySet()) {
      ret.put(breakdown.getKey(), breakdown.getValue().toSummary());
    }

    return ret.build();
  }
}

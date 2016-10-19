package com.bbn.kbp.events2014;

import com.bbn.bue.common.TextGroupPublicImmutable;
import com.bbn.bue.common.annotations.MoveToBUECommon;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import java.util.Map;

import static com.bbn.bue.common.OrderingUtils.maxFunction;
import static com.bbn.bue.common.StringUtils.anyCharMatches;
import static com.bbn.bue.common.StringUtils.isEmpty;
import static com.bbn.bue.common.collections.MultimapUtils.reduceToMap;
import static com.bbn.bue.common.symbols.SymbolUtils.desymbolizeFunction;
import static com.bbn.kbp.events2014._QueryResponse2016.neutralizeRealisFunction;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.compose;
import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.all;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;

/**
 * An immutable collection of {@link QueryResponse2016}, {@link QueryAssessment2016}, and associated
 * metadata.
 */
@TextGroupPublicImmutable
@Value.Immutable
@Functional
public abstract class _CorpusQueryAssessments {

  @Value.Parameter
  public abstract ImmutableSet<QueryResponse2016> queryReponses();

  /**
   * A mapping of query responses to the IDs of systems which submitted them. Every query response
   * will appear as a key in this map.  Every query response will be mapped to at least one system
   * ID.
   */
  @Value.Parameter
  public abstract ImmutableSetMultimap<QueryResponse2016, Symbol> queryResponsesToSystemIDs();

  /**
   * A map of {@link QueryResponse2016}s to their {@link QueryAssessment2016} assessments. Any
   * unassessed members of {@link #queryReponses()} will not appear in this map, so {@link
   * QueryAssessment2016#UNASSASSED} will never be a value.
   */
  @Value.Parameter
  public abstract ImmutableMap<QueryResponse2016, QueryAssessment2016> assessments();

  /**
   * A map of {@link QueryResponse2016} to associated metadata, if any.
   */
  @Value.Parameter
  public abstract ImmutableMap<QueryResponse2016, String> metadata();

  public static CorpusQueryAssessments createEmpty() {
    return CorpusQueryAssessments.builder().build();
  }

  /**
   * @return A derived set from {@link #queryReponses()} of query IDs
   */
  @Value.Derived
  public ImmutableSet<Symbol> queryIDs() {
    return FluentIterable.from(queryReponses()).index(QueryResponse2016Functions.queryID())
        .keySet();
  }

  /**
   * @return A derived set from {@link #queryReponses()} of Document IDs
   */
  @Value.Derived
  public ImmutableSet<Symbol> docIDs() {
    return FluentIterable.from(queryReponses()).index(QueryResponse2016Functions.docID()).keySet();
  }

  /**
   * @return A derived set from {@link #queryReponses()} of System IDs
   */
  @Value.Derived
  public ImmutableSet<Symbol> systemIDs() {
    return ImmutableSet.copyOf(queryResponsesToSystemIDs().values());
  }

  @Value.Check
  protected void check() {
    checkArgument(queryReponses().containsAll(metadata().keySet()),
        "Metadata contained an unknown query; error in constructing the store!");
    checkArgument(queryReponses().containsAll(assessments().keySet()),
        "Assessments contained an unknown query; error in constructing the store!");
    checkArgument(queryReponses().equals(queryResponsesToSystemIDs().keySet()));
    final Iterable<String> systemIDStrings = transform(queryResponsesToSystemIDs().values(),
        desymbolizeFunction());
    checkArgument(all(systemIDStrings, not(isEmpty())), "System IDs may not be empty");
    checkArgument(all(systemIDStrings, not(anyCharMatches(CharMatcher.WHITESPACE))),
        "System IDs may not contain whitespace");
  }

  public final CorpusQueryAssessments filterForSystem(final Symbol system) {
    final ImmutableSet<QueryResponse2016> responsesForSystem =
        queryResponsesToSystemIDs().inverse().get(system);
    final CorpusQueryAssessments.Builder ret = CorpusQueryAssessments.builder();
    ret.queryReponses(responsesForSystem);
    ret.putAllQueryResponsesToSystemIDs(
        ImmutableSetMultimap.<Symbol, QueryResponse2016>builder().putAll(system, responsesForSystem)
            .build().inverse());
    ret.putAllMetadata(Maps.filterKeys(metadata(), in(responsesForSystem)));
    ret.putAllAssessments(Maps.filterKeys(assessments(), in(responsesForSystem)));
    return ret.build();
  }

  public final CorpusQueryAssessments filterForAssessment(final QueryAssessment2016 assessment2016) {
    final ImmutableSet.Builder<QueryResponse2016> matchingQueriesB = ImmutableSet.builder();
    for (final QueryResponse2016 queryResponse2016 : assessments().keySet()) {
      if (assessment2016.equals(assessments().get(queryResponse2016))) {
        matchingQueriesB.add(queryResponse2016);
      }
    }
    final ImmutableSet<QueryResponse2016> matchingQueries = matchingQueriesB.build();
    final CorpusQueryAssessments.Builder ret = CorpusQueryAssessments.builder();
    ret.queryReponses(matchingQueries);
    ret.putAllQueryResponsesToSystemIDs(
        Multimaps.filterKeys(queryResponsesToSystemIDs(), in(matchingQueries)));
    ret.putAllMetadata(Maps.filterKeys(metadata(), in(matchingQueries)));
    ret.putAllAssessments(Maps.filterKeys(assessments(), in(matchingQueries)));
    return ret.build();
  }

  public final CorpusQueryAssessments filterForQuery(final Symbol queryId) {
    final ImmutableSet<QueryResponse2016> responsesToKeep = ImmutableSet.copyOf(
        filter(queryReponses(), compose(equalTo(queryId), QueryResponse2016Functions.queryID())));
    final CorpusQueryAssessments.Builder ret = CorpusQueryAssessments.builder();
    ret.queryReponses(responsesToKeep);
    ret.putAllQueryResponsesToSystemIDs(Multimaps.filterKeys(queryResponsesToSystemIDs(), in(responsesToKeep)));
    ret.putAllMetadata(Maps.filterKeys(metadata(), in(responsesToKeep)));
    ret.putAllAssessments(Maps.filterKeys(assessments(), in(responsesToKeep)));
    return ret.build();
  }

  public final CorpusQueryAssessments withNeutralizedJustifications() {
    return CorpusQueryAssessments.builder()
        .queryReponses(Iterables.transform(queryReponses(), neutralizeRealisFunction()))
        .queryResponsesToSystemIDs(
            copyWithTransformedKeys(queryResponsesToSystemIDs(), neutralizeRealisFunction()))
        .assessments(
            reduceToMap(
                transformKeys(assessments(), neutralizeRealisFunction()),
                // if multiple assessments on the same doc are collapsed together,
                // the ones higher in rank "trump" others
                maxFunction(Ordering.<QueryAssessment2016>natural())))
        // we arbitrarily take the first metadata, which is a bit of a hack
        .metadata(reduceToMap(transformKeys(metadata(), neutralizeRealisFunction()),
            _CorpusQueryAssessments.<String>getFirstFunction()))
        .build();
  }

  @MoveToBUECommon
  private static <K1, K2, V> ImmutableSetMultimap<K2, V> copyWithTransformedKeys(
      final Multimap<K1,V> setMultimap,
      final Function<? super K1, ? extends K2> injection) {
    final ImmutableSetMultimap.Builder<K2,V> ret = ImmutableSetMultimap.builder();
    for (final Map.Entry<K1, V> entry : setMultimap.entries()) {
      ret.put(checkNotNull(injection.apply(entry.getKey())), entry.getValue());
    }
    return ret.build();
  }

  @MoveToBUECommon
  private static <K1, K2, V> ImmutableSetMultimap<K2, V> transformKeys(Map<K1, V> input,
      Function<? super K1, ? extends K2> function) {
    final ImmutableSetMultimap.Builder<K2, V> ret = ImmutableSetMultimap.builder();

    for (final Map.Entry<K1, V> entry : input.entrySet()) {
      ret.put(checkNotNull(function.apply(entry.getKey())), entry.getValue());
    }

    return ret.build();
  }

  @MoveToBUECommon
  private static <V> Function<Iterable<? extends V>, V> getFirstFunction() {
    return new Function<Iterable<? extends V>, V>() {
      @Override
      public V apply(final Iterable<? extends V> input) {
        return checkNotNull(Iterables.getFirst(input, null));
      }
    };
  }
}


package com.bbn.kbp.events2014;

import com.bbn.bue.common.TextGroupPublicImmutable;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.base.CharMatcher;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import static com.bbn.bue.common.StringUtils.anyCharMatches;
import static com.bbn.bue.common.StringUtils.isEmpty;
import static com.bbn.bue.common.symbols.SymbolUtils.desymbolizeFunction;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.all;
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
  public abstract ImmutableMultimap<QueryResponse2016, Symbol> queryResponsesToSystemIDs();

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

    for (final QueryResponse2016 q : assessments().keySet()) {
      checkArgument(!assessments().get(q).equals(QueryAssessment2016.UNASSASSED),
          "No storing of UNASSESSED queries in the assessments allowed");
    }
  }
}

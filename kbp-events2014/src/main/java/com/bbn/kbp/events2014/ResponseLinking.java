package com.bbn.kbp.events2014;

import com.bbn.bue.common.symbols.Symbol;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.concat;

public class ResponseLinking {

  private final Symbol docId;
  private final ImmutableSet<ResponseSet> responseSets;
  private final ImmutableSet<Response> incompleteResponses;

  private ResponseLinking(Symbol docId, Iterable<ResponseSet> responseSets,
      Iterable<Response> incompleteResponses) {
    this.docId = checkNotNull(docId);
    this.responseSets = ImmutableSortedSet.copyOf(responseSets);
    this.incompleteResponses = ImmutableSortedSet.copyOf(
        Response.byUniqueIdOrdering(), incompleteResponses);
    checkValidity();
  }

  public static ResponseLinking from(Symbol docId, Iterable<ResponseSet> responseSets,
      Iterable<Response> incompleteResponses) {
    return new ResponseLinking(docId, responseSets, incompleteResponses);
  }

  public static ResponseLinking createEmpty(Symbol docId) {
    return from(docId, ImmutableList.<ResponseSet>of(), ImmutableList.<Response>of());
  }


  public Symbol docID() {
    return docId;
  }

  public ImmutableSet<ResponseSet> responseSets() {
    return responseSets;
  }

  public ImmutableSet<Response> incompleteResponses() {
    return incompleteResponses;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(docId, responseSets, incompleteResponses);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final ResponseLinking other = (ResponseLinking) obj;
    return Objects.equal(this.docId, other.docId)
        && Objects.equal(this.responseSets, other.responseSets)
        && Objects.equal(this.incompleteResponses, incompleteResponses);
  }

  private void checkValidity() {
    // no incomplete response may appear in any response set
    final ImmutableSet<Response> allResponsesInSets = ImmutableSet.copyOf(
        concat(responseSets));
    for (final Response incompleteResponse : incompleteResponses) {
      checkArgument(!allResponsesInSets.contains(incompleteResponse),
          "A response may not be both completed and incomplete");
    }
  }

  public ImmutableSet<Response> allResponses() {
    return ImmutableSet.copyOf(concat(concat(responseSets), incompleteResponses));
  }

  public String toString() {
    return Objects.toStringHelper(this)
        .add("docID", docId)
        .add("responseSets", responseSets)
        .add("incomplete", incompleteResponses).toString();
  }

  public ResponseLinking copyWithFilteredResponses(final Predicate<Response> toKeepCondition) {
    final Set<Response> newIncompletes = Sets.filter(incompleteResponses, toKeepCondition);
    final ImmutableSet.Builder<ResponseSet> newResponseSets = ImmutableSet.builder();
    for (final ResponseSet responseSet : responseSets) {
      final ImmutableSet<Response> okResponses = FluentIterable.from(responseSet.asSet())
          .filter(toKeepCondition).toSet();
      if (!okResponses.isEmpty()) {
        newResponseSets.add(ResponseSet.from(okResponses));
      }
    }
    return ResponseLinking.from(docId, newResponseSets.build(), newIncompletes);
  }
}

package com.bbn.kbp.events2014;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Represents a set of responses.  The default comparison method is to do a lexicographical sort of
 * the unique IDs of the (sorted) responses.
 */
public final class ResponseSet implements Comparable<ResponseSet>, Iterable<Response> {

  private final ImmutableSet<Response> responses;
  private final int cachedHashCode;

  private ResponseSet(Iterable<Response> responses) {
    this.responses = ImmutableSortedSet.copyOf(Response.byUniqueIdOrdering(), responses);
    this.cachedHashCode = computeHashCode();
  }

  public static ResponseSet from(Iterable<Response> responses) {
    return new ResponseSet(responses);
  }

  public static ResponseSet from(Response... responses) {
    return from(Arrays.asList(responses));
  }

  public ImmutableSet<Response> asSet() {
    return responses;
  }

  @Override
  public int hashCode() {
    return cachedHashCode;
  }

  private int computeHashCode() {
    return Objects.hashCode(responses);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final ResponseSet other = (ResponseSet) obj;
    return Objects.equal(this.responses, other.responses);
  }

  private static Function<ResponseSet, ImmutableSet<Response>> asSetFunction() {
    return new Function<ResponseSet, ImmutableSet<Response>>() {
      @Override
      public ImmutableSet<Response> apply(ResponseSet input) {
        return input.asSet();
      }
    };
  }

  @Override
  public int compareTo(ResponseSet o) {
    return Ordering.natural()
        .onResultOf(Response.uniqueIdFunction())
        .lexicographical()
        .onResultOf(asSetFunction())
        .compare(this, o);
  }

  @Override
  public Iterator<Response> iterator() {
    return asSet().iterator();
  }

  public String toString() {
    return responses.toString();
  }
}

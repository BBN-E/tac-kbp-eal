package com.bbn.kbp.events2014;

import com.bbn.bue.common.TextGroupPublicImmutable;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;

import org.immutables.value.Value;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Represents a set of responses.  The default comparison method is to do a lexicographical sort of
 * the unique IDs of the (sorted) responses.
 */
@Value.Immutable
@TextGroupPublicImmutable
abstract class _ResponseSet implements Comparable<ResponseSet>, Iterable<Response> {

  @Value.Parameter
  public abstract ImmutableSet<Response> responses();

  public static ResponseSet from(Iterable<Response> responses) {
    return ResponseSet.of(responses);
  }

  public static ResponseSet from(Response... responses) {
    return from(Arrays.asList(responses));
  }

  public final ImmutableSet<Response> asSet() {
    return responses();
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
  public final int compareTo(ResponseSet o) {
    return Ordering.natural()
        .onResultOf(ResponseFunctions.uniqueIdentifier())
        .lexicographical()
        .onResultOf(asSetFunction())
        .compare((ResponseSet) this, o);
  }

  @Override
  public final Iterator<Response> iterator() {
    return asSet().iterator();
  }

}

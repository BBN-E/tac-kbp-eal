package com.bbn.kbp.events2014;

import com.bbn.bue.common.TextGroupImmutable;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Represents a set of responses.  The default comparison method is to do a lexicographical sort of
 * the unique IDs of the (sorted) responses.
 */
// old code, we don't care if it uses deprecated stuff
@SuppressWarnings("deprecation")
@Value.Immutable(prehash = true)
@TextGroupImmutable
@Functional
public abstract class ResponseSet implements Comparable<ResponseSet>, Iterable<Response> {

  public abstract ImmutableSet<Response> responses();

  public static ResponseSet from(Iterable<Response> responses) {
    return ImmutableResponseSet.builder().responses(responses).build();
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

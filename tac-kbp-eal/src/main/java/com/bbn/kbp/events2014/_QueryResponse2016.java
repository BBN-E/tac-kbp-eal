package com.bbn.kbp.events2014;


import com.bbn.bue.common.TextGroupPublicImmutable;
import com.bbn.bue.common.symbols.Symbol;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import java.util.SortedSet;

import static com.google.common.base.Preconditions.checkState;

/**
 * A system's indication that the indicated document matches the indicated query. The {@link
 * #predicateJustifications()}s are the text which justifies this claim.
 */
@TextGroupPublicImmutable
@Value.Immutable
@Functional
abstract class _QueryResponse2016 {

  @Value.Parameter
  public abstract Symbol queryID();

  @Value.Parameter
  public abstract Symbol docID();

  @Value.Parameter
  @Value.NaturalOrder
  public abstract SortedSet<CharOffsetSpan> predicateJustifications();

  @Value.Check
  protected void check() {
    checkState(!queryID().asString().isEmpty(), "Empty query IDs not allowed!");
    checkState(!docID().asString().isEmpty(), "Empty doc IDs not allowed!");
    checkState(!queryID().asString().contains("\t"), "Tabs disallowed from query IDs");
    checkState(!docID().asString().contains("\t"), "Tabs disallowed from doc IDs");
    checkState(predicateJustifications().size() > 0, "Must provide PredicateJustifications!");
  }
}

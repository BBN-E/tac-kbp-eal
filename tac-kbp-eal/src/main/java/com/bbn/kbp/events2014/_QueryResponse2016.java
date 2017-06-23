package com.bbn.kbp.events2014;


import com.bbn.bue.common.symbols.Symbol;

import com.google.common.base.Function;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import java.util.SortedSet;

import static com.google.common.base.Preconditions.checkState;

/**
 * A system's indication that the indicated document matches the indicated query. The {@link
 * #predicateJustifications()}s are the text which justifies this claim.
 */
// old code, we don't care if it uses deprecated stuff
@SuppressWarnings("deprecation")
@com.bbn.bue.common.TextGroupPublicImmutable
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

  /**
   * Return a copy of this query response with the justification set to a dummy value. This is
   * used when running the scorer with the optional to ignore justifications.
   */
  public final QueryResponse2016 withNeutralizedJustification() {
    // cast safe because only QueryResponse2016 instances will ever exist
    return ((QueryResponse2016)this).withPredicateJustifications(dummyJustification());
  }

  /**
   * Dummy value used for the justification when scoring while ignoring justifications.
   */
  public static CharOffsetSpan dummyJustification() {
    return CharOffsetSpan.fromOffsetsOnly(0, 0);
  }

  @Value.Check
  protected void check() {
    checkState(!queryID().asString().isEmpty(), "Empty query IDs not allowed!");
    checkState(!docID().asString().isEmpty(), "Empty doc IDs not allowed!");
    checkState(!queryID().asString().contains("\t"), "Tabs disallowed from query IDs");
    checkState(!docID().asString().contains("\t"), "Tabs disallowed from doc IDs");
    checkState(predicateJustifications().size() > 0, "Must provide PredicateJustifications!");
  }

  public static Function<QueryResponse2016, QueryResponse2016> neutralizeRealisFunction() {
    return NeutralizeRealisFunction.INSTANCE;
  }

  private enum NeutralizeRealisFunction implements Function<QueryResponse2016, QueryResponse2016> {
    INSTANCE;

    @Override
    public QueryResponse2016 apply(final QueryResponse2016 input) {
      return input.withNeutralizedJustification();
    }
  }
}

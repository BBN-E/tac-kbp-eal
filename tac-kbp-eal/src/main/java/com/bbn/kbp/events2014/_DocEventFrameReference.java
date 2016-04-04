package com.bbn.kbp.events2014;

import com.bbn.bue.common.TextGroupPublicImmutable;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.bue.common.symbols.SymbolUtils;

import com.google.common.base.Function;
import com.google.common.collect.Ordering;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents a reference to an event frame in a document.
 */
@Value.Immutable
@Functional
@TextGroupPublicImmutable
abstract class _DocEventFrameReference {

  @Value.Parameter
  public abstract Symbol docID();

  @Value.Parameter
  public abstract String eventFrameID();

  @Value.Check
  protected void checkPreconditions() {
    checkArgument(!docID().asString().isEmpty());
    checkArgument(!docID().asString().contains("-"));
    checkArgument(!docID().asString().contains("\t"));
    checkArgument(!eventFrameID().isEmpty());
    checkArgument(!eventFrameID().contains("-"));
    checkArgument(!eventFrameID().contains("\t"));
  }

  public static Ordering<DocEventFrameReference> canonicalOrdering() {
    return SymbolUtils.byStringOrdering().onResultOf(DocEventFrameReferenceFunctions.docID())
        .compound(Ordering.natural().onResultOf(DocEventFrameReferenceFunctions.eventFrameID()));
  }

  public static Function<DocEventFrameReference, String> canonicalStringFunction() {
    return CanonicalStringFunction.INSTANCE;
  }

  enum CanonicalStringFunction implements Function<DocEventFrameReference, String> {
    INSTANCE {
      @Override
      public String apply(final DocEventFrameReference input) {
        checkNotNull(input);
        return input.docID().asString() + "-" + input.eventFrameID();
      }
    }
  }
}

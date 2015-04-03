package com.bbn.kbp.events2014;

import com.bbn.bue.common.symbols.Symbol;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class EventArgumentLinking {

  private final Symbol docID;
  private final ImmutableSet<TypeRoleFillerRealisSet> coreffedArgSets;
  private final ImmutableSet<TypeRoleFillerRealis> incomplete;
  private static Logger log = LoggerFactory.getLogger(EventArgumentLinking.class);

  private EventArgumentLinking(Symbol docID, Iterable<TypeRoleFillerRealisSet> coreffedArgSets,
      Iterable<TypeRoleFillerRealis> incomplete) {
    this.docID = checkNotNull(docID);
    this.coreffedArgSets = ImmutableSet.copyOf(coreffedArgSets);
    this.incomplete = ImmutableSet.copyOf(incomplete);
    for (final TypeRoleFillerRealisSet trfrSet : coreffedArgSets) {
      final Set<TypeRoleFillerRealis> intersection =
          Sets.intersection(trfrSet.asSet(), this.incomplete);
      checkArgument(intersection.isEmpty(), "A TRFR cannot be both incomplete and linked: %s",
          intersection);
    }
  }

  public static EventArgumentLinking create(Symbol docID,
      Iterable<TypeRoleFillerRealisSet> coreffedArgSets,
      Iterable<TypeRoleFillerRealis> incomplete) {
    return new EventArgumentLinking(docID, coreffedArgSets, incomplete);
  }

  public Symbol docID() {
    return docID;
  }

  public ImmutableSet<TypeRoleFillerRealisSet> linkedAsSet() {
    return coreffedArgSets;
  }

  public ImmutableSet<TypeRoleFillerRealis> incomplete() {
    return incomplete;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(docID, coreffedArgSets, incomplete);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final EventArgumentLinking other = (EventArgumentLinking) obj;
    return Objects.equal(this.docID, other.docID) && Objects
        .equal(this.coreffedArgSets, other.coreffedArgSets)
        && Objects.equal(this.incomplete, other.incomplete);
  }

  public static EventArgumentLinking createMinimalLinkingFrom(AnswerKey answerKey) {
    final Function<Response, TypeRoleFillerRealis> ToEquivalenceClass =
        TypeRoleFillerRealis.extractFromSystemResponse(
            answerKey.corefAnnotation().strictCASNormalizerFunction());

    log.info("creating minimal linking for {} responses", answerKey.annotatedResponses().size());

    return EventArgumentLinking.create(answerKey.docId(),
        ImmutableSet.<TypeRoleFillerRealisSet>of(),
        ImmutableSet.copyOf(FluentIterable.from(answerKey.annotatedResponses())
            .transform(AssessedResponse.Response)
            .transform(ToEquivalenceClass)));
  }

}

package com.bbn.kbp;

import com.bbn.bue.common.HasDocID;
import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

import org.immutables.value.Value;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * An assertion that an event has a particular argument. The format of this assertion is
 * "[EventNodeID] [EventType:Role.Realis] [EntityNodeID or StringNodeID] [provenances] (confidence)".
 * For a full list of valid event predicates, see the Event predicates subsection of section 2.10
 * List of Predicates in the 2017 revisions to the KBP TAC 2016 ColdStart task description.
 * (Note: this table does not include the realis in the predicates, which must be appended with ".")
 * <p>
 * This class does not cover inverse event argument assertions of the format "[EntityNodeID]
 * [EntityType:EventType_Role.Realis] [EventNodeID]".
 */
@TextGroupImmutable
@Value.Immutable
public abstract class EventArgumentAssertion implements Assertion, HasDocID {

  @Override
  public abstract EventNode subject();

  @Override
  @Value.Derived
  public Set<Node> allNodes() {
    return ImmutableSet.of(subject(), argument().asNode());
  }

  @Override
  public final Symbol docID() {
    return baseFiller().documentId();
  }

  /**
   * Only if the argument is a string argument. Guidelines p. 18
   */
  public abstract Optional<JustificationSpan> fillerString();

  public abstract JustificationSpan baseFiller();

  public abstract ImmutableSet<JustificationSpan> predicateJustification();

  public abstract ImmutableSet<JustificationSpan> additionalJustifications();

  public abstract EventArgument argument();

  public abstract Symbol eventType();

  public abstract Symbol role();

  public abstract Symbol realis();

  public static Builder builder() {
    return new Builder();
  }

  // enforce constraints from guidelines p. 18
  @Value.Check
  protected void check() {
    // filler string is used only for string node arguments
    checkArgument(fillerString().isPresent() == argument().stringNode().isPresent());
    checkArgument(predicateJustification().size() >= 1);
    checkArgument(predicateJustification().size() <= 3);
    checkAllJustificationsComeFromASingleDocument();
  }

  private void checkAllJustificationsComeFromASingleDocument() {
    final ImmutableSet.Builder<Symbol> docIdsB = ImmutableSet.builder();
    if (fillerString().isPresent()) {
      docIdsB.add(fillerString().get().documentId());
    }
    docIdsB.add(baseFiller().documentId());
    for (final JustificationSpan pj : predicateJustification()) {
      docIdsB.add(pj.documentId());
    }
    for (final JustificationSpan aj : additionalJustifications()) {
      docIdsB.add(aj.documentId());
    }
    final ImmutableSet<Symbol> docIDs = docIdsB.build();
    checkArgument(docIDs.size() == 1,
        "All justifications must come from a single document but saw %s",
        docIDs);
  }

  public static class Builder extends ImmutableEventArgumentAssertion.Builder {

    public final Builder argument(EntityNode node) {
      this.argument(EventArgument.of(node));
      return this;
    }

    public final Builder argument(StringNode node) {
      this.argument(EventArgument.of(node));
      return this;
    }

  }

}


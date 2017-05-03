package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.symbols.Symbol;

import org.immutables.value.Value;

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
public abstract class EventArgumentAssertion implements ProvenancedAssertion {

  @Override
  public abstract EventNode subject();

  public abstract EventArgument argument();

  public abstract Symbol eventType();

  public abstract Symbol role();

  public abstract Symbol realis();

  public static Builder builder() {
    return new Builder();
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

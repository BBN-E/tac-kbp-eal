package com.bbn.kbp;

import com.bbn.bue.common.HasDocID;
import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.collect.ImmutableSet;

import org.immutables.value.Value;

/**
 * An inverse argument assertion with a entity-valued argument.
 */
@TextGroupImmutable
@Value.Immutable
public abstract class EntityInverseEventArgumentAssertion implements Assertion, HasDocID {

  @Override
  public abstract EntityNode subject();

  public abstract EventNode eventNode();

  public abstract Symbol eventType();

  public abstract Symbol role();

  public abstract Symbol realis();

  public abstract Symbol subjectEntityType();

  public abstract JustificationSpan baseFiller();

  public abstract ImmutableSet<JustificationSpan> predicateJustification();

  public abstract ImmutableSet<JustificationSpan> additionalJustifications();

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder extends ImmutableEntityInverseEventArgumentAssertion.Builder {

  }
}

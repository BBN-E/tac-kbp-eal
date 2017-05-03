package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.symbols.Symbol;

import org.immutables.value.Value;

/**
 * An assertion specifying the specific type of an entity. The format of this assertion is
 * "[EntityNodeID] type {PER,ORG,GPE,LOC,FAC}". Type assertions do not have provenances.
 * Each entity node must have exactly one type assertion.
 */
@TextGroupImmutable
@Value.Immutable
public abstract class EntityTypeAssertion implements Assertion {

  @Override
  public abstract EntityNode subject();

  public abstract Symbol entityType();

  public static EntityTypeAssertion of(final EntityNode subject, final Symbol entityType) {
    return ImmutableEntityTypeAssertion.builder().subject(subject).entityType(entityType).build();
  }

}

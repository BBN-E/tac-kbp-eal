package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;

import com.google.common.collect.ImmutableSet;

import org.immutables.value.Value;

import java.util.Set;

/**
 * An assertion that an entity is mentioned in a particular location in text. The format of this
 * assertion is "[EntityNodeID] mention [QuotedString mention] [provenances] (confidence)".
 * Each entity node must have at least one mention or nominal mention.
 */
@TextGroupImmutable
@Value.Immutable
public abstract class EntityMentionAssertion implements MentionAssertion {

  @Override
  public abstract EntityNode subject();

  @Value.Derived
  public Set<Node> allNodes() {
    return ImmutableSet.<Node>of(subject());
  }

  public static EntityMentionAssertion of(final EntityNode subject, final String mention,
      final Set<Provenance> provenances) {

    return ImmutableEntityMentionAssertion.builder()
        .subject(subject)
        .mention(mention)
        .provenances(provenances)
        .build();
  }

}

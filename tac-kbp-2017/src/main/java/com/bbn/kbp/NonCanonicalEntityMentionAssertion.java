package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;

import com.google.common.collect.ImmutableSet;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import java.util.Set;

/**
 * An assertion that an entity is mentioned in a particular location in text. The format of this
 * assertion is "[EntityNodeID] mention [QuotedString mention] [provenances] (confidence)".
 * Each entity node must have at least one mention or nominal mention.
 */
@TextGroupImmutable
@Value.Immutable
@Functional
public abstract class NonCanonicalEntityMentionAssertion extends MentionAssertion
    implements EntityMentionAssertion {

  @Override
  public abstract EntityNode subject();

  @Value.Derived
  @Override
  public Set<Node> allNodes() {
    return ImmutableSet.<Node>of(subject());
  }

  public static NonCanonicalEntityMentionAssertion of(final EntityNode subject,
      final String mention,
      final JustificationSpan predicateJustification) {

    return ImmutableNonCanonicalEntityMentionAssertion.builder()
        .subject(subject)
        .mention(mention)
        .predicateJustification(predicateJustification)
        .build();
  }

}


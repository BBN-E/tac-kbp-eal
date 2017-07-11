package com.bbn.kbp;

import com.bbn.bue.common.HasDocID;
import com.bbn.bue.common.TextGroupImmutable;

import com.google.common.collect.ImmutableSet;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import java.util.Set;

/**
 * An assertion that a particular mention of an entity is its canonical mention. The format of this
 * assertion is "[EntityNodeID] canonical_mention [QuotedString mention] [provenances] (confidence)".
 * Each entity node must have exactly one canonical mention per document, which corresponds to some
 * other non-canonical mention or nominal mention (i.e. the subject, object, and provenances must
 * all match exactly those of some other mention or nominal mention).
 */
@TextGroupImmutable
@Value.Immutable
@Functional
public abstract class EntityCanonicalMentionAssertion extends MentionAssertion
    implements EntityMentionAssertion, HasSinglePredicateJustification,
    HasDocID {

  @Override
  public abstract EntityNode subject();

  @Override
  public abstract JustificationSpan predicateJustification();

  @Override
  @Value.Derived
  public Set<Node> allNodes() {
    return ImmutableSet.<Node>of(subject());
  }

  public static EntityCanonicalMentionAssertion of(final EntityNode subject, final String mention,
      final JustificationSpan predicateJustification) {

    return ImmutableEntityCanonicalMentionAssertion.builder()
        .subject(subject)
        .mention(mention)
        .predicateJustification(predicateJustification)
        .build();
  }

}



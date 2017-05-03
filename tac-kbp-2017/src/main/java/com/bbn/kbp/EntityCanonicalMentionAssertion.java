package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;

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
public abstract class EntityCanonicalMentionAssertion implements MentionAssertion {

  @Override
  public abstract EntityNode subject();

  public static EntityCanonicalMentionAssertion of(final EntityNode subject, final String mention,
      final Set<Provenance> provenances) {

    return ImmutableEntityCanonicalMentionAssertion.builder()
        .subject(subject)
        .mention(mention)
        .provenances(provenances)
        .build();
  }

}

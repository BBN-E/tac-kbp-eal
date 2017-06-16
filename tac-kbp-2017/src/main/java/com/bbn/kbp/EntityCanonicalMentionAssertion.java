package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import java.util.Set;

import static com.bbn.kbp.ProvenanceFunctions.documentId;
import static com.google.common.base.Preconditions.checkArgument;

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
public abstract class EntityCanonicalMentionAssertion implements MentionAssertion {

  @Override
  public abstract EntityNode subject();

  @Value.Derived
  public Set<Node> allNodes() {
    return ImmutableSet.<Node>of(subject());
  }

  @Value.Derived
  public Symbol docId() {
    return Iterables.getOnlyElement(FluentIterable.from(provenances())
        .transform(documentId()));
  }

  @Value.Check
  protected void check() {
    checkArgument(FluentIterable.from(provenances())
            .transform(documentId())
            .size() == 1,
        "All EntityCanonicalMentionAssertion provenances must come from a single document ID");
  }

  public static EntityCanonicalMentionAssertion of(final EntityNode subject, final String mention,
      final Set<Provenance> provenances) {

    return ImmutableEntityCanonicalMentionAssertion.builder()
        .subject(subject)
        .mention(mention)
        .provenances(provenances)
        .build();
  }

}

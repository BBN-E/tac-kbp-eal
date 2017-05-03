package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;

import org.immutables.value.Value;

import java.util.Set;

/**
 * An assertion of the Timex2 normalized form of a string node. The format of this assertion is
 * "[StringNodeID] normalized_mention [QuotedString mention] [provenances] (confidence)".
 * The mention string of a normalized mention (the object of the assertion) must be a Timex2
 * formatted date or time and must correspond to some other non-normalized mention of that string
 * node (i.e. the subject and provenances must match exactly those of another mention; the object
 * need not match as it is a Timex2 normalized form of the original mention string).
 */
@TextGroupImmutable
@Value.Immutable
public abstract class NormalizedMentionAssertion implements MentionAssertion {

  @Override
  public abstract StringNode subject();

  public static NormalizedMentionAssertion of(final StringNode subject, final String mention,
      final Set<Provenance> provenances) {

    return ImmutableNormalizedMentionAssertion.builder()
        .subject(subject)
        .mention(mention)
        .provenances(provenances)
        .build();
  }

}

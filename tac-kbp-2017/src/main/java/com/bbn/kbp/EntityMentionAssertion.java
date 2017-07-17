package com.bbn.kbp;

import com.bbn.bue.common.HasDocID;

import com.google.common.base.Function;

/**
 * An assertion of a mention of an entity, whether canonical or non-canonical.
 */
public interface EntityMentionAssertion extends HasDocID {
  EntityNode subject();

  JustificationSpan predicateJustification();

  enum SubjectFunction implements Function<EntityMentionAssertion, EntityNode> {
    INSTANCE;

    @Override
    public EntityNode apply(final EntityMentionAssertion input) {
      return input.subject();
    }
  }
}

package com.bbn.kbp;

import com.google.common.base.Function;

/**
 * An assertion of a mention of an entity, whether canonical or non-canonical.
 */
public interface EntityMentionAssertion extends MentionAssertion {
  @Override
  EntityNode subject();

  enum SubjectFunction implements Function<EntityMentionAssertion, EntityNode> {
    INSTANCE;

    @Override
    public EntityNode apply(final EntityMentionAssertion input) {
      return input.subject();
    }
  }
}

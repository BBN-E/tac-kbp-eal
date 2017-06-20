package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;

import com.google.common.base.Optional;

import org.immutables.value.Value;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Created by rgabbard on 6/20/17.
 */
@TextGroupImmutable
@Value.Immutable
public abstract class RelationArgument {

  abstract Optional<EntityNode> entityNode();

  abstract Optional<StringNode> stringNode();

  public final Node asNode() {
    if (entityNode().isPresent()) {
      return entityNode().get();
    } else if (stringNode().isPresent()) {
      return stringNode().get();
    } else {
      throw new RuntimeException("Can't happen by check()");
    }
  }

  @Value.Check
  protected void check() {
    checkArgument(entityNode().isPresent() != stringNode().isPresent(),
        "Exactly one of entity node and string node must be present for e relation argument, "
            + "but got entity node %s and string node %s", entityNode(), stringNode());
  }

  public static RelationArgument of(final EntityNode entityNode) {
    return ImmutableRelationArgument.builder().entityNode(entityNode).build();
  }

  public static RelationArgument of(final StringNode stringNode) {
    return ImmutableRelationArgument.builder().stringNode(stringNode).build();
  }

}

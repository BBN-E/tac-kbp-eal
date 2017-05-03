package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;

import com.google.common.base.Optional;

import org.immutables.value.Value;

import static com.google.common.base.Preconditions.checkArgument;

@TextGroupImmutable
@Value.Immutable
public abstract class EventArgument {
  abstract Optional<EntityNode> entityNode();
  abstract Optional<StringNode> stringNode();

  public final Node asKbpNode() {
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
        "Exactly one of entity node and string node must be present for an "
            + "event argument, but got entity node %s and string node %s",
        entityNode(), stringNode());
  }

  public static EventArgument of(final EntityNode entityNode) {
    return ImmutableEventArgument.builder().entityNode(entityNode).build();
  }

  public static EventArgument of(final StringNode stringNode) {
    return ImmutableEventArgument.builder().stringNode(stringNode).build();
  }

}

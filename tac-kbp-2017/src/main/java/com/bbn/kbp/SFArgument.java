package com.bbn.kbp;

import com.bbn.bue.common.TextGroupImmutable;

import com.google.common.base.Optional;

import org.immutables.value.Value;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The object of an {@code SFAssertion} which can either be an {@code EntityNode} or a
 * {@code StringNode}.
 */
@TextGroupImmutable
@Value.Immutable
public abstract class SFArgument {

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
        "Exactly one of entity node and string node must be present for an event argument, "
            + "but got entity node %s and string node %s", entityNode(), stringNode());
  }

  public static SFArgument of(final EntityNode entityNode) {
    return ImmutableSFArgument.builder().entityNode(entityNode).build();
  }

  public static SFArgument of(final StringNode stringNode) {
    return ImmutableSFArgument.builder().stringNode(stringNode).build();
  }

}

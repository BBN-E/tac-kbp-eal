package com.bbn.kbp;

import com.google.common.base.Optional;

/**
 * Created by jmolina on 5/3/17.
 */
class Kbp2017Argument {
  private final Optional<Kbp2017EntityNode> entityNode;
  private final Optional<Kbp2017StringNode> stringNode;

  private Kbp2017Argument(final Kbp2017EntityNode entityNode) {
    this.entityNode = Optional.of(entityNode);
    this.stringNode = Optional.absent();
  }

  private Kbp2017Argument(final Kbp2017StringNode stringNode) {
    this.stringNode = Optional.of(stringNode);
    this.entityNode = Optional.absent();
  }

  static Kbp2017Argument of(final Kbp2017EntityNode entityNode) {
    return new Kbp2017Argument(entityNode);
  }

  static Kbp2017Argument of(final Kbp2017StringNode stringNode) {
    return new Kbp2017Argument(stringNode);
  }

  Kbp2017Node asKbpNode() {
    if (entityNode.isPresent()) {
      return entityNode.get();
    } else if (stringNode.isPresent()) {
      return stringNode.get();
    } else {
      throw new RuntimeException();
    }
  }
}

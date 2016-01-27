package com.bbn.nlp.parsing;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.nlp.ConstituentNode;

import com.google.common.annotations.Beta;
import com.google.common.base.Optional;

/**
 * An interface for finding
 */
@Beta
public interface HeadFinder<NodeT extends ConstituentNode<NodeT, ?>> {
  Optional<NodeT> findHead(final Symbol tag, final Iterable<NodeT> children);

  class HeadException extends RuntimeException {

    public HeadException(final String msg) {
      super(msg);
    }
  }
}

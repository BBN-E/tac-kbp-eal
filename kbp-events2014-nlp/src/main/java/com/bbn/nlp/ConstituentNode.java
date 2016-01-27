package com.bbn.nlp;


import com.bbn.bue.common.symbols.Symbol;
import com.bbn.nlp.corenlp.CoreNLPParseNode;

import com.google.common.annotations.Beta;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * When constructing ConstituentNodes, check that the terminal(), children(), and nodeData() are all
 * consistent with the specific implementations, e.g. for {@link CoreNLPParseNode}
 * we have no NodeDataT on anything except terminal nodes.
 */
@Beta
public interface ConstituentNode<SelfT, NodeDataT> {

  /**
   * An all upper case (as defined by the output of {@code String.ToUpperCase()}) parse tag. This is not intended to be invertible.
   * @return
   */
  Symbol tag();

  Optional<SelfT> parent();

  Optional<SelfT> immediateHead();

  ImmutableList<SelfT> children();

  Optional<NodeDataT> nodeData();

  /**
   * Must be equivalent to children().isEmpty().
   */
  boolean terminal();
}

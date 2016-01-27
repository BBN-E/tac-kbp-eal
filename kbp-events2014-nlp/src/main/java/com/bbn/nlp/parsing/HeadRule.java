package com.bbn.nlp.parsing;

import com.bbn.nlp.ConstituentNode;

import com.google.common.annotations.Beta;
import com.google.common.base.Optional;

@Beta
interface HeadRule<NodeT extends ConstituentNode<NodeT, ?>> {

  Optional<NodeT> matchForChildren(final Iterable<NodeT> children);
}

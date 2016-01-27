package com.bbn.nlp.parsing;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.nlp.ConstituentNode;

import com.google.common.annotations.Beta;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A head finder which stores a head finding rule for each parent symbol using a {@link
 * java.util.Map}.
 */
@Beta
final class MapHeadFinder<NodeT extends ConstituentNode<NodeT, ?>>
    implements HeadFinder<NodeT> {

  private final ImmutableMap<Symbol, HeadRule<NodeT>> rules;

  private MapHeadFinder(final Map<Symbol, HeadRule<NodeT>> rules) {
    this.rules = ImmutableMap.copyOf(rules);
  }

  public static <NodeT extends ConstituentNode<NodeT, ?>> HeadFinder<NodeT> create(
      final Map<Symbol, HeadRule<NodeT>> parentSymbolsToHeadRules) {
    return new MapHeadFinder<>(parentSymbolsToHeadRules);
  }


  @Override
  public Optional<NodeT> findHead(final Symbol tag,
      final Iterable<NodeT> childNodes) {
    final Optional<HeadRule<NodeT>> rule = productionRule(tag);
    if (rule.isPresent()) {
      return rule.get().matchForChildren(childNodes);
    } else {
      final ImmutableList<NodeT> nodes = ImmutableList.copyOf(childNodes);
      if (nodes.size() == 1) {
        return Optional.of(nodes.get(0));
      } else {
        return Optional.absent();
      }
    }
  }

  private final Optional<HeadRule<NodeT>> productionRule(final Symbol tag) {
    checkNotNull(tag);
    if (!rules.containsKey(tag)) {
      // TODO: What behavior do we really want here?
      throw new HeadException("Failed to find a rule for " + tag + ", have " + rules);

    }
    return Optional.of(rules.get(tag));
  }
}

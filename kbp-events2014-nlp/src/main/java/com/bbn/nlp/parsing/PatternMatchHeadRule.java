package com.bbn.nlp.parsing;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.nlp.ConstituentNode;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import java.util.List;
import java.util.Map;

@Beta
final class PatternMatchHeadRule<NodeT extends ConstituentNode<NodeT, ?>>
    implements HeadRule<NodeT> {

  private final boolean leftToRight;
  private final ImmutableList<String> candidateHeadSymbols;

  private static final String LEFTTORIGHT = "1";
  private static final String RIGHTTOLEFT = "0";

  private PatternMatchHeadRule(final boolean leftToRight,
      final List<String> candidateHeadSymbols) {
    this.leftToRight = leftToRight;
    this.candidateHeadSymbols = ImmutableList.copyOf(candidateHeadSymbols);
  }

  public static <NodeT extends ConstituentNode<NodeT, ?>> PatternMatchHeadRule<NodeT> create(
      final boolean leftToRight,
      List<String> candidateHeadSymbols) {
    return new PatternMatchHeadRule<NodeT>(leftToRight, candidateHeadSymbols);
  }

  public boolean leftToRight() {
    return leftToRight;
  }

  @Override
  public Optional<NodeT> matchForChildren(
      final Iterable<NodeT> childNodes) {
    // get the children in the right order for this head rule
    final ImmutableList<NodeT> children;
    if (leftToRight()) {
      children = ImmutableList.copyOf(childNodes);
    } else {
      children = ImmutableList.copyOf(childNodes).reverse();
    }

    for (final NodeT child : children) {
      for (final String tag : candidateHeadSymbols) {
        if (child.tag().asString().matches(tag)) {
          return Optional.of(child);
        }
      }
    }

    if(candidateHeadSymbols.size() == 0) {
      return Optional.fromNullable(Iterables.getFirst(children, null));
    }

    return Optional.absent();
  }

  public static <NodeT extends ConstituentNode<NodeT, ?>> Function<String, Map.Entry<Symbol, HeadRule<NodeT>>> fromHeadRuleFileLine() {
    return new Function<String, Map.Entry<Symbol, HeadRule<NodeT>>>() {
      @Override
      public Map.Entry<Symbol, HeadRule<NodeT>> apply(final String input) {
        final ImmutableList<String> lineParts = ImmutableList.copyOf(input.trim().split("\\s+"));

        final Symbol parent = Symbol.from(lineParts.get(0));
        final boolean leftToRight;
        if (lineParts.get(1).equals(LEFTTORIGHT)) {
          leftToRight = true;
        } else if (lineParts.get(1).equals(RIGHTTOLEFT)) {
          leftToRight = false;
        } else {
          throw new RuntimeException(
              "Unexpected direction to move for head rule; options are 1 and 0 for left to right and visa versa, got "
                  + lineParts.get(1));
        }
        final HeadRule<NodeT> rule =
            create(leftToRight, lineParts.subList(2, lineParts.size()));
        return
            new Map.Entry<Symbol, HeadRule<NodeT>>() {

              @Override
              public Symbol getKey() {
                return parent;
              }

              @Override
              public HeadRule<NodeT> getValue() {
                return rule;
              }

              @Override
              public HeadRule<NodeT> setValue(final HeadRule<NodeT> value) {
                throw new UnsupportedOperationException("Cannot set the value here");
              }
            };
      }
    };
  }
}

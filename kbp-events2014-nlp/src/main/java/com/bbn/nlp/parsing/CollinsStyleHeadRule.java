package com.bbn.nlp.parsing;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.bue.common.symbols.SymbolUtils;
import com.bbn.nlp.ConstituentNode;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import java.util.AbstractMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * English style head rules following the implementation referenced in @{code
 * CollinsStylePTBHeadFinder}
 */
@Beta
final class CollinsStyleHeadRule<NodeT extends ConstituentNode<NodeT, ?>>
    implements HeadRule<NodeT> {

  private static final Symbol LEFTTORIGHT = Symbol.from("1");
  private static final Symbol RIGHTTOLEFT = Symbol.from("0");
  static final Symbol WILDCARD = Symbol.from("**");
  static final Symbol UNKNOWN = Symbol.from("X");


  private final boolean leftToRight;
  private final boolean headInitial;
  private final ImmutableList<Symbol> candidateHeadSymbols;

  private CollinsStyleHeadRule(final boolean leftToRight,
      final ImmutableList<Symbol> candidateHeadSymbols, final boolean headInitial) {
    this.leftToRight = leftToRight;
    this.headInitial = headInitial;
    this.candidateHeadSymbols = checkNotNull(candidateHeadSymbols);
  }

  public static <NodeT extends ConstituentNode<NodeT, ?>> CollinsStyleHeadRule<NodeT> create(
      final boolean leftToRight,
      final ImmutableList<Symbol> candidateHeadSymbols, final boolean headInitial) {
    return new CollinsStyleHeadRule<NodeT>(leftToRight, candidateHeadSymbols, headInitial);
  }

  public boolean leftToRight() {
    return leftToRight;
  }

  @Override
  public Optional<NodeT> matchForChildren(
      final Iterable<NodeT> childNodes) {
    final ImmutableList<NodeT> children;
    if (leftToRight()) {
      children = ImmutableList.copyOf(childNodes);
    } else {
      children = ImmutableList.copyOf(childNodes).reverse();
    }

//    // pick anything for something known or a wildcard
//    if (n.tag().equalTo(UNKNOWN)) {
//      return Optional.of(chooseForWildCardMatch(n));
//    }

    for (final Symbol s : candidateHeadSymbols) {
      if (s.equalTo(WILDCARD)) {
        return Optional.of(chooseForWildCardMatch(children));
      }
      for (final NodeT child : children) {
        if (child.tag().equalTo(s)) {
          return Optional.of(child);
        }
      }
    }

    // if we have no match rule, then just pick the first thing in the correct direction
    if(candidateHeadSymbols.size() == 0) {
      return Optional.fromNullable(Iterables.getFirst(children, null));
    }

    return Optional.absent();
  }

  private NodeT chooseForWildCardMatch(final ImmutableList<NodeT> children) {
    if (headInitial) {
      return children.get(0);
    } else {
      return children.get(children.size() - 1);
    }
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CollinsStyleHeadRule that = (CollinsStyleHeadRule) o;
    return leftToRight == that.leftToRight &&
        this.headInitial == that.headInitial &&
        Objects.equal(candidateHeadSymbols, that.candidateHeadSymbols);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(leftToRight, headInitial, candidateHeadSymbols);
  }

  public static <NodeT extends ConstituentNode<NodeT, ?>> Function<String, Map.Entry<Symbol, HeadRule<NodeT>>> fromHeadRuleFileLine(
      final boolean headInitial) {
    return new Function<String, Map.Entry<Symbol, HeadRule<NodeT>>>() {
      @Override
      public Map.Entry<Symbol, HeadRule<NodeT>> apply(final String input) {
        final ImmutableList<Symbol> lineParts =
            FluentIterable.from(ImmutableList.copyOf(input.trim().split("\\s+")))
                .transform(SymbolUtils.symbolizeFunction()).toList();
        final Symbol parent = lineParts.get(0);
        final boolean leftToRight;
        if (lineParts.get(1).equalTo(LEFTTORIGHT)) {
          leftToRight = true;
        } else if (lineParts.get(1).equalTo(RIGHTTOLEFT)) {
          leftToRight = false;
        } else {
          throw new RuntimeException(
              "Unexpected direction to move for head rule; options are 1 and 0 for left to right and visa versa, got "
                  + lineParts.get(1));
        }
        final HeadRule<NodeT> rule =
            create(leftToRight, lineParts.subList(2, lineParts.size()), headInitial);
        return new AbstractMap.SimpleImmutableEntry<>(parent, rule);
      }
    };
  }

  public static class UNKHeadRule<NodeT extends ConstituentNode<NodeT, ?>>
      implements HeadRule<NodeT> {

    private final boolean headInitial;

    private UNKHeadRule(final boolean headInitial) {
      this.headInitial = headInitial;
    }

    public static <NodeT extends ConstituentNode<NodeT, ?>> UNKHeadRule<NodeT> create(final boolean headInitial) {
      return new UNKHeadRule<>(headInitial);
    }

    @Override
    public Optional<NodeT> matchForChildren(final Iterable<NodeT> children) {
      if(headInitial) {
        return Optional.fromNullable(Iterables.getFirst(children, null));
      } else {
        return Optional.of(ImmutableList.copyOf(children).reverse().get(0));
      }
    }
  }
}

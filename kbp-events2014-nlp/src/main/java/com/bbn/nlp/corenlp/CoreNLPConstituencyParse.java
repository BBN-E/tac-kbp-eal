package com.bbn.nlp.corenlp;

import com.bbn.bue.common.collections.RangeUtils;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.nlp.parsing.HeadFinder;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.Stack;

import static com.google.common.base.Preconditions.checkNotNull;

@Beta
public final class CoreNLPConstituencyParse {

  private final ImmutableList<CoreNLPToken> tokens;
  private final CoreNLPParseNode root;
  private final String coreNLPString;

  private CoreNLPConstituencyParse(final ImmutableList<CoreNLPToken> tokens,
      final CoreNLPParseNode root, final String coreNLPString) {
    this.coreNLPString = checkNotNull(coreNLPString);
    this.tokens = checkNotNull(tokens);
    this.root = checkNotNull(root);
  }

  public ImmutableList<CoreNLPToken> tokens() {
    return tokens;
  }

  public CoreNLPParseNode root() {
    return root;
  }

  /**
   * @return StanfordParseNodes in a pre-order Depth First Search.
   */
  public Iterable<CoreNLPParseNode> preorderTraversal() {
    return root.preorderTraversal();
  }

  public String coreNLPString() {
    return coreNLPString;
  }

  static CoreNLPConstituencyParse create(final HeadFinder<CoreNLPParseNode> headFinder,
      final ImmutableList<CoreNLPToken> tokens,
      final String rawParse, final boolean stripFunctionTags) {
    // remove empty nodes so they are recognized as children.
    final String parse = rawParse.replaceAll("\\(\\)", "");
    // TODO: consider refactoring this cruft into a PTBParseParser
    // find all open and close boundaries
    final ImmutableList<Range<Integer>> ranges = findAllOpenCloseParens(parse);
    final ImmutableMap<Range<Integer>, Range<Integer>> childrenToParent = childrenToParent(ranges);
    final ImmutableMultimap<Range<Integer>, Range<Integer>> parentToChildren =
        childrenToParent.asMultimap().inverse();

    // find and build terminals
    final ImmutableSet<Range<Integer>> terminals = findAllTerminals(ranges);
    final ImmutableMap<Range<Integer>, CoreNLPParseNode>
        terminalNodes = createTerminalNodes(terminals, parse, tokens, stripFunctionTags);

    // build the tree
    final Range<Integer> rootRange = findRoot(ranges);
    final CoreNLPParseNode
        rootNode = buildParseNodes(headFinder, rootRange, parentToChildren, terminalNodes, parse, stripFunctionTags);

    return new CoreNLPConstituencyParse(tokens, rootNode, rawParse);
  }

  private static CoreNLPParseNode buildParseNodes(final HeadFinder<CoreNLPParseNode> headFinder,
      final Range<Integer> subtreeRoot,
      final ImmutableMultimap<Range<Integer>, Range<Integer>> parentToChildren,
      final ImmutableMap<Range<Integer>, CoreNLPParseNode> terminalNodes, final String parse,
      final boolean stripFunctionTags) {
    final CoreNLPParseNode.StanfordParseNodeBuilder
        builder = CoreNLPParseNode.builderForNonTerminal(headFinder);
    builder.withTag(extractTag(subtreeRoot, parse, stripFunctionTags));
    for (final Range<Integer> child : parentToChildren.get(subtreeRoot)) {
      // base case
      if (terminalNodes.containsKey(child)) {
        builder.addChildren(terminalNodes.get(child));
      } else {
        builder.addChildren(
            buildParseNodes(headFinder, child, parentToChildren, terminalNodes, parse,
                stripFunctionTags));
      }
    }
    return builder.build();
  }

  private static ImmutableSet<Range<Integer>> findAllTerminals(
      final ImmutableList<Range<Integer>> ranges) {
    final ImmutableMap<Range<Integer>, Range<Integer>> childrenToParent = childrenToParent(ranges);
    final ImmutableSet.Builder<Range<Integer>> terminals = ImmutableSet.builder();
    // if nothing points to us, then we are a terminal node
    for (final Range<Integer> r : ranges) {
      if (!childrenToParent.values().contains(r)) {
        terminals.add(r);
      }
    }
    return terminals.build();
  }

  private static ImmutableMap<Range<Integer>, CoreNLPParseNode> createTerminalNodes(
      final ImmutableSet<Range<Integer>> terminals,
      final String parse, final ImmutableList<CoreNLPToken> tokens, final boolean stripFunctionTags) {
    final ImmutableList<Range<Integer>> orderedTerminals =
        Ordering.natural().onResultOf(RangeUtils.<Integer>lowerEndPointFunction())
            .immutableSortedCopy(terminals);
    final ListMultimap<String, CoreNLPToken> textToToken =
        ArrayListMultimap
            .create(FluentIterable.from(tokens).index(CoreNLPToken.contentFunction()));

    final ImmutableMap.Builder<Range<Integer>, CoreNLPParseNode> ret = ImmutableMap.builder();
    for (final Range<Integer> r : orderedTerminals) {
      final CoreNLPParseNode.StanfordParseNodeBuilder builder =
          CoreNLPParseNode.buildForTerminal();
      builder.withTag(extractTag(r, parse, stripFunctionTags));
      final String text = rangeToText(parse).apply(r);
      builder.withText(Optional.fromNullable(text));
      // find token, we know the first token is the one that matches since our ranges are ordered,
      // and the keys and the associated values of the FluentIterable produced map have their order preserved.
      final CoreNLPToken matching = Iterables.getFirst(textToToken.get(text), null);
      textToToken.remove(text, matching);
      builder.withToken(Optional.fromNullable(matching));
      ret.put(r, builder.build());
    }
    return ret.build();
  }

  private static Function<Range<Integer>, String> rangeToText(final String parse) {
    return new Function<Range<Integer>, String>() {
      @Override
      public String apply(final Range<Integer> input) {
        final int spacePosition = parse.indexOf(" ", input.lowerEndpoint());
        final int closeParenPosition = parse.indexOf(")", spacePosition + 1);
        final String text = parse.substring(spacePosition + 1, closeParenPosition);
        return text;
      }
    };
  }

  private static Range<Integer> findRoot(final ImmutableList<Range<Integer>> ranges) {
    final ImmutableMap<Range<Integer>, Range<Integer>> childrenToParent = childrenToParent(ranges);
    // the root node doesn't point anywhere
    final Range<Integer> rootRange = Iterables
        .getOnlyElement(Sets.difference(ImmutableSet.copyOf(ranges), childrenToParent.keySet()));
    return rootRange;
  }

  private static Symbol extractTag(final Range<Integer> r, final String parse,
      final boolean stripFunctionTags) {
    final String wholeTag =
        parse.substring(r.lowerEndpoint() + 1, parse.indexOf(" ", r.lowerEndpoint()));
    final String tag;
    if(stripFunctionTags && wholeTag.contains("-")) {
      tag = wholeTag.substring(0, wholeTag.indexOf('-'));
    } else {
      tag = wholeTag;
    }
    return Symbol.from(tag.toUpperCase());
  }

  private static <T extends Comparable<T>> ImmutableMap<Range<T>, Range<T>> childrenToParent(
      Collection<Range<T>> ranges) {
    final ImmutableMap.Builder<Range<T>, Range<T>> ret = ImmutableMap.builder();
    for (final Range<T> r : ranges) {
      final Optional<Range<T>> parent = smallestContainerForRange(ranges, r);
      if (parent.isPresent()) {
        ret.put(r, parent.get());
      }
    }
    return ret.build();
  }

  /**
   * Assuming our ranges look tree-structured, finds the "smallest" range for the particular target
   * one.
   */
  private static <T extends Comparable<T>> Optional<Range<T>> smallestContainerForRange(
      Collection<Range<T>> ranges, Range<T> target) {
    Range<T> best = Range.all();
    for (final Range<T> r : ranges) {
      if (r.equals(target)) {
        continue;
      }
      // prefer a smaller range, always;
      if (r.encloses(target) && best.encloses(r)) {
        best = r;
      }
    }
    if (best.equals(Range.<T>all())) {
      return Optional.absent();
    }
    return Optional.of(best);
  }

  // finds the open/closes using a stack
  private static ImmutableList<Range<Integer>> findAllOpenCloseParens(final String parse) {
    final ImmutableList.Builder<Range<Integer>> ret = ImmutableList.builder();
    final Stack<Integer> openPositions = new Stack<>();
    for (int pos = 0; pos < parse.length(); pos++) {
      // push
      if (parse.charAt(pos) == '(') {
        openPositions.push(pos);
      }
      // pop
      if (parse.charAt(pos) == ')') {
        Integer open = openPositions.pop();
        ret.add(Range.closed(open, pos));
      }
    }

    return ret.build();
  }

}

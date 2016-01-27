package com.bbn.nlp.parsing;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.bue.common.symbols.SymbolUtils;
import com.bbn.nlp.ConstituentNode;

import com.google.common.annotations.Beta;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * An implementation of Spanish head finding. This borrows heavily from the AncoraSpanishHeadRules
 * from the Apache OpenNLP implementation at https://opennlp.apache.org/index.html
 *
 * See the following links for source files: https://github.com/apache/opennlp/blob/trunk/opennlp-tools/lang/es/parser/es-head-rules
 * https://github.com/apache/opennlp/blob/trunk/opennlp-tools/src/main/java/opennlp/tools/parser/lang/es/AncoraSpanishHeadRules.java
 */
@Beta
/* package-private */ final class SpanishHeadRules {

  private SpanishHeadRules() {
    throw new UnsupportedOperationException();
  }

  static final Symbol GRUP_NOM = Symbol.from("GRUP.NOM");
  static final Symbol GRUP_ADV = Symbol.from("GRUP.ADV");
  static final Symbol GRUP_A = Symbol.from("GRUP.A");
  static final Symbol GRUP_VERB = Symbol.from("GRUP.VERB");

  static final Symbol SA = Symbol.from("SA");
  static final Symbol S_A = Symbol.from("S.A");
  static final Symbol SN = Symbol.from("SN");

  private final static String[] advNPs =
      {"AQA.*", "AQC.*", "GRUP\\.A", "S\\.A", "NC.*S.*", "NP.*", "NC.*P.*", "GRUP\\.NOM"};
  private static final ImmutableSet<Symbol> nouns = ImmutableSet.of(SN, GRUP_NOM);
  private final static ImmutableSet<Symbol> adjs = SymbolUtils.setFrom("$", "GRUP.A", "SA");
  private final static String[] moreAdjs =
      {"AQ0.*", "AQ[AC].*", "AO.*", "GRUP\\.A", "S\\.A", "RG", "RN", "GRUP\\.NOM"};

  static <NodeT extends ConstituentNode<NodeT, ?>> HeadFinder<NodeT> createFromResources()
      throws IOException {
    final boolean headInitial = true;
    final File headRuleFile =
        new File(SpanishHeadRules.class.getResource("es_heads.opennlp.txt").getFile());
    final ImmutableMap<Symbol, HeadRule<NodeT>>
        headRules = headRulesFromResources(headRuleFile.toPath());
    final ImmutableMap.Builder<Symbol, HeadRule<NodeT>> rules = ImmutableMap.builder();
    rules.putAll(headRules);
    for (final Symbol tag : new Symbol[]{GRUP_NOM, SN}) {
      final ImmutableList<HeadRule<NodeT>> ruleList =
          ImmutableList.<HeadRule<NodeT>>of(new SpanishABVNPRule<NodeT>(headInitial),
              new SpanishSNNOMRule<NodeT>(headInitial),
              new SpanishAdjRule<NodeT>(headInitial),
              new SpanishMoreADJRule<NodeT>(headInitial),
              new SpanishFallbackRule<NodeT>(headInitial)
          );
      rules.put(tag, CompositeHeadRule.create(ruleList));
    }
    return MapHeadFinder.create(rules.build());
  }

  private static <NodeT extends ConstituentNode<NodeT, ?>> ImmutableMap<Symbol, HeadRule<NodeT>> headRulesFromResources(
      final Path path) throws IOException {
    final ImmutableMap.Builder<Symbol, HeadRule<NodeT>> ret = ImmutableMap.builder();
    for (final Map.Entry<Symbol, HeadRule<NodeT>> e : FluentIterable
        .from(Files.readAllLines(path, Charsets.UTF_8))
        .transform(StringUtils.Trim)
        .filter(Predicates.not(StringUtils.startsWith("#")))
        .filter(Predicates.not(StringUtils.isEmpty()))
        .transform(PatternMatchHeadRule.<NodeT>fromHeadRuleFileLine())) {
      ret.put(e.getKey(), e.getValue());
    }
    return ret.build();
  }


  private abstract static class SpanishRule<NodeT extends ConstituentNode<NodeT, ?>>
      implements HeadRule<NodeT> {

    private final boolean headInitial;

    protected SpanishRule(final boolean headInitial) {
      this.headInitial = headInitial;
    }

    protected ImmutableList<NodeT> childrenInOrder(
        final Iterable<NodeT> children) {
      if (headInitial) {
        return ImmutableList.copyOf(children);
      } else {
        return ImmutableList.copyOf(children).reverse();
      }
    }

    protected ImmutableList<NodeT> childrenInOrder(
        final NodeT n) {
      if (headInitial) {
        return n.children();
      } else {
        return n.children().reverse();
      }
    }
  }

  private static class SpanishABVNPRule<NodeT extends ConstituentNode<NodeT, ?>>
      extends SpanishRule<NodeT> {

    protected SpanishABVNPRule(final boolean headInitial) {
      super(headInitial);
    }

    @Override
    public Optional<NodeT> matchForChildren(
        final Iterable<NodeT> children) {
      for (final NodeT child : childrenInOrder(children)) {
        for (final String tag : advNPs) {
          if (child.tag().asString().matches(tag)) {
            return Optional.of(child);
          }
        }
      }
      return Optional.absent();
    }
  }

  private static class SpanishSNNOMRule<NodeT extends ConstituentNode<NodeT, ?>>
      extends SpanishRule<NodeT> {

    protected SpanishSNNOMRule(final boolean headInitial) {
      super(headInitial);
    }

    @Override
    public Optional<NodeT> matchForChildren(
        final Iterable<NodeT> children) {
      for (final NodeT child : childrenInOrder(children)) {
        if (nouns.contains(child.tag())) {
          return Optional.of(child);
        }
      }
      return Optional.absent();
    }
  }

  private static class SpanishAdjRule<NodeT extends ConstituentNode<NodeT, ?>>
      extends SpanishRule<NodeT> {

    protected SpanishAdjRule(final boolean headInitial) {
      super(headInitial);
    }

    @Override
    public Optional<NodeT> matchForChildren(
        final Iterable<NodeT> children) {
      for (final NodeT child : childrenInOrder(children)) {
        if (adjs.contains(child.tag())) {
          return Optional.of(child);
        }
      }
      return Optional.absent();
    }
  }

  private static class SpanishMoreADJRule<NodeT extends ConstituentNode<NodeT, ?>>
      extends SpanishRule<NodeT> {

    protected SpanishMoreADJRule(final boolean headInitial) {
      super(headInitial);
    }

    @Override
    public Optional<NodeT> matchForChildren(
        final Iterable<NodeT> children) {
      for (final NodeT child : childrenInOrder(children)) {
        for (final String tag : moreAdjs) {
          if (child.tag().asString().matches(tag)) {
            return Optional.of(child);
          }
        }
      }
      return Optional.absent();
    }
  }

  private static class SpanishFallbackRule<NodeT extends ConstituentNode<NodeT, ?>>
      extends SpanishRule<NodeT> {

    protected SpanishFallbackRule(final boolean headInitial) {
      super(headInitial);
    }

    @Override
    public Optional<NodeT> matchForChildren(
        final Iterable<NodeT> childNodes) {
      final ImmutableList<NodeT> children = ImmutableList.copyOf(childNodes);
      return Optional.of(childrenInOrder(children).get(children.size() - 1));
    }
  }

}

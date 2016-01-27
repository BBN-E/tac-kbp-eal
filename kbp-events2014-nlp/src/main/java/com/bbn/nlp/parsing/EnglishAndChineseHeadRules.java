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
import com.google.common.io.CharSource;
import com.google.common.io.Resources;

import java.io.IOException;
import java.util.List;

/**
 * Implements the head rules as found in: Three Generative, Lexicalised Models for Statistical
 * Parsing  (ACL/EACL97) A New Statistical Parser Based on Bigram Lexical Dependencies (ACL96)
 *
 * Derived from the email archived at http://www.cs.columbia.edu/~mcollins/papers/heads
 *
 *
 * Also contains a Head finder implementation using English NP rules and the Chinese head table as
 * defined in
 *
 * Honglin Sun and Daniel Jurafsky. 2004. Shallow Semantic Parsing of Chinese. In North American
 * Chapter of the ACL: Human Language Technologies (NAACL-HLT), pages 249256, Boston, MA.
 */
@Beta
final class EnglishAndChineseHeadRules {

  private EnglishAndChineseHeadRules() {
    throw new UnsupportedOperationException();
  }

  final static Symbol NP = Symbol.from("NP");

  private static final ImmutableSet<Symbol> ns =
      SymbolUtils.setFrom("NN", "NNP", "NNPS", "NNS", "NX", "POS", "JJR");
  private static final ImmutableSet<Symbol> adjPRN = SymbolUtils.setFrom("$", "ADJP", "PRN");
  private static final Symbol CD = Symbol.from("CD");
  private static final ImmutableSet<Symbol> adjs = SymbolUtils.setFrom("JJ", "JJS", "RB", "QP");

  public static <NodeT extends ConstituentNode<NodeT, ?>> HeadFinder<NodeT> createEnglishPTBFromResources()
      throws IOException {
    final boolean headInitial = true;
    final CharSource resource = Resources
        .asCharSource(EnglishAndChineseHeadRules.class.getResource("en_heads.collins.txt"), Charsets.UTF_8);
    final HeadRule<NodeT> englishNPHandling = EnglishNPHeadRules();
    final ImmutableMap<Symbol, HeadRule<NodeT>> headRules =
        headRulesFromResources(headInitial, resource);
    final ImmutableMap.Builder<Symbol, HeadRule<NodeT>> ruleB = ImmutableMap.builder();
    ruleB.putAll(headRules);
    // english NP rules get an NP key...
    ruleB.put(NP, englishNPHandling);

    return MapHeadFinder.create(ruleB.build());
  }

//  /**
//   * Head finder using English NP rules and the Chinese head table as defined in
//   *
//   * Honglin Sun and Daniel Jurafsky. 2004. Shallow Semantic Parsing of Chinese. In North American
//   * Chapter of the ACL: Human Language Technologies (NAACL-HLT), pages 249256, Boston, MA.
//   */
//  public static <NodeT extends ConstituentNode<NodeT, ?>> HeadFinder<NodeT> createChinesePTBFromResources()
//      throws IOException {
//    final boolean headInitial = true;
//    final File headRuleFile =
//        new File(EnglishAndChineseHeadRules.class.getResource("ch_heads.sun.txt").getFile());
//    final HeadRule<NodeT> englishNPHandling = EnglishNPHeadRules();
//    final ImmutableMap<Symbol, HeadRule<NodeT>> headRules =
//        headRulesFromResources(headInitial, headRuleFile.toPath());
//    final ImmutableMap.Builder<Symbol, HeadRule<NodeT>> ruleB = ImmutableMap.builder();
//    ruleB.putAll(headRules);
//    // english NP rules get an NP key...
//    ruleB.put(NP, englishNPHandling);
//    // add the UNK rule
//    ruleB.put(CollinsStyleHeadRule.UNKNOWN, CollinsStyleHeadRule.UNKHeadRule.<NodeT>create(headInitial));
//    return MapHeadFinder.create(ruleB.build());
//  }

  public static <NodeT extends ConstituentNode<NodeT, ?>> ImmutableMap<Symbol, HeadRule<NodeT>> headRulesFromResources(
      final boolean headInitial,
      final CharSource p)
      throws IOException {
    return ImmutableMap.copyOf(FluentIterable.from(p.readLines())
        .transform(StringUtils.Trim)
        .filter(Predicates.not(StringUtils.startsWith("#")))
        .filter(Predicates.not(StringUtils.isEmpty()))
        .transform(CollinsStyleHeadRule.<NodeT>fromHeadRuleFileLine(headInitial)).toList());
  }

  public static <NodeT extends ConstituentNode<NodeT, ?>> HeadFinder<NodeT> createFromResources(
      final boolean headInitial, final CharSource p)
      throws IOException {
    final ImmutableMap<Symbol, HeadRule<NodeT>> headRules = headRulesFromResources(headInitial, p);
    return MapHeadFinder.create(headRules);
  }

  // English Rules
  private static <NodeT extends ConstituentNode<NodeT, ?>> HeadRule<NodeT> EnglishNPHeadRules() {
    final List<HeadRule<NodeT>> rules = ImmutableList.<HeadRule<NodeT>>of(new EnglishPOSRule<NodeT>(),
        new EnglishNSRule<NodeT>(),
        new EnglishNPNPRule<NodeT>(),
        new EnglishAdjPRNPhraseRule<NodeT>(),
        new EnglishCDRule<NodeT>(),
        new EnglishAdjRule<NodeT>(),
        new EnglishFallBackRule<NodeT>());
    return CompositeHeadRule.create(rules);
  }

  protected static <NodeT extends ConstituentNode<NodeT, ?>> Optional<NodeT> findChildWithMatchingTag(
      final Iterable<NodeT> children, final Symbol tag) {
    for (final NodeT c : children) {
      if (c.tag().equalTo(tag)) {
        return Optional.of(c);
      }
    }
    return Optional.absent();
  }

  protected static <NodeT extends ConstituentNode<NodeT, ?>> Optional<NodeT> findChildWithMatchingTag(
      final Iterable<NodeT> children, final ImmutableSet<Symbol> tags) {
    for (final NodeT c : children) {
      if (tags.contains(c.tag())) {
        return Optional.of(c);
      }
    }
    return Optional.absent();
  }


  private static class EnglishPOSRule<NodeT extends ConstituentNode<NodeT, ?>>
      implements HeadRule<NodeT> {

    @Override
    public Optional<NodeT> matchForChildren(
        final Iterable<NodeT> childNodes) {
      final ImmutableList<NodeT> children = ImmutableList.copyOf(childNodes);
      // if POS
      if (children.reverse().get(0).terminal()) {
        return Optional.of(children.reverse().get(0));
      }
      return Optional.absent();
    }
  }

  private static class EnglishNSRule<NodeT extends ConstituentNode<NodeT, ?>>
      implements HeadRule<NodeT> {

    @Override
    public Optional<NodeT> matchForChildren(
        final Iterable<NodeT> children) {
      return findChildWithMatchingTag(ImmutableList.copyOf(children).reverse(), ns);
    }
  }

  private static class EnglishNPNPRule<NodeT extends ConstituentNode<NodeT, ?>>
      implements HeadRule<NodeT> {

    @Override
    public Optional<NodeT> matchForChildren(
        final Iterable<NodeT> children) {
      return findChildWithMatchingTag(children, NP);
    }
  }

  private static class EnglishAdjPRNPhraseRule<NodeT extends ConstituentNode<NodeT, ?>>
      implements HeadRule<NodeT> {

    @Override
    public Optional<NodeT> matchForChildren(
        final Iterable<NodeT> children) {
      // else find $, ADJP, PRN (r2l)
      return findChildWithMatchingTag(ImmutableList.copyOf(children).reverse(), adjPRN);
    }
  }

  private static class EnglishCDRule<NodeT extends ConstituentNode<NodeT, ?>>
      implements HeadRule<NodeT> {

    @Override
    public Optional<NodeT> matchForChildren(
        final Iterable<NodeT> children) {
      // else find CD (r2l)
      return findChildWithMatchingTag(ImmutableList.copyOf(children).reverse(), CD);
    }
  }

  private static class EnglishAdjRule<NodeT extends ConstituentNode<NodeT, ?>>
      implements HeadRule<NodeT> {

    @Override
    public Optional<NodeT> matchForChildren(
        final Iterable<NodeT> children) {
      // else find JJ, JJS, RB, QP (r2l)
      return findChildWithMatchingTag(ImmutableList.copyOf(children).reverse(), adjs);
    }
  }

  private static class EnglishFallBackRule<NodeT extends ConstituentNode<NodeT, ?>>
      implements HeadRule<NodeT> {

    @Override
    public Optional<NodeT> matchForChildren(
        final Iterable<NodeT> children) {
      // else last word
      return Optional.of(ImmutableList.copyOf(children).reverse().get(0));
    }
  }


}

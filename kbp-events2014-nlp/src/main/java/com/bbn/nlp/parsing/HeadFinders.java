package com.bbn.nlp.parsing;

import com.bbn.nlp.ConstituentNode;

import com.google.common.annotations.Beta;

import java.io.IOException;

/**
 * Class to get pre-made head finders for common tree formats and languages.
 */
@Beta
public final class HeadFinders {

  private HeadFinders() {
    throw new UnsupportedOperationException();
  }

  /**
   * Gets a head finder for English parses in Penn Treebank format using the standard Collins
   * rules.
   */
  public static <NodeT extends ConstituentNode<NodeT, ?>> HeadFinder<NodeT> getEnglishPTBHeadFinder()
      throws IOException {
    return EnglishAndChineseHeadRules.createEnglishPTBFromResources();
  }

//  /**
//   * Gets a head finder for Chinese parses in Penn Treebank format using rules from Honglin Sun and
//   * Daniel Jurafsky. 2004. Shallow Semantic Parsing of Chinese.
//   */
//  public static <NodeT extends ConstituentNode<NodeT, ?>> HeadFinder<NodeT> getChinesePTBHeadFinder()
//      throws IOException {
//    return EnglishAndChineseHeadRules.createChinesePTBFromResources();
//  }
//
//  /**
//   * Gets a head finder for Spanish parses in ANCORA format. Borrows heavily from OpenNLP's Spanish
//   * head finder.
//   */
//  public static <NodeT extends ConstituentNode<NodeT, ?>> HeadFinder<NodeT> getSpanishAncoraHeadFinder()
//      throws IOException {
//    return SpanishHeadRules.<NodeT>createFromResources();
//  }
}

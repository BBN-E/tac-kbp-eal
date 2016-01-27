package com.bbn.nlp;

import com.bbn.nlp.corenlp.CoreNLPConstituencyParse;
import com.bbn.nlp.corenlp.CoreNLPDocument;
import com.bbn.nlp.corenlp.CoreNLPParseNode;
import com.bbn.nlp.corenlp.CoreNLPSentence;
import com.bbn.nlp.corenlp.CoreNLPToken;
import com.bbn.nlp.corenlp.CoreNLPXMLLoader;
import com.bbn.nlp.parsing.HeadFinders;

import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

public final class TestStanfordXML {

  private CoreNLPDocument fetchEnglish() throws IOException {
    final File example =
        new File(this.getClass().getResource("corenlp/bbn.input.txt.xml").getFile());
    return CoreNLPXMLLoader.builder(HeadFinders.<CoreNLPParseNode>getEnglishPTBHeadFinder()).build()
        .loadFrom(example);
  }

//  private CoreNLPDocument fetchSpanish() throws IOException {
//    final File example =
//        new File(this.getClass().getResource("corenlp/bbn.es.input.txt.xml").getFile());
//    return CoreNLPXMLLoader
//        .create(HeadFinders.<CoreNLPParseNode>getSpanishAncoraHeadFinder())
//        .loadFrom(example);
//  }

  private ImmutableList<CoreNLPDocument> docs() throws IOException {
    return ImmutableList.of(fetchEnglish());
  }

  @Test
  public void loadXML() throws IOException {
    for (final CoreNLPDocument doc : docs()) {
      assertTrue(doc != null);
    }
  }

  @Test
  public void testParse() throws IOException {
    for (final CoreNLPDocument doc : docs()) {
      for (final CoreNLPSentence sent : doc.sentences()) {
        iterableParseNodesAreLoopLess(sent.parse().get());
        parseIsConsistent(sent);
      }
    }
  }

  @Test
  public void everyChildHasAParentInParse() throws IOException {
    // except root; nobody loves root.
    for (final CoreNLPDocument doc : docs()) {
      for (final CoreNLPSentence sent : doc.sentences()) {
        assertTrue("the example input has a parse", sent.parse().isPresent());
        final ImmutableSet<CoreNLPParseNode> nodes =
            ImmutableSet.copyOf(sent.parse().get().preorderTraversal());
        final Set<CoreNLPParseNode> noParents = Sets.newHashSet();
        for (final CoreNLPParseNode n : nodes) {
          if (n.parent().isPresent()) {
            checkState(nodes.contains(n.parent().get()), "This node has an unknown parent!");
          } else {
            noParents.add(n);
          }
        }
        assertTrue("Exactly one node may have no parent, instead we have " + noParents.toString(),
            noParents.size() == 1);
      }
    }
  }

  @Test
  public void everyNonTerminalHasAHead() throws IOException {
    for (final CoreNLPDocument doc : docs()) {
      assertTrue("no sentences!", doc.sentences().size() > 0);
      for (final CoreNLPSentence sent : doc.sentences()) {
        assertTrue("Expected a parse!", sent.parse().isPresent());
        for (final CoreNLPParseNode node : sent.parse().get().preorderTraversal()) {
          if (!node.terminal()) {
            assertTrue(
                "Every non-terminal node constructed here is expected to have a head! " + node,
                node.immediateHead().isPresent());
            assertTrue("The head of a node must of one of its children!",
                node.children().contains(node.immediateHead().get()));
          }
        }
      }
    }
  }

  private void iterableParseNodesAreLoopLess(final CoreNLPConstituencyParse parse) {
    final Set<CoreNLPParseNode> visitedNodes = Sets.newHashSet();
    for (CoreNLPParseNode n : parse.preorderTraversal()) {
      assertFalse("found a loop when iterating over the nodes in our parse!",
          visitedNodes.contains(n));
      visitedNodes.add(n);
    }
  }

  private static void parseIsConsistent(final CoreNLPSentence sent) {
    final ImmutableList<CoreNLPParseNode>
        nodes = FluentIterable.from(ImmutableList.copyOf(sent.parse().get().preorderTraversal()))
        .filter(CoreNLPParseNode.isTerminal()).toList();
    final ImmutableList<CoreNLPToken> tokens = sent.tokens();
    assertEquals("have unequal numbers of terminal nodes and tokens!", nodes.size(), tokens.size());
    for (int i = 0; i < nodes.size(); i++) {
      //skip any empty parse nodes
      if (nodes.get(i).nodeData().isPresent()) {
        // we want reference equality here, since the StanfordParse uses the same token object
        assertEquals("terminal node and token did not agree, check ordering!",
            nodes.get(i).token().get(), (tokens.get(i)));
      }
    }
  }

  @Ignore
  @Test
  public void printEnglishHeads() throws IOException {
    final CoreNLPDocument doc = fetchEnglish();
    for (final CoreNLPSentence sent : doc.sentences()) {
      final StringBuilder sb = new StringBuilder();
      final CoreNLPConstituencyParse parse = sent.parse().get();
      printNodeAndHead(parse.root(), sb, "", 0);
      System.out.println(sb.toString());
    }
  }

  private void printNodeAndHead(final CoreNLPParseNode n, final StringBuilder sb, final String prefix, final int i) {

    if (n.terminal()) {
      // tag, start, end
      final String nodeFormat = "%s: %d-%d ";
      sb.append(Strings.repeat("\t", i));
      sb.append(prefix);
      sb.append(String.format(nodeFormat, n.tag(), n.span().startInclusive().asInt(), n.span().endInclusive().asInt()));
      sb.append(" ");
      sb.append(n.token().get().content());
      sb.append("\n");
    } else {
      final String nodeFormat = "%s: %d-%d ";
      sb.append(Strings.repeat("\t", i));
      sb.append(prefix);
      sb.append(String.format(nodeFormat, n.tag(), n.span().startInclusive().asInt(), n.span().endInclusive().asInt()));
      sb.append("\n");

      final CoreNLPParseNode head = n.immediateHead().get();
      for(final CoreNLPParseNode child: n.children()) {
        if(child.equals(head)) {
          printNodeAndHead(child, sb, "*", i+1);
        } else {
          printNodeAndHead(child, sb, "", i+1);
        }
      }
    }

  }
}

package com.bbn.nlp.corenlp;

import com.bbn.bue.common.strings.offsets.OffsetRange;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.nlp.parsing.HeadFinder;

import com.google.common.annotations.Beta;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharSource;
import com.google.common.io.Files;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import static com.bbn.bue.common.xml.XMLUtils.directChild;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This class and its consumers assume that you ran the Stanford pipeline with: {@code -annotators
 * tokenize,cleanxml,ssplit,parse -tokenize.options invertible  -outputFormat xml}
 *
 * It might accidentally work with other options; the import ones are tokenize, cleanxml,
 * invertible, and xml
 */
@Beta
public final class CoreNLPXMLLoader {

  private final HeadFinder<CoreNLPParseNode> headFinder;
  private final boolean stripFunctionTags;

  private CoreNLPXMLLoader(final HeadFinder<CoreNLPParseNode> headFinder,
      final boolean stripFunctionTags) {
    this.headFinder = headFinder;
    this.stripFunctionTags = stripFunctionTags;
  }

  public CoreNLPDocument loadFrom(final File f) throws IOException {
    checkNotNull(f);
    try {
      return loadFrom(Files.asCharSource(f, Charsets.UTF_8));
    } catch (final Exception e) {
      throw new IOException(
          String.format("Error loading StanfordXML document %s", f.getAbsolutePath()), e);
    }
  }

  public CoreNLPDocument loadFrom(final CharSource source) throws IOException {
    checkNotNull(source);
    return loadFromString(source.read());
  }

  private CoreNLPDocument loadFromString(final String s) throws IOException {
    final int contentStart = s.indexOf("<root>");
    if (contentStart < 0) {
      throw new IOException("could not find a root element for this document");
    }
    final InputSource in = new InputSource(new StringReader(s));

    try {
      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      final DocumentBuilder builder = factory.newDocumentBuilder();
      return loadFrom(builder.parse(in));
    } catch (final ParserConfigurationException | SAXException e) {
      throw new RuntimeException("Error parsing xml", e);
    }
  }

  private CoreNLPDocument loadFrom(final org.w3c.dom.Document xml) {
    final Element root = xml.getDocumentElement();
    final String rootTag = root.getTagName();
    if (rootTag.equalsIgnoreCase("root")) {
      final Optional<Element> documentChild = directChild(root, "document");
      if (documentChild.isPresent()) {
        return loadFrom(documentChild.get());
      } else {
        throw new RuntimeException(
            "If a StanfordXML has StanfordXML tag at the top-level, it must have a StanfordDocument element immediately below it");
      }
    } else if (rootTag.equalsIgnoreCase("StanfordDocument")) {
      return loadFrom(root);
    } else {
      throw new RuntimeException("StanfordXML should have a root of root or document");
    }

  }

  private CoreNLPDocument loadFrom(final Element element) {
    return toDocument(element);
  }

  private CoreNLPDocument toDocument(final Element e) {
    checkArgument(e.getTagName().equalsIgnoreCase("Document"));
    final CoreNLPDocument.StanfordDocumentBuilder documentBuilder =
        CoreNLPDocument.builder();
    for (Node child = e.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element) {
        final Element childElement = (Element) child;
        if (childElement.getTagName().equalsIgnoreCase("sentences")) {
          documentBuilder.withSentences(toSentences(childElement));
        }
      }
    }
    return documentBuilder.build();
  }

  private ImmutableList<CoreNLPSentence> toSentences(final Element e) {
    final ImmutableList.Builder<CoreNLPSentence> sentenceBuilder = ImmutableList.builder();
    for (Node child = e.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element) {
        final Element childElement = (Element) child;
        if (childElement.getTagName().equalsIgnoreCase("sentence")) {
          sentenceBuilder.add(toSentence(childElement));
        }
      }
    }

    return sentenceBuilder.build();
  }

  private CoreNLPSentence toSentence(final Element e) {
    final CoreNLPSentence.StanfordSentenceBuilder ret = CoreNLPSentence.builder();
    ImmutableList<CoreNLPToken> tokens = null;
    final String id = e.getAttribute("id");
    for (Node child = e.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element) {
        final Element childElement = (Element) child;
        if (childElement.getTagName().equalsIgnoreCase("tokens")) {
          if (tokens != null) {
            throw new RuntimeException("Can't have tokens twice!");
          }
          tokens = toTokens(childElement);
          ret.withTokens(tokens);
        }
        if (childElement.getTagName().equals("parse")) {
          final CoreNLPConstituencyParse parse =
              CoreNLPConstituencyParse.create(headFinder, tokens, childElement.getTextContent(), stripFunctionTags);
          ret.withParse(Optional.of(parse));
        }
      }
    }
    return ret.build();
  }

  private ImmutableList<CoreNLPToken> toTokens(final Element e) {
    final ImmutableList.Builder<CoreNLPToken> tokenList = ImmutableList.builder();

    for (Node child = e.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element) {
        final Element childElement = (Element) child;
        if (childElement.getTagName().equalsIgnoreCase("token")) {
          tokenList.add(toToken(childElement));
        }
      }
    }

    return tokenList.build();
  }

  private CoreNLPToken toToken(final Element e) {
    String word = null;
    int startOffset = -1;
    int endOffset = -1;
    String POS = null;

    //    final String id = e.getAttribute("id");

    for (Node child = e.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element) {
        final Element childElement = (Element) child;
        if (childElement.getTagName().equalsIgnoreCase("word")) {
          word = child.getTextContent();
        }
        if (childElement.getTagName().equalsIgnoreCase("CharacterOffsetBegin")) {
          startOffset = Integer.parseInt(child.getTextContent());
        }
        // these are exclusive, but we want everything inclusive
        if (childElement.getTagName().equalsIgnoreCase("CharacterOffsetEnd")) {
          endOffset = Integer.parseInt(child.getTextContent()) - 1;
        }
        if (childElement.getTagName().equalsIgnoreCase("POS")) {
          POS = child.getTextContent();
        }

      }
    }

    return CoreNLPToken
        .create(Symbol.from(POS), word, OffsetRange.charOffsetRange(startOffset, endOffset));
  }

  public static CoreNLPXMLLoaderBuilder builder(final HeadFinder<CoreNLPParseNode> headfinder) {
    return new CoreNLPXMLLoaderBuilder(headfinder);
  }

  public static class CoreNLPXMLLoaderBuilder {

    private final HeadFinder<CoreNLPParseNode> headFinder;
    private boolean stripFunctionTags = true;

    private CoreNLPXMLLoaderBuilder(final HeadFinder<CoreNLPParseNode> headFinder) {
      this.headFinder = headFinder;
    }

    public CoreNLPXMLLoaderBuilder keepFunctionTags() {
      // revisit in the future if we need to
      throw new UnsupportedOperationException("I can't let you do that Dave.");
    }


    public CoreNLPXMLLoader build() {
      CoreNLPXMLLoader coreNLPXMLLoader = new CoreNLPXMLLoader(headFinder, stripFunctionTags);
      return coreNLPXMLLoader;
    }
  }
}

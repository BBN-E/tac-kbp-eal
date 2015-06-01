package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;

import com.google.common.base.Function;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.io.CharSink;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Created by jdeyoung on 6/1/15.
 */
public class AssessmentQA {

  private static final Logger log = LoggerFactory.getLogger(AssessmentQA.class);

  public static void trueMain(String... args) throws IOException {
    final Parameters params = Parameters.loadSerifStyle(new File(args[0]));
    final AnnotationStore store =
        AssessmentSpecFormats.openAnnotationStore(params.getExistingDirectory("annotationStore"),
            AssessmentSpecFormats.Format.KBP2015);

    for(Symbol docID: store.docIDs()) {
      log.info("processing document {}", docID.asString());
      final AnswerKey answerKey = store.read(docID);
      final HTMLRenderer htmlRenderer = new HTMLRenderer();
      htmlRenderer.addResponses(overallOrdering.sortedCopy(Iterables.transform(
          answerKey.annotatedResponses(), AssessedResponse.Response)));
      final File output = params.getCreatableDirectory("output");
      htmlRenderer.renderTo(
          Files.asCharSink(new File(output, docID.asString() + ".html"),
              Charset.defaultCharset()));
    }
  }

  private static Ordering<Response> overallOrdering = Ordering.compound(ImmutableList
      .of(Response.byEvent(), Response.byRole(), Response.byFillerString(),
          Response.byCASSttring()));

  public static void main(String... args) {
    try {
      trueMain(args);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  private final static class HTMLRenderer {

    final private Multimap<String, Response> typeToResponse = HashMultimap.create();

    public static String bodyHeader() {
      return "<body>";
    }

    public static String htmlHeader() {
      return "<html>";
    }

    public static String bodyFooter() {
      return "</body>";
    }

    public static String htmlFooter() {
      return "</html>";
    }

    public static String javascript() {
      return "<script type=\"text/javascript\"></script>";
    }

    public void addResponses(final Iterable<Response> responses) {
      typeToResponse.putAll(
          Multimaps.index(responses, new Function<Response, String>() {
            @Override
            public String apply(final Response input) {
              return input.type().asString();
            }
          }));
    }

    public void renderTo(CharSink sink) throws IOException {
      final StringBuilder sb = new StringBuilder();
      sb.append(bodyHeader());
      sb.append(javascript());
      sb.append(htmlHeader());
      for (final String type : Ordering.natural().sortedCopy(typeToResponse.keySet())) {
        addSection(sb, type, typeToResponse.get(type));
      }
      sb.append(htmlFooter());
      sb.append(bodyFooter());

      sink.write(sb.toString());
    }

    private void addSection(StringBuilder sb, String type, Iterable<Response> responses) {
      sb.append("<div>");
      sb.append("<h2>");
      sb.append(type);
      sb.append("</h3>");
      sb.append("<ul class=\"list\">");
      for (Response r : overallOrdering.sortedCopy(responses)) {
        sb.append("<li>");
        sb.append(r.toString());
        sb.append("</li>");
      }
      sb.append("</ul>");
      sb.append("</div>");
    }
  }
}

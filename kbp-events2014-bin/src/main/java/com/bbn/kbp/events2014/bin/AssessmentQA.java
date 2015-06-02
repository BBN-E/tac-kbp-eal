package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.CorefAnnotation;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.transformers.MakeAllRealisActual;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
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

import static com.google.common.base.Preconditions.checkNotNull;

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

    for (Symbol docID : store.docIDs()) {
      log.info("processing document {}", docID.asString());
      final AnswerKey answerKey = MakeAllRealisActual.forAnswerKey().apply(store.read(docID));
      final HTMLRenderer htmlRenderer = new HTMLRenderer(docID.asString());
      htmlRenderer.setCorefAnnotation(answerKey.corefAnnotation());
      htmlRenderer.addResponses(
          overallOrdering.sortedCopy(FluentIterable.from(answerKey.annotatedResponses()).filter(
              AssessedResponse.IsCorrectUpToInexactJustifications)
              .transform(AssessedResponse.Response)));
      final File output = params.getCreatableDirectory("output");
      htmlRenderer.renderTo(
          Files.asCharSink(new File(output, docID.asString() + ".html"),
              Charset.defaultCharset()));
    }
  }

  private static Ordering<Response> overallOrdering = Ordering.compound(ImmutableList
      .of(Response.byEvent(), Response.byRole(), Response.byFillerString(),
          Response.byCASSttring()));
  private static Ordering<TypeRoleFillerRealis> trfrOrdering = Ordering.compound(ImmutableList.of(
      TypeRoleFillerRealis.byType(), TypeRoleFillerRealis.byRole(), TypeRoleFillerRealis.byCAS(),
      TypeRoleFillerRealis.byRealis()));

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
    final private String docID;
    private CorefAnnotation corefAnnotation;

    private HTMLRenderer(final String docID) {
      this.docID = docID;
    }

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
      return "<script type=\"text/javascript\">" +
          "function toggle(element) {\n "+
          "\tvar e = document.getElementById(element); "
          + "\nif(e.style.display == \"none\") { \n "
          + "\te.style.display = \"block\"; \n}"
          + "else {\n "
          + "\te.style.display = \"none\";\n}"
          + "}"
          + "</script>";
    }

    public void setCorefAnnotation(CorefAnnotation corefAnnotation) {
      this.corefAnnotation = corefAnnotation;
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

    private static String href(final String id) {
      return String.format("<a href=\"javascript:toggle('%s')\">\n", id);
    }

    private static String closehref() {
      return "</a>";
    }

    public void renderTo(CharSink sink) throws IOException {
      checkNotNull(corefAnnotation);
      final StringBuilder sb = new StringBuilder();
      sb.append(htmlHeader());
      sb.append(javascript());
      sb.append(bodyHeader());
      sb.append("<h1>");
      sb.append(docID);
      sb.append("</h1>\n");
      for (final String type : Ordering.natural().sortedCopy(typeToResponse.keySet())) {
        final Multimap<TypeRoleFillerRealis, Response> trfrToResponse =
            Multimaps.index(typeToResponse.get(type),
                TypeRoleFillerRealis.extractFromSystemResponse(
                    corefAnnotation.laxCASNormalizerFunction()));
        sb.append(href(type));
        sb.append("<h2>");
        sb.append(type);
        sb.append("</h2>\n");
        sb.append(closehref());
        sb.append("<div id=\"");
        sb.append(type);
        sb.append("\" style=\"display:none\">\n");


        sb.append("<ul>\n");
        for (final TypeRoleFillerRealis trfr : trfrOrdering.sortedCopy(trfrToResponse.keySet())) {
          final String trfrID = String.format("%s.%s", type, trfr.role().asString());
          sb.append("<li>\n");
          sb.append(String.format("<div id=\"%s\" style=\"display:inherit\" >", trfrID));
          sb.append("<h3>");
          sb.append(String.format("%s-%s:%s - %s", trfr.type().asString(), trfr.role().asString(),
              trfr.realis().name(), trfr.argumentCanonicalString().string()));
          sb.append("</h3>\n");

          addSection(sb, trfrID, Iterables.transform(trfrToResponse.get(trfr),
              new Function<Response, String>() {
                @Override
                public String apply(final Response r) {
                  return String
                      .format("%s: %s", r.realis().name(), r.canonicalArgument().string());
                }
              }));
          sb.append("</div>\n");
          sb.append("</li>\n");
        }
        sb.append("</ul>\n");
        sb.append("</div>\n");
        sb.append("</li>\n");
      }
      sb.append(bodyFooter());
      sb.append(htmlFooter());


      sink.write(sb.toString());
    }

    private void addSection(final StringBuilder sb, final String trfrID, final Iterable<String> responses) {
      //sb.append(String.format("<div id=\"%s.responses\" style=\"display:inherit\">", trfrID));
      //sb.append("<h3>");
      //sb.append(type);
      //sb.append("</h3>");
      sb.append("<ul>\n");
      for (String r : responses) {
        sb.append("<li>\n");
        sb.append(r);
        sb.append("</li>\n");
      }
      sb.append("</ul>\n");
      //sb.append("</div>\n");
    }
  }
}

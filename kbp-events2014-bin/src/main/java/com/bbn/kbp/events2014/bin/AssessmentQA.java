package com.bbn.kbp.events2014.bin;

import com.bbn.bue.common.collections.MultimapUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.io.AnnotationStore;
import com.bbn.kbp.events2014.io.AssessmentSpecFormats;
import com.bbn.kbp.events2014.transformers.MakeAllRealisActual;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
      final DocumentRenderer htmlRenderer = new DocumentRenderer(docID.asString());
      final File output = params.getCreatableDirectory("output");
      htmlRenderer.renderTo(
          Files.asCharSink(new File(output, docID.asString() + ".html"), Charset.defaultCharset()),
          answerKey, generateWarnings(
              Iterables.transform(answerKey.annotatedResponses(), AssessedResponse.Response)));
    }
  }

  private static Ordering<Response> overallOrdering = Ordering.compound(ImmutableList
      .of(Response.byEvent(), Response.byRole(), Response.byFillerString(),
          Response.byCASSttring()));
  private static Ordering<TypeRoleFillerRealis> trfrOrdering = Ordering.compound(ImmutableList.of(
      TypeRoleFillerRealis.byType(), TypeRoleFillerRealis.byRole(), TypeRoleFillerRealis.byCAS(),
      TypeRoleFillerRealis.byRealis()));
  private final static List<Warning> warnings =
      ImmutableList.of(new ConjunctionWarning(), new OverlapWarning());


  private static ImmutableMultimap<Response, Warning> generateWarnings(
      Iterable<Response> responses) {
    ImmutableMultimap.Builder<Warning, Response> warningResponseBuilder =
        ImmutableMultimap.builder();
    for (Warning w : warnings) {
      warningResponseBuilder.putAll(w, w.applyWarning(responses));
    }
    return warningResponseBuilder.build().inverse();
  }

  public static void main(String... args) {
    try {
      trueMain(args);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  private interface Warning {

    String CSSStyleName();

    String CSSForWarning();

    Iterable<Response> applyWarning(final Iterable<Response> input);
  }

  private static abstract class ContainsStringWarning implements Warning, Predicate<Response> {

    private final Set<String> verboten;

    public ContainsStringWarning(final Set<String> verboten) {
      this.verboten = verboten;
    }

    @Override
    public Iterable<Response> applyWarning(final Iterable<Response> input) {
      return Iterables.filter(input, this);
    }

    @Override
    public boolean apply(final Response input) {
      Set<String> inputParts =
          ImmutableSet.copyOf(input.canonicalArgument().string().trim().toLowerCase().split(
              "\\s+"));
      inputParts.retainAll(verboten);
      return inputParts.size() > 0;
    }

  }

  private final static class ConjunctionWarning extends ContainsStringWarning {

    final static ImmutableSet<String> conjunctions = ImmutableSet.of("and", "or");

    public ConjunctionWarning() {
      super(conjunctions);
    }

    public static ConjunctionWarning create() {
      return new ConjunctionWarning();
    }

    @Override
    public String CSSStyleName() {
      return "conjunction";
    }

    @Override
    public String CSSForWarning() {
      return "." + CSSStyleName() + " {\n"
          + "color: red;\n"
          + "margin-left: 14px;\n"
          + "visibility: inherit;\n"
          + "}\n";
    }
  }

  private final static class OverlapWarning implements Warning {

    @Override
    public String CSSStyleName() {
      return "overlap";
    }

    @Override
    public String CSSForWarning() {
      return "." + CSSStyleName() + " {\n"
          + "color: blue;\n"
          + "margin-left: 12px;\n"
          + "visibility: inherit;\n"
          + "}\n";
    }

    // see http://en.wikipedia.org/wiki/S%C3%B8rensen%E2%80%93Dice_coefficient
    private double diceScoreBySpace(String one, String two) {
      Set<String> oneParts = ImmutableSet.copyOf(one.split("\\s+"));
      Set<String> twoParts = ImmutableSet.copyOf(two.split("\\s+"));
      Set<String> overlap = new HashSet<String>(oneParts);
      overlap.retainAll(twoParts);
      return 2 * overlap.size() / ((double) oneParts.size() + twoParts.size());
    }

    @Override
    public Iterable<Response> applyWarning(final Iterable<Response> input) {
      ImmutableSet.Builder<Response> output = ImmutableSet.builder();
      for (Response a : input) {
        if (a.canonicalArgument().string().trim().isEmpty()) {
          continue;
        }
        for (Response b : input) {
          if (a == b || b.canonicalArgument().string().trim().isEmpty()) {
            continue;
          }
          // check for complete containment
          if (a.canonicalArgument().string().contains(b.canonicalArgument().string())) {
            //log.info("adding {} and {} by complete string containment", a, b);
            output.add(a);
            output.add(b);
          }
          // arbitrarily chosen magic constant
          if (diceScoreBySpace(a.canonicalArgument().string(), b.canonicalArgument().string())
              > 0.5) {
            //log.info("adding {} and {} by dice score", a, b);
            output.add(a);
            output.add(b);
          }
        }
      }
      return output.build();
    }
  }


  private final static class DocumentRenderer {

    final private String docID;

    private DocumentRenderer(final String docID) {
      this.docID = docID;
    }

    private static String bodyHeader() {
      return "<body>";
    }

    private static String htmlHeader() {
      return "<!doctype html>\n"
          + "<html>\n"
          + "<html>\n"
          + "  <head>\n"
          + "    <meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\">\n";
    }

    private static String bodyFooter() {
      return "</body>";
    }

    private static String htmlFooter() {
      return "</html>";
    }

    private static String javascript() {
      return "<script type=\"text/javascript\">" +
          "function toggle(element) {\n " +
          "\tvar e = document.getElementById(element); "
          + "\nif(e.style.display == \"none\") { \n "
          + "\te.style.display = \"block\"; \n}"
          + "else {\n "
          + "\te.style.display = \"none\";\n}"
          + "}"
          + "</script>";
    }

    private static String defaultStyle() {
      return "* {\n"
          + "\tvisibility: inherit;\n"
          + "};\n";
    }

    private static String CSS() {
      final StringBuilder sb = new StringBuilder("<style>\n");
      for (Warning w : warnings) {
        sb.append(w.CSSForWarning());
        sb.append("\n");
      }
      sb.append(defaultStyle());
      sb.append("</style>\n");
      return sb.toString();
    }

    private static String href(final String id) {
      return String.format("<a href=\"javascript:toggle('%s')\">\n", id);
    }

    private static String closehref() {
      return "</a>";
    }

    private static int warningsDiv(final StringBuilder sb, final Iterable<Warning> warnings) {
      int total = 0;
      for (final Warning w : warnings) {
        sb.append("<div style=\"");
        sb.append(w.CSSStyleName());
        sb.append("\" class=\"");
        sb.append(w.CSSStyleName());
        sb.append("\" />");
        total += 1;
      }
      return total;
    }

    public void renderTo(final CharSink sink, final AnswerKey answerKey,
        final ImmutableMultimap<Response, Warning> warnings)
        throws IOException {
      final StringBuilder sb = new StringBuilder();
      sb.append(htmlHeader());
      //sb.append("<meta charset=\"UTF-8\">");
      sb.append(javascript());
      sb.append(CSS());
      sb.append(bodyHeader());
      sb.append("</head>");
      sb.append("</head>");
      sb.append("<h1>");
      sb.append(docID);
      sb.append("</h1>\n");

      final ImmutableMultimap<TypeRoleFillerRealis, Response> trfrToReponses = Multimaps
          .index(warnings.keySet(), TypeRoleFillerRealis
              .extractFromSystemResponse(answerKey.corefAnnotation().laxCASNormalizerFunction()));
      final ImmutableMultimap<TypeRoleFillerRealis, Warning> trfrToWarning =
          MultimapUtils.deriveFromKeys(
              trfrToReponses.keySet(), new Function<TypeRoleFillerRealis, Iterable<Warning>>() {
                final Function<Response, Iterable<Warning>>
                    r2w = MultimapUtils.multiMapAsFunction(warnings);

                @Override
                public Iterable<Warning> apply(final TypeRoleFillerRealis input) {
                  return ImmutableSet.copyOf(
                      Iterables.concat(Iterables.transform(trfrToReponses.get(input), r2w)));
                }
              });
      final Multimap<String, TypeRoleFillerRealis> typeToTRFR = Multimaps.index(
          trfrToReponses.keySet(), new Function<TypeRoleFillerRealis, String>() {
            @Override
            public String apply(final TypeRoleFillerRealis input) {
              return input.type().asString();
            }
          });

      for (final String type : Ordering.natural().sortedCopy(typeToTRFR.keySet())) {
        sb.append(href(type));
        sb.append("<h2>");
        sb.append(type);
        sb.append("</h2>\n");
        sb.append(closehref());
        sb.append("<div id=\"");
        sb.append(type);
        sb.append("\" style=\"display:none\">\n");

        sb.append("<ul>\n");

        for (final TypeRoleFillerRealis trfr : trfrOrdering.sortedCopy(typeToTRFR.get(type))) {
          final String trfrID =
              String.format("%s.%s", trfr.type().asString(), trfr.role().asString());
          sb.append("<li>\n");
          int totalWarnings = warningsDiv(sb, trfrToWarning.get(trfr));
          sb.append(String.format("<div id=\"%s\" style=\"display:inherit\" >", trfrID));
          sb.append("<h3>");
          sb.append(String.format("%s-%s:%s - %s", trfr.type().asString(), trfr.role().asString(),
              trfr.realis().name(), trfr.argumentCanonicalString().string()));
          sb.append("</h3>\n");
          sb.append(Strings.repeat("</div>", totalWarnings));

          addSection(sb,
              Iterables.transform(overallOrdering.sortedCopy(trfrToReponses.get(trfr)),
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

    private void addSection(final StringBuilder sb, final Iterable<String> responses) {
      sb.append("<ul>\n");
      for (String r : responses) {
        sb.append("<li>\n");
        sb.append(r);
        sb.append("</li>\n");
      }
      sb.append("</ul>\n");
    }

  }
}

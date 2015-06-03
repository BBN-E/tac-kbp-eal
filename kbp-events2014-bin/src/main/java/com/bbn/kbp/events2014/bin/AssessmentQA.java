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
import com.google.common.base.Functions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.io.CharSink;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
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
      log.info("serializing {}", docID.asString());
      htmlRenderer.renderTo(
          Files.asCharSink(new File(output, docID.asString() + ".html"), Charset.defaultCharset()),
          answerKey, generateWarnings(answerKey));
    }
  }

  private static Ordering<Response> overallOrdering = Ordering.compound(ImmutableList
      .of(Response.byEvent(), Response.byRole(), Response.byFillerString(),
          Response.byCASSttring()));
  private static Ordering<TypeRoleFillerRealis> trfrOrdering = Ordering.compound(ImmutableList.of(
      TypeRoleFillerRealis.byType(), TypeRoleFillerRealis.byRole(), TypeRoleFillerRealis.byCAS(),
      TypeRoleFillerRealis.byRealis()));
  private final static List<? extends WarningRule> warnings =
      ImmutableList.of(ConjunctionWarningRule.create(), OverlapWarningRule.create());


  private static ImmutableMultimap<Response, Warning> generateWarnings(
      AnswerKey answerKey) {
    ImmutableMultimap.Builder<Response, Warning> warningResponseBuilder =
        ImmutableMultimap.builder();
    for (WarningRule w : warnings) {
      Multimap<Response, Warning> warnings = w.applyWarning(answerKey);
      warningResponseBuilder.putAll(warnings);
    }
    return warningResponseBuilder.build();
  }

  public static void main(String... args) {
    try {
      trueMain(args);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  private final static class Warning {

    public enum SEVERITY {
      MAJOR("major", ".major {\n"
          + "box-sizing: border-box;\n"
          + "margin: 2px;\n"
          + "border-color: red;\n"
          + "background-color: red;\n"
          + "border-width: 2px;\n"
          + "border-style: solid;\n"
          + "visibility: inherit;\n"
          + "font-weight: bold;\n"
          + "}\n"),
      MINIOR("minor", ".minor {\n"
          + "box-sizing: border-box;\n"
          + "margin: 2px;\n"
          + "border-color: orange;\n"
          + "background-color: orange;\n"
          + "border-width: 2px;\n"
          + "border-style: solid;\n"
          + "visibility: inherit;\n"
          + "font-weight: bold;\n"
          + "}\n");

      final String CSSClassName;
      final String CSS;

      SEVERITY(final String cssClassName, final String css) {
        CSSClassName = cssClassName;
        CSS = css;
      }
    }

    final String warningString;
    final SEVERITY severity;

    private Warning(final String warningString, final SEVERITY severity) {
      this.warningString = warningString;
      this.severity = severity;
    }

    public static Warning create(final String warningString, final SEVERITY severity) {
      return new Warning(warningString, severity);
    }

    public static ImmutableSet<SEVERITY> extractSeverity(final Iterable<Warning> warnings) {
      Set<SEVERITY> severities = Sets.newHashSet();
      for (Warning w : warnings) {
        severities.add(w.severity);
      }
      return ImmutableSet.copyOf(severities);
    }


  }

  private interface WarningRule {

    SetMultimap<Response, Warning> applyWarning(final AnswerKey answerKey);

  }
//
//  private static final class DummyWarningRule implements WarningRule {
//
//    @Override
//    public SetMultimap<Response, Warning> applyWarning(final AnswerKey answerKey) {
//      ImmutableSetMultimap.Builder<Response, Warning> warnings = ImmutableSetMultimap.builder();
//      for (Response r : Iterables.transform(answerKey.annotatedResponses(),
//          AssessedResponse.Response)) {
//        warnings.put(r, warning());
//      }
//      return warnings.build();
//    }
//
//    @Override
//    public Warning warning() {
//      return Warning.DUMMY;
//    }
//  }

  private static abstract class TRFRWarning implements WarningRule {

    protected Multimap<TypeRoleFillerRealis, Response> extractTRFRs(final AnswerKey answerKey) {
      return Multimaps.index(
          Iterables.transform(answerKey.annotatedResponses(), AssessedResponse.Response),
          TypeRoleFillerRealis
              .extractFromSystemResponse(answerKey.corefAnnotation().laxCASNormalizerFunction()));
    }
  }

  private static class OverlapWarningRule extends TRFRWarning {

    @Override
    public ImmutableSetMultimap<Response, Warning> applyWarning(final AnswerKey answerKey) {
      final Multimap<TypeRoleFillerRealis, Response> trfrToResponse = extractTRFRs(answerKey);
      final ImmutableSetMultimap.Builder<Response, Warning> warnings =
          ImmutableSetMultimap.builder();
      for (List<TypeRoleFillerRealis> pair : Sets
          .cartesianProduct(ImmutableList.of(trfrToResponse.keySet(), trfrToResponse.keySet()))) {
        TypeRoleFillerRealis fst = pair.get(0);
        TypeRoleFillerRealis snd = pair.get(1);
        // skip when they are identical or do not match types
        if (fst.equals(snd) || fst.type() != snd.type() || fst.role() != snd.role()) {
          continue;
        }
        warnings.putAll(findOverlap(trfrToResponse.get(fst), trfrToResponse.get(snd)));
      }
      return warnings.build();
    }


    protected Multimap<? extends Response, ? extends Warning> findOverlap(
        final Iterable<Response> first,
        final Iterable<Response> second) {
      final ImmutableMultimap.Builder<Response, Warning> result = ImmutableMultimap.builder();
      for (Response a : first) {
        for (Response b : second) {
          if (a == b || b.canonicalArgument().string().trim().isEmpty() || a.role() != b.role()
              || a.type() != b.type()) {
            continue;
          }
          // check for complete containment
          if (a.canonicalArgument().string().contains(b.canonicalArgument().string())) {
            log.info("adding \"{}\" and \"{}\" by complete string containment",
                b.canonicalArgument().string(), a.canonicalArgument().string());
            //result.put(a, warning());
            result.put(b, Warning.create(
                String.format("Conainted by %s", a.canonicalArgument().string()),
                Warning.SEVERITY.MINIOR));
          }
          /*
          // arbitrarily chosen magic constant
          if (diceScoreBySpace(a.canonicalArgument().string(), b.canonicalArgument().string())
              > 0.5) {
            log.info("adding {} and {} by dice score", a, b);
            result.put(a, Warning.OVERLAP);
            result.put(b, Warning.OVERLAP);
          }*/
        }
      }
      return result.build();
    }

    public static OverlapWarningRule create() {
      return new OverlapWarningRule();
    }
  }

  private static abstract class ConstainsStringWarningRule implements WarningRule {

    final ImmutableSet<String> verboten;

    protected ConstainsStringWarningRule(final ImmutableSet<String> verboten) {
      this.verboten = verboten;
    }

    public SetMultimap<Response, Warning> applyWarning(final AnswerKey answerKey) {
      final Iterable<Response> responses =
          Iterables.transform(answerKey.annotatedResponses(), AssessedResponse.Response);
      final ImmutableSetMultimap.Builder<Response, Warning> warnings =
          ImmutableSetMultimap.builder();
      for (Response r : responses) {
        if (apply(r)) {
          warnings.put(r, Warning.create(String.format("contains one of %s", verboten.toString()),
              Warning.SEVERITY.MAJOR));
          log.info("adding {} by contains string", r.canonicalArgument().string());
        }
      }
      return warnings.build();
    }

    protected boolean apply(final Response input) {
      Set<String> inputParts =
          Sets.newHashSet(input.canonicalArgument().string().trim().toLowerCase().split(
              "\\s+"));
      inputParts.retainAll(verboten);
      return inputParts.size() > 0;
    }
  }

  private final static class ConjunctionWarningRule extends ConstainsStringWarningRule {

    final static ImmutableSet<String> conjunctions = ImmutableSet.of("and", "or");

    protected ConjunctionWarningRule() {
      super(conjunctions);
    }

    public static ConjunctionWarningRule create() {
      return new ConjunctionWarningRule();
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
      return "body {\n"
          + "background-color: rgb(128,128,128);\n"
          + "}\n"
          + "* {\n"
          + "\tvisibility: inherit;\n"
          + "}\n";
    }

    private static String CSS() {
      final StringBuilder sb = new StringBuilder("<style>\n");
      for (Warning.SEVERITY s : Warning.SEVERITY.values()) {
        sb.append(s.CSS);
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

    private static int warningsDiv(final StringBuilder sb,
        final Iterable<Warning.SEVERITY> severities) {
      int total = 0;
      for (final Warning.SEVERITY s : severities) {
        sb.append("<div style=\"");
        sb.append(s.CSSClassName);
        sb.append("\" class=\"");
        sb.append(s.CSSClassName);
        sb.append("\">");
        total += 1;
      }
      return total;
    }

    public void renderTo(final CharSink sink, final AnswerKey answerKey,
        final ImmutableMultimap<Response, Warning> warnings)
        throws IOException {
      final StringBuilder sb = new StringBuilder();
      sb.append(htmlHeader());
      sb.append(String.format("<title>%s</title>", answerKey.docId().asString()));
      sb.append(javascript());
      sb.append(CSS());
      sb.append(bodyHeader());
      sb.append("<h1>");
      sb.append(docID);
      sb.append("</h1>\n");

      final ImmutableMultimap<TypeRoleFillerRealis, Response> trfrToAllResponses = Multimaps
          .index(Iterables.transform(answerKey.annotatedResponses(), AssessedResponse.Response),
              TypeRoleFillerRealis
                  .extractFromSystemResponse(
                      answerKey.corefAnnotation().laxCASNormalizerFunction()));
      final ImmutableMultimap<TypeRoleFillerRealis, Response> trfrToErrorfulReponses = Multimaps
          .index(warnings.keySet(), TypeRoleFillerRealis
              .extractFromSystemResponse(answerKey.corefAnnotation().laxCASNormalizerFunction()));

      final ImmutableMultimap<TypeRoleFillerRealis, Warning> trfrToWarning =
          MultimapUtils.composeToSetMultimap(trfrToErrorfulReponses, warnings);
      final Multimap<String, TypeRoleFillerRealis> typeToTRFR = Multimaps.index(
          trfrToAllResponses.keySet(),
          Functions.compose(Functions.toStringFunction(), TypeRoleFillerRealis.Type));

      for (final String type : Ordering.natural().sortedCopy(typeToTRFR.keySet())) {
        final ImmutableSet<Warning> typeWarnings =
            MultimapUtils.composeToSetMultimap(typeToTRFR, trfrToWarning).get(type);
        int typeWarningsCount = warningsDiv(sb, Warning.extractSeverity(typeWarnings));
        sb.append(href(type));
        sb.append("<h2>");
        sb.append(type);
        sb.append("</h2>\n");
        sb.append(closehref());
        sb.append(Strings.repeat("</div>", typeWarningsCount));
        sb.append("<div id=\"");
        sb.append(type);
        sb.append("\" style=\"display:none\">\n");
        //sb.append("<ul>\n");

        for (final TypeRoleFillerRealis trfr : trfrOrdering.sortedCopy(typeToTRFR.get(type))) {
          log.info("serializing trfr {}", trfr);
          final String trfrID =
              String.format("%s.%s", trfr.type().asString(), trfr.role().asString());
          // sb.append("<li>\n");
          final String readableTRFR =
              String.format("%s-%s:%s - %s", trfr.type().asString(), trfr.role().asString(),
                  trfr.realis().name(), trfr.argumentCanonicalString().string());
          int totalWarnings = warningsDiv(sb, Warning.extractSeverity(trfrToWarning.get(trfr)));
          sb.append(href(trfr.uniqueIdentifier()));
          sb.append(String.format("<h3>%s</h3>", readableTRFR));
          sb.append(closehref());
          sb.append(Strings.repeat("</div>", totalWarnings));

          sb.append(
              String.format("<div id=\"%s\" style=\"display:none\" >", trfr.uniqueIdentifier()));
          totalWarnings = warningsDiv(sb, Warning.extractSeverity(trfrToWarning.get(trfr)));
          sb.append(Strings.repeat("</div>", totalWarnings));

          addSection(sb, overallOrdering.sortedCopy(trfrToAllResponses.get(trfr)), warnings);
          sb.append("</div>\n");

          //sb.append("</li>\n");
        }
        //sb.append("</ul>\n");
        sb.append("</div>\n");
        //sb.append("</li>\n");
      }
      sb.append(bodyFooter());
      sb.append(htmlFooter());

      sink.write(sb.toString());
    }

    private static final Function<Response, String> responseToString() {
      return new Function<Response, String>() {
        @Override
        public String apply(final Response r) {
          return String.format("%s: %s", r.realis().name(), r.canonicalArgument().string());
        }
      };
    }

    private void addSection(final StringBuilder sb, final Iterable<Response> responses,
        final ImmutableMultimap<Response, Warning> warnings) {
      sb.append("<ul>\n");
      int with_warning = 0;
      int without_warning = 0;
      for (Response r : responses) {
        sb.append("<li>\n");
        final String responseString = responseToString().apply(r);
        if (warnings.containsKey(r)) {
          int totalWarnings = warningsDiv(sb, Warning.extractSeverity(warnings.get(r)));
          sb.append(href(responseString));
          sb.append(responseString);
          sb.append(closehref());
          sb.append(String.format("<div id=\"%s\" style=\"display:none\" >", responseString));
          sb.append("<ul>\n");
          for(Warning w: warnings.get(r)) {
            sb.append(String.format("<li>%s</li>\n", w.warningString));
          }
          sb.append("</ul>\n");
          sb.append("</div>");
          sb.append(Strings.repeat("</div>", totalWarnings));
          with_warning++;
        } else {
          sb.append(responseString);
          without_warning++;
        }

        sb.append("</li>\n");
      }
      sb.append("</ul>\n");
      log.info("saved responses with {} and without {} warnings", with_warning, without_warning);
    }

  }
}

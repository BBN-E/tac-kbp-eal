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
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.io.CharSink;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

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
      final DocumentRenderer htmlRenderer =
          new DocumentRenderer(docID.asString(), answerKey.corefAnnotation());
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
      for (final String bad : verboten) {
        if (input.canonicalArgument().string().contains(bad)) {
          return true;
        }
      }
      return false;
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
      return ".conjunction";
    }

    @Override
    public String CSSForWarning() {
      return CSSStyleName() + " {\n"
          + "color: red;\n"
          + "margin-left: 14px;\n"
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
      return CSSStyleName() + " {\n"
          + "color: blue;\n"
          + "margin-left: 12px;\n"
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
        if(a.canonicalArgument().string().trim().isEmpty()) {
          continue;
        }
        for (Response b : input) {
          if (a == b || b.canonicalArgument().string().trim().isEmpty()) {
            continue;
          }
          // check for complete containment
          if (a.canonicalArgument().string().contains(b.canonicalArgument().string())) {
            log.info("adding {} and {} by complete string containment", a, b);
            output.add(a);
            output.add(b);
          }
          // arbitrarily chosen magic constant
          if (diceScoreBySpace(a.canonicalArgument().string(), b.canonicalArgument().string())
              > 0.5) {
            log.info("adding {} and {} by dice score", a, b);
            output.add(a);
            output.add(b);
          }
        }
      }
      return output.build();
    }
  }


  private final static class TRFRResponseSet {

    final static private List<Warning> warnings =
        ImmutableList.of(new ConjunctionWarning(), new OverlapWarning());

    private final TypeRoleFillerRealis trfr;
    private final Iterable<Response> responses;
    private final Multimap<Warning, Response> warningResponseMultimap;

    public TRFRResponseSet(final TypeRoleFillerRealis trfr, final Iterable<Response> responses,
        final Multimap<Warning, Response> warningResponseMultimap) {
      this.trfr = trfr;
      this.responses = responses;
      this.warningResponseMultimap = warningResponseMultimap;
    }

    public String toHTML() {
      final StringBuilder sb = new StringBuilder();
      final String trfrID = String.format("%s.%s", trfr.type().asString(), trfr.role().asString());
      sb.append("<li>\n");
      sb.append(String.format("<div id=\"%s\" style=\"display:inherit\" >", trfrID));
      sb.append("<h3>");
      sb.append(String.format("%s-%s:%s - %s", trfr.type().asString(), trfr.role().asString(),
          trfr.realis().name(), trfr.argumentCanonicalString().string()));
      sb.append("</h3>\n");

      addSection(sb, Iterables.transform(responses,
          new Function<Response, String>() {
            @Override
            public String apply(final Response r) {
              return String
                  .format("%s: %s", r.realis().name(), r.canonicalArgument().string());
            }
          }));
      sb.append("</div>\n");
      sb.append("</li>\n");
      return sb.toString();
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

    @Override
    public String toString() {
      return "TRFRResponseSet{" +
          "trfr=" + trfr +
          ", responses=" + responses +
          ", warningResponseMultimap=" + warningResponseMultimap +
          '}';
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final TRFRResponseSet that = (TRFRResponseSet) o;

      if (!trfr.equals(that.trfr)) {
        return false;
      }
      if (!responses.equals(that.responses)) {
        return false;
      }
      return warningResponseMultimap.equals(that.warningResponseMultimap);

    }

    @Override
    public int hashCode() {
      int result = trfr.hashCode();
      result = 31 * result + responses.hashCode();
      result = 31 * result + warningResponseMultimap.hashCode();
      return result;
    }

    public static TRFRResponseSet create(final TypeRoleFillerRealis trfr,
        final Iterable<Response> responses) {
      ImmutableMultimap.Builder<Warning, Response> warningResponseBuilder =
          ImmutableMultimap.builder();
      for (Warning w : warnings) {
        warningResponseBuilder.putAll(w, w.applyWarning(responses));
      }
      return new TRFRResponseSet(trfr, responses, warningResponseBuilder.build());
    }

    public static Iterable<TRFRResponseSet> createFromMultiMap(
        final Multimap<TypeRoleFillerRealis, Response> trfrMap) {
      ImmutableList.Builder<TRFRResponseSet> trfrResponseSetBuilder = ImmutableList.builder();
      for (Map.Entry<TypeRoleFillerRealis, Collection<Response>> entry : trfrMap.asMap()
          .entrySet()) {
        trfrResponseSetBuilder.add(TRFRResponseSet.create(entry.getKey(), entry.getValue()));
      }
      return trfrResponseSetBuilder.build();
    }

    public static Function<TRFRResponseSet, TypeRoleFillerRealis> extractTRFR() {
      return new Function<TRFRResponseSet, TypeRoleFillerRealis>() {
        @Override
        public TypeRoleFillerRealis apply(final TRFRResponseSet input) {
          return input.trfr;
        }
      };
    }

    public static Function<TRFRResponseSet, Iterable<Warning>> getApplicableWarnings() {
      return new Function<TRFRResponseSet, Iterable<Warning>>() {
        @Override
        public Iterable<Warning> apply(final TRFRResponseSet input) {
          return input.warningResponseMultimap.keySet();
        }
      };
    }
  }

  private final static class DocumentRenderer {

    final private Multimap<String, Response> typeToResponse = HashMultimap.create();
    final private String docID;
    private CorefAnnotation corefAnnotation;

    private DocumentRenderer(final String docID, final CorefAnnotation corefAnnotation) {
      this.docID = docID;
      this.corefAnnotation = corefAnnotation;
    }

    private static String bodyHeader() {
      return "<body>";
    }

    private static String htmlHeader() {
      return "<html>";
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
      sb.append("<meta charset=\"UTF-8\">");
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
        final Iterable<TRFRResponseSet> responseSets =
            Sets.newHashSet(TRFRResponseSet.createFromMultiMap(trfrToResponse));
        log.info("multimap: {}", trfrToResponse);
        log.info("responsesSets: {}", responseSets);
        final SortedMap<TypeRoleFillerRealis, TRFRResponseSet> sortedMap =
            ImmutableSortedMap.copyOf(Maps.uniqueIndex(responseSets, TRFRResponseSet.extractTRFR()),
                trfrOrdering);
        final ImmutableSet<Warning> applicableWarnings = ImmutableSet.copyOf(Iterables.concat(
            Maps.transformValues(sortedMap, TRFRResponseSet.getApplicableWarnings()).values()));
        sb.append(href(type));
        sb.append("<h2>");
        sb.append(type);
        sb.append("</h2>\n");
        sb.append(closehref());
        sb.append("<div id=\"");
        sb.append(type);
        sb.append("\" style=\"display:none\">\n");

        sb.append("<ul>\n");
        for (final TypeRoleFillerRealis trfr : sortedMap.keySet()) {
          sb.append(sortedMap.get(trfr).toHTML());
        }
        sb.append("</ul>\n");
        sb.append("</div>\n");
        sb.append("</li>\n");
      }
      sb.append(bodyFooter());
      sb.append(htmlFooter());

      sink.write(sb.toString());
    }
  }
}

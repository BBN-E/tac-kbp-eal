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
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
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
    warnings = ImmutableList.of(ConjunctionWarningRule.create(), OverlapWarningRule.create(),
        ConflictingTypeWarningRule.create(params.getString("argFile"), params.getString("roleFile")));

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
  private static List<? extends WarningRule> warnings = null;



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

  private static String readableTRFR(TypeRoleFillerRealis trfr) {
    return String.format("%s-%s:%s - %s", trfr.type().asString(), trfr.role().asString(),
        trfr.realis().name(), trfr.argumentCanonicalString().string());
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

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final Warning warning = (Warning) o;

      if (warningString != null ? !warningString.equals(warning.warningString)
                                : warning.warningString != null) {
        return false;
      }
      return severity == warning.severity;

    }

    @Override
    public int hashCode() {
      int result = warningString != null ? warningString.hashCode() : 0;
      result = 31 * result + (severity != null ? severity.hashCode() : 0);
      return result;
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

    protected OverlapWarningRule() {

    }

    protected boolean warningAppliesTo(TypeRoleFillerRealis fst, TypeRoleFillerRealis snd) {
      // we don't want to handle the same TRFR twice, but we do want identical TRFRs to be an error
      if (fst == snd) {
        return false;
      }
      // only apply to things of the same type
      if (fst.equals(snd) || fst.type() != snd.type() || fst.role() != snd.role()) {
        return false;
      }
      return true;
    }

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
        if (warningAppliesTo(fst, snd)) {
          warnings.putAll(findOverlap(fst, trfrToResponse.get(fst), snd, trfrToResponse.get(snd)));
        }
      }
      return warnings.build();
    }


    protected Multimap<? extends Response, ? extends Warning> findOverlap(
        final TypeRoleFillerRealis fst, final Iterable<Response> first,
        final TypeRoleFillerRealis snd, final Iterable<Response> second) {
      final ImmutableMultimap.Builder<Response, Warning> result = ImmutableMultimap.builder();
      for (Response a : first) {
        for (Response b : second) {
          if (a == b || b.canonicalArgument().string().trim().isEmpty() || a.role() != b.role()
              || a.type() != b.type()) {
            continue;
          }
          // check for complete containment
          if (a.canonicalArgument().string().contains(b.canonicalArgument().string())) {
            log.info("adding \"{}\" from {} by complete string containment in \"{}\" from {}",
                b.canonicalArgument().string(), snd, a.canonicalArgument().string(), fst);
            //result.put(a, warning());
            result.put(b, Warning.create(
                String.format("Contained by \"%s\" in \"%s\"", a.canonicalArgument().string(),
                    readableTRFR(fst)), Warning.SEVERITY.MINIOR));
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

  private static class ConflictingTypeWarningRule extends OverlapWarningRule {

    private final ImmutableTable<Symbol, Symbol, Set<Symbol>> argTypeRoleType;

    private ConflictingTypeWarningRule(final Table<Symbol, Symbol, Set<Symbol>> argTypeRoleType) {
      super();
      this.argTypeRoleType = ImmutableTable.copyOf(argTypeRoleType);
    }


    @Override
    protected boolean warningAppliesTo(TypeRoleFillerRealis fst, TypeRoleFillerRealis snd) {
      if (fst == snd || fst.type().equals(snd.type())) {
        return false;
      }
      return true;
    }

    @Override
    protected Multimap<? extends Response, ? extends Warning> findOverlap(
        final TypeRoleFillerRealis fst, final Iterable<Response> first,
        final TypeRoleFillerRealis snd, final Iterable<Response> second) {
      final ImmutableMultimap.Builder<Response, Warning> result = ImmutableMultimap.builder();
      for (Response a : first) {
        Set<Symbol> atypes = argTypeRoleType.get(a.type(), a.role());
//        log.info("a response {} type: {} role: {} has types {}", a, a.type(), a.role(), atypes);
        for (Response b : second) {
          if (a == b || b.canonicalArgument().string().trim().isEmpty()) {
            continue;
          }
          if(a.canonicalArgument().equals(b.canonicalArgument())) {
//            log.info("b response {} type: {} role: {} has types {}", b, b.type(), b.role(), argTypeRoleType.get(b.type(), b.role()));
            Set<Symbol> btypes = Sets.newHashSet(argTypeRoleType.get(b.type(), b.role()));
//            log.info("b response {} type: {} role: {} has types {}", b, b.type(), b.role(), btypes);
            btypes.retainAll(atypes);
            if(btypes.size() == 0) {
            result.put(b, Warning.create(String
                .format("%s has same string as %s but mismatched types %s/%s and %s/%s in trfr %s", a.canonicalArgument().string(),
                    b.canonicalArgument().string(), a.type().asString(), a.role().asString(), b.type().asString(), b.role().asString(), readableTRFR(fst)), Warning.SEVERITY.MINIOR));
//            result.put(b, Warning.create(String
//                .format("%s has same string as %s by mismatched types %s/%s and %s/%s in trfr %s", b.canonicalArgument().string(),
//                    a.canonicalArgument().string(), b.type().asString(), b.role().asString(), a.type().asString(), a.role().asString(), readableTRFR(snd)), Warning.SEVERITY.MINIOR));

            }
          }


//          if (a.canonicalArgument().equals(b.canonicalArgument()) && !a.type().equals(b.type())) {
//            result.put(b, Warning.create(String
//                .format("%s has same string as %s by mismatched types %s and %s", a.toString(),
//                    b.toString(), a.type().asString(), b.type().asString()), Warning.SEVERITY.MINIOR));
//            result.put(b, Warning.create(String
//                .format("%s has same string as %s by mismatched types %s and %s", b.toString(),
//                    a.toString(), b.type().asString(), a.type().asString()), Warning.SEVERITY.MINIOR));
//          }
        }
      }
      return result.build();
    }

    public static ConflictingTypeWarningRule create(String argsFile, String rolesFile)
        throws IOException {
      Table<Symbol, Symbol, Set<Symbol>> argTypeRoleType = HashBasedTable.create();
      Multimap<Symbol, Symbol> roleToTypes = HashMultimap.create();
      for(String line : Files.readLines(new File(rolesFile), Charset.defaultCharset())) {
        String[] parts = line.split("\t");
        for(String type: parts[1].split(",\\s+")) {
          roleToTypes.put(Symbol.from(parts[0].trim()), Symbol.from(type.trim()));
        }
      }

      for(String line : Files.readLines(new File(argsFile), Charset.defaultCharset())) {
        String[] parts = line.trim().split("\t");
        Symbol type = Symbol.from(parts[0].trim());
        for(int i = 1; i < parts.length; i++) {
          Symbol roleKey = Symbol.from(parts[i].trim());
          Symbol role = Symbol.from(parts[i].trim().replaceAll("[^A-Za-z-.\\s]", ""));
          log.info("type: {}, roleKey {}, role {}", type, roleKey, role);
          if(argTypeRoleType.contains(type, role)) {
            Set<Symbol> types = new HashSet<Symbol>(argTypeRoleType.get(type, role));
            types.addAll(roleToTypes.get(roleKey));
            argTypeRoleType.put(type, role, ImmutableSet.copyOf(types));
          } else {
            argTypeRoleType.put(type, role, ImmutableSet.copyOf(roleToTypes.get(roleKey)));
          }
        }
      }

      for(Symbol r: roleToTypes.keySet()) {
        log.info("{} -> {}", r, roleToTypes.get(r));
      }

      for(Symbol r: argTypeRoleType.rowKeySet()) {
        for (Symbol c: argTypeRoleType.columnKeySet()) {
          if(c != null) {
            log.info("{}.{}: {}", r, c, argTypeRoleType.get(r, c));
          }
        }
      }

      return new ConflictingTypeWarningRule(ImmutableTable.copyOf(argTypeRoleType));
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
              Warning.SEVERITY.MINIOR));
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


    protected static <K> Optional<Warning.SEVERITY> extractWarningForType(Iterable<Warning> warnings,
        Iterable<K> all) {
      int numWarnings = 0;
      for(Warning w: warnings) {
        numWarnings++;
        // any severe warning is propogated
        if(w.severity.equals(Warning.SEVERITY.MAJOR)) {
          return Optional.of(Warning.SEVERITY.MAJOR);
        }
      }
      int totalItems = ImmutableList.copyOf(all).size();
      // if there are a lot of warning compared to the number of things, this is severe
      // there can be more than one warning per thing
      // 1 is a UI hack - this way singletons don't get propogated
      if((numWarnings >= totalItems/2) && totalItems > 1) {
        return Optional.of(Warning.SEVERITY.MAJOR);
      }
      if(numWarnings > 0) {
        return Optional.of(Warning.SEVERITY.MINIOR);
      }
      return Optional.absent();
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
        final Optional<Warning.SEVERITY> typeWarning = extractWarningForType(typeWarnings,
            typeToTRFR.get(type));
        if(typeWarning.isPresent()) {
          warningsDiv(sb, ImmutableList.of(typeWarning.get()));
        }
        sb.append(href(type));
        sb.append("<h2>");
        sb.append(type);
        sb.append("</h2>\n");
        sb.append(closehref());
        if(typeWarning.isPresent()) {
          // one close div needed for one warning
          sb.append(Strings.repeat("</div>", 1));
        }
        sb.append("<div id=\"");
        sb.append(type);
        sb.append("\" style=\"display:none\">\n");

        for (final TypeRoleFillerRealis trfr : trfrOrdering.sortedCopy(typeToTRFR.get(type))) {
          log.info("serializing trfr {}", trfr);
          final String readableTRFR = readableTRFR(trfr);
          final Optional<Warning.SEVERITY> trfrWarning = extractWarningForType(trfrToWarning.get(trfr), trfrToAllResponses.get(trfr));
          if(trfrWarning.isPresent()) {
            warningsDiv(sb, ImmutableList.of(trfrWarning.get()));
          }
          sb.append(href(trfr.uniqueIdentifier()));
          sb.append(String.format("<h3>%s</h3>", readableTRFR));
          sb.append(closehref());
          if(trfrWarning.isPresent()) {
            // only need one close div for a warning
            sb.append(Strings.repeat("</div>", 1));
          }

          sb.append(
              String.format("<div id=\"%s\" style=\"display:none\" >", trfr.uniqueIdentifier()));
          int totalWarnings = warningsDiv(sb, Warning.extractSeverity(trfrToWarning.get(trfr)));
          sb.append(Strings.repeat("</div>", totalWarnings));

          addSection(sb, overallOrdering.sortedCopy(trfrToAllResponses.get(trfr)), warnings);
          sb.append("</div>\n");

        }
        sb.append("</div>\n");
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
          sb.append(href(r.hashCode() + ""));
          sb.append(responseString);
          sb.append(closehref());
          sb.append(String.format("<div id=\"%s\" style=\"display:none\" >", r.hashCode()));
          sb.append("<ul>\n");
          for (Warning w : warnings.get(r)) {
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

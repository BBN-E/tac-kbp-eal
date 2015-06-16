package com.bbn.kbp.events2014.bin.QA;

import com.bbn.bue.common.collections.MultimapUtils;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.bin.QA.Warnings.Warning;
import com.bbn.kbp.events2014.bin.QA.Warnings.WarningRule;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.io.CharSink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Created by jdeyoung on 6/4/15.
 */
final class QADocumentRenderer {

  private static final Logger log = LoggerFactory.getLogger(QADocumentRenderer.class);
  private final Ordering<Response> overallOrdering;
  private final Ordering<TypeRoleFillerRealis> trfrOrdering;
  private final ImmutableMap<String, String> warningTypeToDescription;

  private QADocumentRenderer(final Ordering<Response> overallOrdering,
      final Ordering<TypeRoleFillerRealis> trfrOrdering,
      final Map<String, String> warningTypeToDescription) {
    this.trfrOrdering = trfrOrdering;
    this.overallOrdering = overallOrdering;
    this.warningTypeToDescription = ImmutableMap.copyOf(warningTypeToDescription);
  }

  public static QADocumentRenderer createWithDefaultOrdering(
      final ImmutableList<WarningRule> warnings) {
    final Map<String, String> warningToType =
        Maps.transformValues(Maps.uniqueIndex(warnings, new Function<WarningRule, String>() {
          @Override
          public String apply(final WarningRule input) {
            return input.getTypeString();
          }
        }), new Function<WarningRule, String>() {
          @Override
          public String apply(final WarningRule input) {
            return input.getTypeDescription();
          }
        });
    return new QADocumentRenderer(DEFAULT_OVERALL_ORDERING, DEFAULT_TRFR_ORDERING, warningToType);
  }

  private static final Ordering<Response> DEFAULT_OVERALL_ORDERING = Ordering.compound(ImmutableList
      .of(Response.byEvent(), Response.byRole(), Response.byFillerString(),
          Response.byCASSttring()));
  private static final Ordering<TypeRoleFillerRealis> DEFAULT_TRFR_ORDERING =
      Ordering.compound(ImmutableList.of(
          TypeRoleFillerRealis.byType(), TypeRoleFillerRealis.byRole(),
          TypeRoleFillerRealis.byCAS(),
          TypeRoleFillerRealis.byRealis()));

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
    for (Warning.Severity s : Warning.Severity.values()) {
      sb.append(s.CSS());
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
      final Iterable<Warning.Severity> severities) {
    int total = 0;
    for (final Warning.Severity s : severities) {
      sb.append("<div style=\"");
      sb.append(s.CSSClassName());
      sb.append("\" class=\"");
      sb.append(s.CSSClassName());
      sb.append("\">");
      total += 1;
    }
    return total;
  }


  protected static <K> Optional<Warning.Severity> extractWarningForType(
      Iterable<Warning> warnings,
      Iterable<K> all) {
    int numWarnings = 0;
    for (Warning w : warnings) {
      numWarnings++;
      // any severe warning is propogated
      if (w.severity().equals(Warning.Severity.MAJOR)) {
        return Optional.of(Warning.Severity.MAJOR);
      }
    }
    int totalItems = ImmutableList.copyOf(all).size();
    // if there are a lot of warning compared to the number of things, this is severe
    // there can be more than one warning per thing
    // 1 is a UI hack - this way singletons don't get propogated
    if ((numWarnings >= totalItems / 2) && totalItems > 1) {
      return Optional.of(Warning.Severity.MAJOR);
    }
    if (numWarnings > 0) {
      return Optional.of(Warning.Severity.MINOR);
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
    sb.append(answerKey.docId());
    sb.append("</h1>\n");

    sb.append("<div>");
    sb.append(href("Warning Strings"));
    sb.append(String.format("<h2>%s</h2>", "Warning Strings"));
    sb.append(closehref());
    sb.append("<div id=\"Warning Strings\" style=\"display:none\">\n");
    sb.append("<ul>");
    for(Map.Entry<String, String> e : warningTypeToDescription.entrySet()) {
      sb.append(String.format("<li>%s: %s</li>\n", e.getKey(), e.getValue()));
    }
    sb.append("</ul>");
    sb.append("</div>");
    sb.append("</div>");

    final Function<Response, TypeRoleFillerRealis> responseToTRFR = TypeRoleFillerRealis
        .extractFromSystemResponse(answerKey.corefAnnotation().laxCASNormalizerFunction());
    final ImmutableMultimap<TypeRoleFillerRealis, Response> trfrToAllResponses = Multimaps
        .index(Iterables.transform(answerKey.annotatedResponses(), AssessedResponse.Response),
            responseToTRFR);
    final ImmutableMultimap<TypeRoleFillerRealis, Response> trfrToErrorfulReponses = Multimaps
        .index(warnings.keySet(), responseToTRFR);

    final ImmutableMultimap<TypeRoleFillerRealis, Warning> trfrToWarning =
        MultimapUtils.composeToSetMultimap(trfrToErrorfulReponses, warnings);
    final Multimap<String, TypeRoleFillerRealis> typeToTRFR = Multimaps.index(
        trfrToAllResponses.keySet(),
        Functions.compose(Functions.toStringFunction(), TypeRoleFillerRealis.Type));

    final ImmutableSetMultimap<String, Warning>
        typesToWarnings = MultimapUtils.composeToSetMultimap(typeToTRFR, trfrToWarning);

    for (final String type : Ordering.natural().sortedCopy(typeToTRFR.keySet())) {
      final ImmutableSet<Warning> typeWarnings = typesToWarnings.get(type);
      final Optional<Warning.Severity> typeWarning = extractWarningForType(typeWarnings,
          typeToTRFR.get(type));
      if (typeWarning.isPresent()) {
        warningsDiv(sb, ImmutableList.of(typeWarning.get()));
      }
      sb.append(href(type));
      sb.append("<h2>");
      sb.append(type);
      sb.append("</h2>\n");
      sb.append(closehref());
      if (typeWarning.isPresent()) {
        // one close div needed for one warning
        sb.append(Strings.repeat("</div>", 1));
      }
      sb.append("<div id=\"");
      sb.append(type);
      sb.append("\" style=\"display:none\">\n");

      for (final TypeRoleFillerRealis trfr : trfrOrdering.sortedCopy(typeToTRFR.get(type))) {
        log.info("serializing trfr {}", trfr);
        final String readableTRFR = AssessmentQA.readableTRFR(trfr);
        final Optional<Warning.Severity> trfrWarning =
            extractWarningForType(trfrToWarning.get(trfr), trfrToAllResponses.get(trfr));
        if (trfrWarning.isPresent()) {
          warningsDiv(sb, ImmutableList.of(trfrWarning.get()));
        }
        sb.append(href(trfr.uniqueIdentifier()));
        sb.append(String.format("<h3>%s</h3>", readableTRFR));
        sb.append(closehref());
        if (trfrWarning.isPresent()) {
          // only need one close div for a warning
          sb.append(Strings.repeat("</div>", 1));
        }

        sb.append(
            String.format("<div id=\"%s\" style=\"display:none\" >", trfr.uniqueIdentifier()));
        int totalWarnings = warningsDiv(sb, Warning
            .extractSeverity(trfrToWarning.get(trfr)));
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
        int totalWarnings = warningsDiv(sb, Warning
            .extractSeverity(warnings.get(r)));
        sb.append(href(r.hashCode() + ""));
        sb.append(responseString);
        sb.append(closehref());
        sb.append(String.format("<div id=\"%s\" style=\"display:none\" >", r.hashCode()));
        sb.append("<ul>\n");
        for (Warning w : warnings.get(r)) {
          sb.append(String.format("<li>%s: %s</li>\n", w.typeString(), w.warningString()));
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

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
final class AssessmentQADocumentRenderer extends QADocumentRenderer {

  private static final Logger log = LoggerFactory.getLogger(AssessmentQADocumentRenderer.class);

  private AssessmentQADocumentRenderer(final Ordering<Response> overallOrdering,
      final Ordering<TypeRoleFillerRealis> trfrOrdering,
      final Map<String, String> warningTypeToDescription) {
    super(overallOrdering, trfrOrdering, warningTypeToDescription);
  }

  public static AssessmentQADocumentRenderer createWithDefaultOrdering(
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
    return new AssessmentQADocumentRenderer(DEFAULT_OVERALL_ORDERING, DEFAULT_TRFR_ORDERING, warningToType);
  }

  @Override
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
    sb.append(href("WarningStrings"));
    sb.append(String.format("<h1>%s</h1>", "Warning Strings"));
    sb.append(closehref());
    sb.append("<div id=\"WarningStrings\" style=\"display:none\">\n");
    sb.append("<ul>");
    for (Map.Entry<String, String> e : warningTypeToDescription.entrySet()) {
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
      } else {
        // skip TRFR types without warnings
        continue;
      }
      sb.append("<hr/>");
      sb.append(href(type));
      sb.append("<h1>");
      sb.append(type);
      sb.append("</h1>\n");
      sb.append(closehref());
      if (typeWarning.isPresent()) {
        // one close div needed for one warning
        sb.append(Strings.repeat("</div>", 1));
      }
      sb.append("<div id=\"");
      sb.append(type);
      sb.append("\" style=\"display:block\">\n");

      for (final TypeRoleFillerRealis trfr : trfrOrdering.sortedCopy(typeToTRFR.get(type))) {
        log.info("serializing trfr {}", trfr);
        final String readableTRFR = AssessmentQA.readableTRFR(trfr, trfrToAllResponses.get(trfr));
        final Optional<Warning.Severity> trfrWarning =
            extractWarningForType(trfrToWarning.get(trfr), trfrToAllResponses.get(trfr));
        if (trfrWarning.isPresent()) {
          warningsDiv(sb, ImmutableList.of(trfrWarning.get()));
        } else {
          // skip TRFRs without warnings
          continue;
        }
        sb.append(href(trfr.uniqueIdentifier()));
        sb.append(String.format("<h3>%s</h3>", readableTRFR));
        sb.append(closehref());
        if (trfrWarning.isPresent()) {
          // only need one close div for a warning
          sb.append(Strings.repeat("</div>", 1));
          sb.append(
              String.format("<div id=\"%s\" style=\"display:inherit\" >", trfr.uniqueIdentifier()));
        } else {
          sb.append(String.format("<div id=\"%s\" style=\"display:none\" >", trfr.uniqueIdentifier()));
        }
        addSection(sb, overallOrdering.sortedCopy(trfrToAllResponses.get(trfr)), warnings);
        sb.append("</div>\n");
      }
      sb.append("</div>\n");
    }
    sb.append(bodyFooter());
    sb.append(htmlFooter());

    sink.write(sb.toString());
  }
}

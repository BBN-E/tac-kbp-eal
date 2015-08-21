package com.bbn.kbp.events2014.bin.QA;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.CorefAnnotation;
import com.bbn.kbp.events2014.KBPString;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.bin.QA.Warnings.Warning;
import com.bbn.kbp.events2014.bin.QA.Warnings.WarningRule;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.io.CharSink;

import java.io.IOException;
import java.util.Map;

/**
 * Created by jdeyoung on 6/30/15.
 */
public final class CorefDocumentRenderer extends QADocumentRenderer {

  CorefDocumentRenderer(
      final Ordering<Response> overallOrdering,
      final Ordering<TypeRoleFillerRealis> trfrOrdering,
      final Map<String, String> warningTypeToDescription) {
    super(overallOrdering, trfrOrdering, warningTypeToDescription);
  }

  public static CorefDocumentRenderer createWithDefaultOrdering(
      final ImmutableList<WarningRule<Integer>> warnings) {
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
    return new CorefDocumentRenderer(DEFAULT_OVERALL_ORDERING, DEFAULT_TRFR_ORDERING,
        warningToType);
  }

  public void renderTo(final CharSink sink, final AnswerKey answerKey,
      final ImmutableMultimap<Integer, Warning> warnings) throws IOException {
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

    // begin bullets
    sb.append("<div id=\"CASGroups\" style=\"display:block\">");
    for (final Integer CASGroup : warnings.keySet()) {
      appendCASGroup(sb, CASGroup, answerKey, warnings);
    }
    sb.append("</div>");

    sink.write(sb.toString());
  }

  public static ImmutableSet<Response> responsesForCASGroup(final Integer CASGroup,
      final AnswerKey answerKey) {
    final ImmutableSet.Builder<Response> responses = ImmutableSet.builder();
    final ImmutableSetMultimap<KBPString, Response> kbpStringToResponse =
        ImmutableSetMultimap.copyOf(
            Multimaps.index(answerKey.allResponses(), Response.CASFunction()));
    for (final KBPString kbpString : answerKey.corefAnnotation().clusterIDToMembersMap()
        .get(CASGroup)) {
      responses.addAll(kbpStringToResponse.get(kbpString));
    }
    return responses.build();
  }

  private static void appendCASStringList(final StringBuilder sb,
      final ImmutableCollection<KBPString> kbpStrings) {
    sb.append("CAS String List\n");
    sb.append("<ul>\n");
    for (final String kbpString : ImmutableSet.copyOf(
        Iterables.transform(kbpStrings, KBPString.Text))) {
      sb.append("<li>");
      sb.append(kbpString);
      sb.append("</li>\n");
    }
    sb.append("</ul>\n");
  }


  private static void appendTypeAndRoleSummaryForCAS(final StringBuilder sb,
      final Integer CASGroup, final AnswerKey answerKey) {
    final ImmutableMultimap<Symbol, Response> eventTypeToResponse =
        Multimaps.index(responsesForCASGroup(CASGroup, answerKey),
            Response.typeFunction());
    final ImmutableSetMultimap<Symbol, Symbol> eventTypeToRoles = ImmutableSetMultimap.copyOf(
        Multimaps.transformValues(eventTypeToResponse, Response.roleFunction()));
    final Joiner comma = Joiner.on(", ");

    sb.append("Summary of event type, role for this CAS\n");
    sb.append("<ul>\n");
    for (final Symbol type : eventTypeToRoles.keySet()) {
      sb.append("<li>");
      sb.append(type);
      sb.append(": ");
      sb.append(comma.join(eventTypeToRoles.get(type)));
      sb.append("</li>");
    }
    sb.append("</ul>");
  }


  private static void appendWarningsListForCAS(final StringBuilder sb,
      final ImmutableCollection<Warning> warnings) {
    final ImmutableSetMultimap<String, Warning> warningByType = ImmutableSetMultimap.copyOf(
        Multimaps.index(warnings,
            new Function<Warning, String>() {
              @Override
              public String apply(final Warning input) {
                return input.typeString();
              }
            }));
    sb.append("Warning list\n");
    sb.append("<ul>\n");

    for (final String warningType : warningByType.keySet()) {
      sb.append("<li>");
      sb.append(warningType);
      sb.append(" - ");
      sb.append("<ul>");
      for (final Warning warning : warningByType.get(warningType)) {
        sb.append("<li>");
        sb.append(warning.warningString());
        sb.append("</li>\n");
      }
      sb.append("</ul>");
      sb.append("</li>\n");
    }
    sb.append("</ul>");

  }

  private static void appendCASRoleList(final StringBuilder sb,
      final Integer CASGroup, final AnswerKey answerKey) {
    sb.append("CAS with role list\n");
    sb.append("<ul>");
    for (final KBPString kbpString : answerKey.corefAnnotation().clusterIDToMembersMap()
        .get(CASGroup)) {
      sb.append("<li>");
      sb.append(kbpString.string());
      sb.append("<ul>\n");
      for (final Response r : ImmutableSet.copyOf(
          Multimaps.index(answerKey.allResponses(), Response.CASFunction()).get(kbpString))) {
        sb.append(r.type());
        sb.append(", ");
        sb.append(r.role());
        sb.append(", ");
        sb.append(r.realis());
      }
      sb.append("</ul>");
      sb.append("</li>\n");
    }
    sb.append("</ul>");
  }

  private static void appendCASGroup(final StringBuilder sb, final Integer CASGroup,
      final AnswerKey answerKey, final ImmutableMultimap<Integer, Warning> warnings) {
    final CorefAnnotation coref = answerKey.corefAnnotation();
    sb.append("<div id=\"CASGroup-").append(CASGroup).append("\" style=\"display:inherit\"");
    sb.append("<ul>");

    //cas string list
    sb.append("<li>");
    appendCASStringList(sb, coref.clusterIDToMembersMap().get(CASGroup));
    sb.append("</li>");

    // summary of type and roles

    sb.append("<li>");
    appendTypeAndRoleSummaryForCAS(sb, CASGroup, answerKey);
    sb.append("</li>");

    // warnings list
    sb.append("<li>");
    appendWarningsListForCAS(sb, warnings.get(CASGroup));
    sb.append("</li>");

    // CAS with role list
    sb.append("<li>");
    appendCASRoleList(sb, CASGroup, answerKey);
    sb.append("</li>");

    sb.append("</ul>");
    sb.append("</div>");
  }
}

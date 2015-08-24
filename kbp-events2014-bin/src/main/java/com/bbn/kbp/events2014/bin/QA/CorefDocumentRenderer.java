package com.bbn.kbp.events2014.bin.QA;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.CorefAnnotation;
import com.bbn.kbp.events2014.KBPString;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.bin.QA.Warnings.CorefWarningRule;
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
import com.google.common.collect.Sets;
import com.google.common.io.CharSink;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Ugh these were not supposed to develop into objects that actually had real functionality Created
 * by jdeyoung on 6/30/15.
 */
public final class CorefDocumentRenderer extends QADocumentRenderer {

  CorefDocumentRenderer(
      final Ordering<Response> overallOrdering,
      final Ordering<TypeRoleFillerRealis> trfrOrdering,
      final Map<String, String> warningTypeToDescription) {
    super(overallOrdering, trfrOrdering, warningTypeToDescription);
  }

  public static CorefDocumentRenderer createWithDefaultOrdering(
      final ImmutableList<CorefWarningRule<Integer>> warnings) {
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

    // put all the CAS groups here, just for reference
//    sb.append(href("CASGroups"));
//    sb.append("<h2>CASGroups</h2>");
//    sb.append(closehref());
//    sb.append("<div id=\"CASGroups\" style=\"display:none\"");
//    for (final Integer CASGroup : answerKey.corefAnnotation().clusterIDToMembersMap().keySet()) {
////      sb.append(href(String.format("CASGroup_%d", CASGroup)));
//      sb.append("<h3>CASGroup-").append(CASGroup).append("</h3>");
////      sb.append(closehref());
//      sb.append("<div id=\"CASGroup_");
//      sb.append(CASGroup);
//      sb.append("\" style=\"display:inherit\" >");
//      appendCASStringList(sb, answerKey.corefAnnotation().clusterIDToMembersMap().get(CASGroup),
//          CASGroup);
//      sb.append("</div>");
//    }
//    sb.append("</div>");

    // begin bullets
    sb.append(href("CASGroupErrors"));
    sb.append("<h2>CAS Group Analysis</h2>");
    sb.append(closehref());
    sb.append("<div id=\"CASGroupErrors\" style=\"display:block\">");

    final ImmutableSetMultimap<TypeRole, Integer> typeRoleToCASGroup =
        ImmutableSetMultimap.copyOf(TypeRole.buildMappingFromAnswerKey(answerKey).inverse());
    for (final TypeRole typeRole : Ordering.<TypeRole>usingToString().sortedCopy(
        typeRoleToCASGroup.keySet())) {
      // append the type role
      final Set<Integer> offendingCASIntersections = Sets.intersection(
          typeRoleToCASGroup.get(typeRole), warnings.keySet());
      if (offendingCASIntersections.size() > 1) {
        sb.append(href(typeRole.toString()));
        sb.append("<h2>").append(typeRole.toString()).append("</h2>");
        sb.append(closehref());
        sb.append("<div id=\"").append(typeRole.toString()).append("\" style=\"display:block\">");
        for (final Integer CASGroup : offendingCASIntersections) {
          appendCASGroup(sb, CASGroup, answerKey, warnings.get(CASGroup));
        }
        sb.append("</div>");
      }
    }
//    for (final Integer CASGroup : warnings.keySet()) {
//      appendCASGroup(sb, CASGroup, answerKey, warnings);
//    }
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
      final ImmutableCollection<KBPString> kbpStrings, final Integer CASGroup) {
//    sb.append(href(String.format("CASStringList_%d", CASGroup)));
//    sb.append("CAS String List\n");
//    sb.append(closehref());

    sb.append("<div id=\"CASStringList_").append(CASGroup).append("\" style=\"display:block\">");
//    sb.append("<ul>\n");
    final Joiner semicolon = Joiner.on("; ");
    sb.append(semicolon.join(orderStringByLength().sortedCopy(ImmutableSet.copyOf(
        Iterables.transform(kbpStrings, KBPString.Text)))));
//    for (final String kbpString : orderStringByLength().sortedCopy(ImmutableSet.copyOf(
//        Iterables.transform(kbpStrings, KBPString.Text)))) {
//      sb.append("<li>");
//      sb.append(kbpString);
//      sb.append("</li>\n");
//    }
//    sb.append("</ul>\n");
    sb.append("</div>");
  }


  private static void appendTypeAndRoleSummaryForCAS(final StringBuilder sb,
      final Integer CASGroup, final AnswerKey answerKey) {
    final ImmutableMultimap<Symbol, Response> eventTypeToResponse =
        Multimaps.index(responsesForCASGroup(CASGroup, answerKey),
            Response.typeFunction());
    final ImmutableSetMultimap<Symbol, Symbol> eventTypeToRoles = ImmutableSetMultimap.copyOf(
        Multimaps.transformValues(eventTypeToResponse, Response.roleFunction()));
    final Joiner comma = Joiner.on(", ");

    sb.append(href(String.format("SummaryEventTypeRole_%d", CASGroup)));
    sb.append("Summary of event type, role for this CAS\n");
    sb.append(closehref());
    sb.append("<div id=\"SummaryEventTypeRole_").append(CASGroup)
        .append("\" style=\"display:none\">");
    sb.append("<ul>\n");
    for (final Symbol type : eventTypeToRoles.keySet()) {
      sb.append("<li>");
      sb.append(type);
      sb.append(": ");
      sb.append(comma.join(eventTypeToRoles.get(type)));
      sb.append("</li>");
    }
    sb.append("</ul>");
    sb.append("</div>");
  }


  private static void appendWarningsListForCAS(final StringBuilder sb,
      final ImmutableCollection<Warning> warnings, final Integer CASGroup) {
    final ImmutableSetMultimap<String, Warning> warningByType = ImmutableSetMultimap.copyOf(
        Multimaps.index(warnings,
            new Function<Warning, String>() {
              @Override
              public String apply(final Warning input) {
                return input.typeString();
              }
            }));
    sb.append(href(String.format("Warnings_%d", CASGroup)));
    sb.append("Warning list\n");
    sb.append(closehref());

    sb.append("<div id=\"Warnings_").append(CASGroup).append("\" style=\"display:none\">");
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
    sb.append("</div>");

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
      final AnswerKey answerKey, final ImmutableCollection<Warning> warnings) {
    final CorefAnnotation coref = answerKey.corefAnnotation();
    sb.append(href(String.format("CASGroupError_%d", CASGroup)));
    sb.append("<b>CASGroup-").append(CASGroup).append("</b>");
    sb.append(closehref());
    sb.append("<div id=\"CASGroupError_").append(CASGroup).append("\" style=\"display:inherit\" >");
    sb.append("<ul>");

    //cas string list
    sb.append("<li>");
    appendCASStringList(sb, coref.clusterIDToMembersMap().get(CASGroup), CASGroup);
    sb.append("</li>");

    // summary of type and roles

//    sb.append("<li>");
//    appendTypeAndRoleSummaryForCAS(sb, CASGroup, answerKey);
//    sb.append("</li>");

    // warnings list
//    sb.append("<li>");
//    appendWarningsListForCAS(sb, warnings, CASGroup);
//    sb.append("</li>");

//    // CAS with role list
//    sb.append("<li>");
//    appendCASRoleList(sb, CASGroup, answerKey);
//    sb.append("</li>");

    sb.append("</ul>");
    sb.append("</div>");
  }

  private static final Ordering<String> orderStringByLength() {
    return new Ordering<String>() {
      @Override
      public int compare(final String left, final String right) {
        return left.length() - right.length();
      }
    };
  }

  private final static class TypeRole {

    final Symbol type;
    final Symbol role;

    private TypeRole(final Symbol type, final Symbol role) {
      this.type = type;
      this.role = role;
    }

    public static ImmutableMultimap<Integer, TypeRole> buildMappingFromAnswerKey(
        final AnswerKey answerKey) {
      final ImmutableMultimap.Builder<Integer, Response> responsesForCASGroupBuilder =
          ImmutableMultimap.builder();
      final ImmutableSetMultimap<KBPString, Response> kbpStringToResponse =
          ImmutableSetMultimap.copyOf(
              Multimaps.index(answerKey.allResponses(), Response.CASFunction()));
      for (final Integer CASGroup : answerKey.corefAnnotation().clusterIDToMembersMap().keySet()) {
        for (final KBPString kbpString : answerKey.corefAnnotation().clusterIDToMembersMap()
            .get(CASGroup)) {
          responsesForCASGroupBuilder.putAll(CASGroup, kbpStringToResponse.get(kbpString));
        }
      }

      final ImmutableMultimap<Integer, Response> CASGroupToResponse =
          responsesForCASGroupBuilder.build();
      final ImmutableMultimap<Integer, TypeRole> CASGroupToTypeRole = ImmutableMultimap.copyOf(
          Multimaps.transformValues(CASGroupToResponse, responseToTypeRole()));
      return CASGroupToTypeRole;
    }

    public static Function<Response, TypeRole> responseToTypeRole() {
      return new Function<Response, TypeRole>() {
        @Override
        public TypeRole apply(final Response input) {
          return new TypeRole(input.type(), input.role());
        }
      };
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final TypeRole typeRole = (TypeRole) o;

      if (!type.equals(typeRole.type)) {
        return false;
      }
      return role.equals(typeRole.role);

    }

    @Override
    public int hashCode() {
      int result = type.hashCode();
      result = 31 * result + role.hashCode();
      return result;
    }

    @Override
    public String toString() {
      return type + "/" + role;
    }
  }
}

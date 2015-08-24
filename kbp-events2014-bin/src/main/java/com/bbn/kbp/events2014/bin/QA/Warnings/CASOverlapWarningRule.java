package com.bbn.kbp.events2014.bin.QA.Warnings;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.CorefAnnotation;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.KBPString;
import com.bbn.kbp.events2014.Response;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Created by jdeyoung on 8/20/15.
 */
public class CASOverlapWarningRule implements CorefWarningRule<Integer> {

  private static final Logger log = LoggerFactory.getLogger(CASOverlapWarningRule.class);

  private CASOverlapWarningRule() {

  }

  public static CASOverlapWarningRule create() {
    return new CASOverlapWarningRule();
  }

  @Override
  public SetMultimap<Integer, Warning> applyWarning(final AnswerKey answerKey) {
    return applyWarning(answerKey, answerKey.allResponses());
  }

  @Override
  public SetMultimap<Integer, Warning> applyWarning(final AnswerKey answerKey,
      final Set<Response> restrictWarningTo) {
    final ImmutableSetMultimap.Builder<Integer, Warning> result = ImmutableSetMultimap.builder();
    final ImmutableMultimap.Builder<Integer, Integer> casOverlapsWith = ImmutableMultimap.builder();
    final CorefAnnotation corefAnnotation = answerKey.corefAnnotation();
    final ImmutableSet<Integer> CASGroupsOfConcern = CASGroupsForResponses(corefAnnotation, restrictWarningTo);

    // gather all sets that overlap
    for (final Integer setA : CASGroupsOfConcern) {
      for (final Integer setB : CASGroupsOfConcern) {
        if (setA.equals(setB)) {
          continue;
        }
        if (overlapsForPuposesofIncompleteCoref(corefAnnotation, setA, setB)) {
          casOverlapsWith.put(setA, setB);
        }
      }
    }

    // Warnings! Assemble!
    final ImmutableMultimap<Integer, Integer> casToOverlaps = casOverlapsWith.build();
//    final Joiner comma = Joiner.on(", ");
    for (final Integer key : casToOverlaps.keySet()) {
      for (final Integer overlappingCluster : casToOverlaps.get(key)) {
        final StringBuilder warningText = new StringBuilder();
        // this is a hack to allow jumping in the browser to other CASGroups with which this might overlap, if any
//        warningText.append("<a href=\"#CASGroup_").append(overlappingCluster).append("\" >");
//        warningText.append("CASGroup-").append(overlappingCluster);
//        warningText.append("</a>");
        // end hack
        warningText.append("Overlaps with <b>CASGroup-").append(overlappingCluster).append("</b>");
//        warningText.append(" - ");
//        warningText.append(comma.join(
//            ImmutableSet.copyOf(
//                Iterables.transform(
//                    corefAnnotation.clusterIDToMembersMap().get(overlappingCluster),
//                    KBPString.Text))));
//        warningText.append("\n<ul>\n");
//        for (final TypeRoleRealis r : ImmutableSet.copyOf(
//            Ordering.<TypeRoleRealis>usingToString().sortedCopy(
//                Iterables.transform(CorefDocumentRenderer.responsesForCASGroup(overlappingCluster,
//                    answerKey), TypeRoleRealis.responseToTRR())))) {
//          warningText.append("<li>");
//          warningText.append(r.toString());
//          warningText.append("</li>\n");
//        }
//        warningText.append("</ul>\n");
        final Warning w =
            Warning.create(getTypeString(), warningText.toString(), Warning.Severity.MINOR);
        result.put(key, w);
      }
    }

    return result.build();
  }

  private static ImmutableSet<Integer> CASGroupsForResponses(final CorefAnnotation corefAnnotation, final Iterable<Response> responses) {
    final ImmutableSet.Builder<Integer> CASGroups = ImmutableSet.builder();
    for(final KBPString kbpString: Iterables.transform(responses, Response.CASFunction())) {
      final Optional<Integer> CASGroup = corefAnnotation.corefId(kbpString);
      if(CASGroup.isPresent()) {
        CASGroups.add(CASGroup.get());
      }
    }
    return CASGroups.build();
  }

  private static boolean overlapsForPuposesofIncompleteCoref(final CorefAnnotation corefAnnotation,
      final Integer setA,
      final Integer setB) {
    if (setA.equals(setB)) {
      return false;
    }
    for (final KBPString kbpStringA : corefAnnotation.clusterIDToMembersMap().get(setA)) {
      for (final KBPString kbpStringB : corefAnnotation.clusterIDToMembersMap().get(setB)) {
        final String a = kbpStringA.string().toLowerCase();
        final String b = kbpStringB.string().toLowerCase();
        // no need to be symmetric about these concerns - each string will be looped over later by apply warning
        if (a.contains(b) || b.contains(a)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public String getTypeString() {
    return "Large Overlap Warning";
  }

  @Override
  public String getTypeDescription() {
    return "These CASes appear to have very large overlaps - perhaps they should be merged (ignoring TIME)";
  }

  private static class TypeRoleRealis {

    final Symbol type;
    final Symbol role;
    final KBPRealis realis;

    private TypeRoleRealis(final Symbol type, final Symbol role, final KBPRealis realis) {
      this.type = type;
      this.role = role;
      this.realis = realis;
    }

    public static Function<Response, TypeRoleRealis> responseToTRR() {
      return new Function<Response, TypeRoleRealis>() {
        @Override
        public TypeRoleRealis apply(final Response input) {
          return new TypeRoleRealis(input.type(), input.role(), input.realis());
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

      final TypeRoleRealis that = (TypeRoleRealis) o;

      if (!type.equals(that.type)) {
        return false;
      }
      if (!role.equals(that.role)) {
        return false;
      }
      return realis == that.realis;

    }

    @Override
    public int hashCode() {
      int result = type.hashCode();
      result = 31 * result + role.hashCode();
      result = 31 * result + realis.hashCode();
      return result;
    }

    @Override
    public String toString() {
      return type + "-" + role + ":" + realis;
    }
  }
}

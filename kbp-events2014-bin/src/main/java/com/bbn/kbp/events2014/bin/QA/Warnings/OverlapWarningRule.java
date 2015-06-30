package com.bbn.kbp.events2014.bin.QA.Warnings;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.bin.QA.AssessmentQA;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by jdeyoung on 6/4/15.
 */
public class OverlapWarningRule extends TRFRWarning {

  private static final Logger log = LoggerFactory.getLogger(OverlapWarningRule.class);

  protected OverlapWarningRule() {

  }

  protected boolean warningAppliesTo(TypeRoleFillerRealis fst, TypeRoleFillerRealis snd) {
    if (fst.equals(snd)) {
      return false;
    }
    // only warningApplies to things of the same type
    return fst.type().equals(snd.type()) && fst.role().equals(snd.role());
  }

  @Override
  public ImmutableSetMultimap<Response, Warning> applyWarning(
      final AnswerKey answerKey) {
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

  @Override
  public String getTypeString() {
    return "Significant Overlap";
  }

  @Override
  public String getTypeDescription() {
    return "We think that there's a significant overlap between two responses of the same type, maybe they should have been coreffed together";
  }


  protected Multimap<Response, Warning> findOverlap(
      final TypeRoleFillerRealis fst, final Iterable<Response> first,
      final TypeRoleFillerRealis snd, final Iterable<Response> second) {
    final ImmutableMultimap.Builder<Response, Warning> result =
        ImmutableMultimap.builder();
    for (Response a : first) {
      for (Response b : second) {
        if (a == b || b.canonicalArgument().string().trim().isEmpty() || a.role() != b.role()
            || a.type() != b.type()) {
          continue;
        }
        // check for complete containment
        if (a.canonicalArgument().string().toLowerCase().contains(
            b.canonicalArgument().string().toLowerCase())) {
          log.info("adding \"{}\" from {} by complete string containment in \"{}\" from {}",
              b.canonicalArgument().string(), snd, a.canonicalArgument().string(), fst);
          //result.put(a, warning());
          result.put(b, Warning.create(getTypeString(),
              String.format("Contained in <br/>\"%s\"", AssessmentQA.readableTRFR(fst, first)), Warning.Severity.MINOR));
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

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


  protected Multimap<? extends Response, ? extends Warning> findOverlap(
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
        if (a.canonicalArgument().string().contains(b.canonicalArgument().string())) {
          log.info("adding \"{}\" from {} by complete string containment in \"{}\" from {}",
              b.canonicalArgument().string(), snd, a.canonicalArgument().string(), fst);
          //result.put(a, warning());
          result.put(b, Warning.create(
              String.format("Contained by \"%s\" in \"%s\"", a.canonicalArgument().string(),
                  AssessmentQA.readableTRFR(fst)), Warning.SEVERITY.MINIOR));
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

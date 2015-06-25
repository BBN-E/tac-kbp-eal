package com.bbn.kbp.events2014.transformers;

import com.bbn.bue.common.scoring.Scored;
import com.bbn.kbp.events2014.CorefAnnotation;
import com.bbn.kbp.events2014.KBPString;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.equalTo;
import static com.google.common.base.Predicates.not;

/**
 * Deduplicates a system output by keeping only a single representative for each (docid, type, role,
 * CAS, realis) tuple.  The selection criterion is the same as for the scorer - prefer the higher,
 * and if there is a tie, prefer the 'larger' ID (which is computed from a stable hash code). If
 * CorefAnnotation is provided, only a single representative is kept for each (docid, type, role,
 * CAS-coref-cluster, realis) tuple.
 */
public final class KeepBestJustificationOnly {

  private static final Logger log = LoggerFactory.getLogger(KeepBestJustificationOnly.class);

  public static ResponseMapping computeResponseMapping(SystemOutput input) {
    return computeResponseMapping(input, Functions.<KBPString>identity());
  }

  public static ResponseMapping computeResponseMappingUsingProvidedCoref(SystemOutput input,
      CorefAnnotation corefAnnotation) {
    return computeResponseMapping(input, corefAnnotation.strictCASNormalizerFunction());
  }

  public static Function<SystemOutput, SystemOutput> asFunctionOnSystemOutput() {
    return new Function<SystemOutput, SystemOutput>() {
      @Override
      public SystemOutput apply(final SystemOutput input) {
        return computeResponseMapping(input).apply(input);
      }
    };
  }
  public static Function<SystemOutput, SystemOutput> asFunctionUsingCoref(final CorefAnnotation corefAnnotation) {
    return new Function<SystemOutput, SystemOutput>() {
      @Override
      public SystemOutput apply(final SystemOutput input) {
        return computeResponseMappingUsingProvidedCoref(input, corefAnnotation).apply(input);
      }
    };
  }


  private static ResponseMapping computeResponseMapping(SystemOutput input, Function<KBPString, KBPString> CASNormalizer) {
    checkNotNull(input);

    // group response by TypeRoleFillerRealis tuples
    final Multimap<TypeRoleFillerRealis, Response> groupedResponses =
        Multimaps.index(input.responses(),
            TypeRoleFillerRealis.extractFromSystemResponse(CASNormalizer));

    final ImmutableSet.Builder<Response> toDeleteB = ImmutableSet.builder();

    for (final Map.Entry<TypeRoleFillerRealis, Collection<Response>> group : groupedResponses
        .asMap().entrySet()) {
      // we know by construction known of those groups is empty, so the .get() is safe
      final Collection<Response> competitors = group.getValue();
      final Scored<Response> selected = input.score(input.selectFromMultipleSystemResponses(
          competitors).get());
      toDeleteB.addAll(Iterables.filter(competitors, not(equalTo(selected.item()))));
      if (competitors.size() > 1) {
        log.info("For equivalence class {}, got {} responses: {} and selected {}", group.getKey(),
            competitors.size(), competitors, selected);
      }
    }

    final ImmutableSet<Response> toDelete = toDeleteB.build();

    log.info(
        "For document {}, after keeping only selected justifications, deleted {} of {} responses",
        input.docId(), toDelete.size(), input.size());

    return ResponseMapping.create(ImmutableMap.<Response,Response>of(), toDelete);
  }
}

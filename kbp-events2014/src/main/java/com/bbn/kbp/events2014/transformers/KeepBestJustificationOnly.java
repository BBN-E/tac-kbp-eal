package com.bbn.kbp.events2014.transformers;

import com.bbn.bue.common.scoring.Scored;
import com.bbn.kbp.events2014.CorefAnnotation;
import com.bbn.kbp.events2014.KBPString;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;
import com.bbn.kbp.events2014.SystemOutput;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.concat;

/**
 * Deduplicates a system output by keeping only a single representative for each (docid, type, role,
 * CAS, realis) tuple.  The selection criterion is the same as for the scorer - prefer the higher,
 * and if there is a tie, prefer the 'larger' ID (which is computed from a stable hash code). If
 * CorefAnnotation is provided, only a single representative is kept for each (docid, type, role,
 * CAS-coref-cluster, realis) tuple.
 */
public final class KeepBestJustificationOnly implements Function<SystemOutput, SystemOutput> {

  private static final Logger log = LoggerFactory.getLogger(KeepBestJustificationOnly.class);

  private final Function<KBPString, KBPString> CASNormalizer;

  @Override
  public SystemOutput apply(SystemOutput input) {
    checkNotNull(input);

    /*
    // group response by TypeRoleFillerRealis tuples
    final Multimap<TypeRoleFillerRealis, Response> groupedResponses =
        Multimaps.index(input.responses(),
            TypeRoleFillerRealis.extractFromSystemResponse(CASNormalizer));

    final ImmutableSet.Builder<Scored<Response>> filteredResults = ImmutableSet.builder();

    for (final Map.Entry<TypeRoleFillerRealis, Collection<Response>> group : groupedResponses
        .asMap().entrySet()) {
      // we know by construction known of those groups is empty, so the .get() is safe
      final Scored<Response> selected = input.score(input.selectFromMultipleSystemResponses(
          group.getValue()).get());
      if (group.getValue().size() > 1) {
        log.info("For equivalence class {}, got {} responses: {} and selected {}", group.getKey(),
            group.getValue().size(), group.getValue(), selected);
      }
      filteredResults.add(selected);
    }

    final ImmutableSet<Scored<Response>> filteredResponses = filteredResults.build();
    */
    
    final ImmutableSet<Scored<Response>> filteredResponses = filterResponses(input.responses(), input);
    
    log.info(
        "For document {}, after keeping only selected justifications, went from {} to {} responses",
        input.docId(), input.size(), filteredResponses.size());

    return SystemOutput.from(input.docId(), filteredResponses, input.allMetadata());
  }

  public SystemOutput apply(final SystemOutput input, final ResponseLinking linking) {
    checkNotNull(input);
    checkNotNull(linking);
    
    final ImmutableSet.Builder<Scored<Response>> filteredResults = ImmutableSet.builder();
    
    for(final ResponseSet responseSet : linking.responseSets()) {
      final ImmutableSet<Scored<Response>> responses = filterResponses(responseSet.asSet(), input);
      filteredResults.addAll(responses);
    }
    
    // ResponseLinking won't have Responses with Realis=Generic. We need to include them here.
    final ImmutableSet<Response> responsesInLinking = ImmutableSet.copyOf(concat(linking.responseSets()));
    final ImmutableSet<Response> responsesNotInLinking = ImmutableSet.copyOf(Sets.difference(input.responses(), responsesInLinking));
    filteredResults.addAll(filterResponses(responsesNotInLinking, input));
    
    final ImmutableSet<Scored<Response>> filteredResponses = filteredResults.build();
    
    log.info(
        "For document {}, responsesInLinking={}, responsesNotInLinking={}", 
        input.docId(), responsesInLinking.size(), responsesNotInLinking.size());
    
    log.info(
        "For document {}, after keeping only selected justifications, went from {} to {} responses",
        input.docId(), input.size(), filteredResponses.size());

    return SystemOutput.from(input.docId(), filteredResponses, input.allMetadata());
  }
  
  
  private ImmutableSet<Scored<Response>> filterResponses(final ImmutableSet<Response> responses, final SystemOutput input) {
    final ImmutableSet.Builder<Scored<Response>> ret = ImmutableSet.builder();
    
    // group response by TypeRoleFillerRealis tuples
    final Multimap<TypeRoleFillerRealis, Response> groupedResponses =
        Multimaps.index(responses,
            TypeRoleFillerRealis.extractFromSystemResponse(CASNormalizer));
    
    for (final Map.Entry<TypeRoleFillerRealis, Collection<Response>> group : groupedResponses.asMap().entrySet()) {
      // we know by construction known of those groups is empty, so the .get() is safe
      final Scored<Response> selected = input.score(input.selectFromMultipleSystemResponses(group.getValue()).get());
      
      if (group.getValue().size() > 1) {
        log.info("For equivalence class {}, got {} responses: {} and selected {}", group.getKey(), 
            group.getValue().size(), group.getValue(), selected);
      }
      
      ret.add(selected);
    }
    
    return ret.build();
  }
  
  
  private KeepBestJustificationOnly(Function<KBPString, KBPString> CASNormalizer) {
    this.CASNormalizer = checkNotNull(CASNormalizer);
  }

  // this 'normalizer' is just a dummy which does no normalization
  private static final Function<KBPString, KBPString> identityNormalizer =
      Functions.identity();

  public static KeepBestJustificationOnly create() {
    return new KeepBestJustificationOnly(identityNormalizer);
  }

  public static KeepBestJustificationOnly createForCorefAnnotation(
      CorefAnnotation corefAnnotation) {
    return new KeepBestJustificationOnly(corefAnnotation.strictCASNormalizerFunction());
  }
}

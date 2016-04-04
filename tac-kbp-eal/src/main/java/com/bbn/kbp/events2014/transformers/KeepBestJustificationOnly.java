package com.bbn.kbp.events2014.transformers;

import com.bbn.bue.common.scoring.Scored;
import com.bbn.kbp.events2014.CorefAnnotation;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.KBPString;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseFunctions;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;
import com.bbn.kbp.events2014.DocumentSystemOutput;
import com.bbn.kbp.events2014.DocumentSystemOutput2015;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.compose;
import static com.google.common.base.Predicates.equalTo;

/**
 * Deduplicates a system output by keeping only a single representative for each (docid, type, role,
 * CAS, realis) tuple.  The selection criterion is the same as for the scorer - prefer the higher,
 * and if there is a tie, prefer the 'larger' ID (which is computed from a stable hash code). If
 * CorefAnnotation is provided, only a single representative is kept for each (docid, type, role,
 * CAS-coref-cluster, realis) tuple.  If a linking is provided, we keep the best representative of
 * each TRFR from each response set.
 */
public final class KeepBestJustificationOnly {

  private static final Logger log = LoggerFactory.getLogger(KeepBestJustificationOnly.class);

  public static ResponseMapping computeResponseMapping(DocumentSystemOutput input) {
    return computeResponseMapping(input, Functions.<KBPString>identity());
  }

  public static ResponseMapping computeResponseMappingUsingProvidedCoref(DocumentSystemOutput input,
      CorefAnnotation corefAnnotation) {
    return computeResponseMapping(input, corefAnnotation.strictCASNormalizerFunction());
  }

  public static Function<DocumentSystemOutput, DocumentSystemOutput> asFunctionOnSystemOutput() {
    return new Function<DocumentSystemOutput, DocumentSystemOutput>() {
      @Override
      public DocumentSystemOutput apply(final DocumentSystemOutput input) {
        return input.copyTransformedBy(computeResponseMapping(input));
      }
    };
  }

  public static Function<DocumentSystemOutput, DocumentSystemOutput> asFunctionUsingCoref(
      final CorefAnnotation corefAnnotation) {
    return new Function<DocumentSystemOutput, DocumentSystemOutput>() {
      @Override
      public DocumentSystemOutput apply(final DocumentSystemOutput input) {
        return input.copyTransformedBy(
            computeResponseMappingUsingProvidedCoref(input, corefAnnotation));
      }
    };
  }


  private static ResponseMapping computeResponseMapping(DocumentSystemOutput input,
      Function<KBPString, KBPString> CASNormalizer) {
    checkNotNull(input);
    final Function<Response, TypeRoleFillerRealis> trfrFunction =
        TypeRoleFillerRealis.extractFromSystemResponse(CASNormalizer);

    // if the input doesn't have response linking information,
    // we create a dummy linking with everything in one event frame
    final ResponseLinking responseLinking = getResponseLinking(input);

    final Set<Response> toKeep = Sets.newHashSet();
    for (final ResponseSet eventFrame : responseLinking.responseSets()) {
      final Multimap<TypeRoleFillerRealis, Response> groupedResponses =
          Multimaps.index(eventFrame.asSet(), trfrFunction);

      for (final Map.Entry<TypeRoleFillerRealis, Collection<Response>> group : groupedResponses
          .asMap().entrySet()) {
        // we know by construction none of those groups is empty, so the .get() is safe
        final Collection<Response> competitors = group.getValue();
        final Scored<Response> selected =
            input.arguments().score(input.arguments().selectFromMultipleSystemResponses(
                competitors).get());
        toKeep.add(selected.item());
      }
    }

    // ResponseLinking contain only Responses with Realis ACTUAL, OTHER. The above loop does keepBest for these.
    // We now do keepBest for Generic Responses
    final Predicate<Response> HasGenericRealis = compose(equalTo(KBPRealis.Generic),
        ResponseFunctions.realis());
    final ImmutableSet<Response> genericResponses = FluentIterable.from(input.arguments().responses()).filter(HasGenericRealis).toSet();
    final Multimap<TypeRoleFillerRealis, Response> groupedGenericResponses = Multimaps.index(genericResponses, trfrFunction);
    for (final Map.Entry<TypeRoleFillerRealis, Collection<Response>> group : groupedGenericResponses.asMap().entrySet()) {
      final Collection<Response> competitors = group.getValue();
      final Scored<Response> selected = input.arguments().score(input.arguments().selectFromMultipleSystemResponses(competitors).get());
      toKeep.add(selected.item());
    }

    final ImmutableSet<Response> toDelete =
        Sets.difference(input.arguments().responses(), toKeep).immutableCopy();
    log.info(
        "For document {}, after keeping only selected justifications, deleted {} of {} responses",
        input.docID(), toDelete.size(), input.arguments().size());

    return ResponseMapping.create(ImmutableMap.<Response,Response>of(), toDelete);
  }

  private static ResponseLinking getResponseLinking(final DocumentSystemOutput input) {
    if (input instanceof DocumentSystemOutput2015) {
      return ((DocumentSystemOutput2015) input).linking();
    } else {
      final ImmutableSet<ResponseSet> responseSets;
      if (!input.arguments().responses().isEmpty()) {
        responseSets = ImmutableSet.of(
            ResponseSet.from(input.arguments().responses()));
      } else {
        responseSets = ImmutableSet.of();
      }

      return ResponseLinking.builder().docID(input.docID()).responseSets(responseSets).build();
    }
  }
}

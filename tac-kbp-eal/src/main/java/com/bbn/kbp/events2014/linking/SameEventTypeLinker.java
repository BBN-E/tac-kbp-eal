package com.bbn.kbp.events2014.linking;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.ArgumentOutput;
import com.bbn.kbp.events2014.AssessedResponseFunctions;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseFunctions;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

import static com.google.common.base.Predicates.compose;
import static com.google.common.base.Predicates.in;

public final class SameEventTypeLinker extends AbstractLinkingStrategy implements LinkingStrategy {

  private static final Logger log = LoggerFactory.getLogger(SameEventTypeLinker.class);

  private final ImmutableSet<KBPRealis> realisesWhichMustBeAligned;

  private SameEventTypeLinker(final Iterable<KBPRealis> realisesWhichMustBeAligned) {
    this.realisesWhichMustBeAligned = ImmutableSet.copyOf(realisesWhichMustBeAligned);
  }

  public static SameEventTypeLinker create(final Iterable<KBPRealis> realises) {
    return new SameEventTypeLinker(realises);
  }

  @Override
  public ResponseLinking linkResponses(final ArgumentOutput argumentOutput) {
    return linkResponses(argumentOutput.docId(), argumentOutput.responses());
  }

  public ResponseLinking linkResponses(AnswerKey key) {
    return linkResponses(key.docId(), Iterables.transform(key.annotatedResponses(),
        AssessedResponseFunctions.response()));
  }

  private ResponseLinking linkResponses(final Symbol docId,
      final Iterable<Response> responses) {
    final Predicate<Response> HasRelevantRealis =
        compose(in(realisesWhichMustBeAligned), ResponseFunctions.realis());
    final ImmutableSet<Response> systemResponsesAlignedRealis =
        FluentIterable.from(responses).filter(HasRelevantRealis).toSet();

    final Multimap<Symbol, Response> responsesByEventType =
        Multimaps.index(systemResponsesAlignedRealis, ResponseFunctions.type());

    final ImmutableSet.Builder<ResponseSet> ret = ImmutableSet.builder();

    for (final Collection<Response> responseSet : responsesByEventType.asMap().values()) {
      ret.add(ResponseSet.from(responseSet));
    }

    return ResponseLinking.builder().docID(docId).addAllResponseSets(ret.build()).build();
  }
}

package com.bbn.kbp.events2014.linking;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseLinking;
import com.bbn.kbp.events2014.ResponseSet;
import com.bbn.kbp.events2014.SystemOutput;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

import static com.google.common.base.Predicates.compose;
import static com.google.common.base.Predicates.in;

public final class SameEventTypeLinker implements LinkingStrategy {

  private static final Logger log = LoggerFactory.getLogger(SameEventTypeLinker.class);

  private final ImmutableSet<KBPRealis> realisesWhichMustBeAligned;

  private SameEventTypeLinker(final Iterable<KBPRealis> realisesWhichMustBeAligned) {
    this.realisesWhichMustBeAligned = ImmutableSet.copyOf(realisesWhichMustBeAligned);
  }

  public static SameEventTypeLinker create(final Iterable<KBPRealis> realises) {
    return new SameEventTypeLinker(realises);
  }

  @Override
  public ResponseLinking linkResponses(final SystemOutput systemOutput) {
    final Symbol docId = systemOutput.docId();

    final Predicate<Response> HasRelevantRealis =
        compose(in(realisesWhichMustBeAligned), Response.realisFunction());
    final ImmutableSet<Response> systemResponsesAlignedRealis =
        FluentIterable.from(systemOutput.responses()).filter(HasRelevantRealis).toSet();

    final Multimap<Symbol, Response> responsesByEventType =
        Multimaps.index(systemResponsesAlignedRealis, Response.typeFunction());

    final ImmutableSet.Builder<ResponseSet> ret = ImmutableSet.builder();

    for (final Collection<Response> responseSet : responsesByEventType.asMap().values()) {
      ret.add(ResponseSet.from(responseSet));
    }

    return ResponseLinking.from(docId, ret.build(), ImmutableSet.<Response>of());
  }
}

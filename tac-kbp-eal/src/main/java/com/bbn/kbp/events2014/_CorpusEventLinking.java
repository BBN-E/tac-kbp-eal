package com.bbn.kbp.events2014;

import com.bbn.bue.common.TextGroupPublicImmutable;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;

import org.immutables.func.Functional;
import org.immutables.value.Value;

/**
 * The collection of all cross-document events found in a corpus.
 */
@Value.Immutable
@TextGroupPublicImmutable
@Functional
abstract class _CorpusEventLinking {

  @Value.Parameter
  public abstract ImmutableSet<CorpusEventFrame> corpusEventFrames();

  @Value.Derived
  public ImmutableMultimap<DocEventFrameReference, CorpusEventFrame> docEventsToCorpusEvents() {
    final ImmutableMultimap.Builder<DocEventFrameReference, CorpusEventFrame> ret =
        ImmutableMultimap.builder();

    for (final CorpusEventFrame corpusEventFrame : corpusEventFrames()) {
      for (final DocEventFrameReference docEventFrameReference : corpusEventFrame
          .docEventFrames()) {
        ret.put(docEventFrameReference, corpusEventFrame);
      }
    }

    return ret.build();
  }
}

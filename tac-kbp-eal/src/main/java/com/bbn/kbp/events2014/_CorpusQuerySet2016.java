package com.bbn.kbp.events2014;

import com.bbn.bue.common.TextGroupPublicImmutable;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import java.util.Iterator;

import static com.bbn.kbp.events2014.CorpusQuery2016Functions.id;
import static com.google.common.base.Preconditions.checkArgument;

/**
 * A collection of {@link CorpusQuery2016}. All IDs are guaranteed to be unique.
 */
// old code, we don't care if it uses deprecated stuff
@SuppressWarnings("deprecation")
@TextGroupPublicImmutable
@Value.Immutable
@Functional
abstract class _CorpusQuerySet2016 implements Iterable<CorpusQuery2016> {

  @Value.Parameter
  public abstract ImmutableSet<CorpusQuery2016> queries();

  @Override
  public final Iterator<CorpusQuery2016> iterator() {
    return queries().iterator();
  }

  @Value.Check
  protected void check() {
    final ImmutableSet<Symbol> allIds = FluentIterable.from(queries()).transform(id()).toSet();
    checkArgument(allIds.size() == queries().size(), "All query IDs in a query set must be unique");
  }
}

package com.bbn.kbp.events;

import com.bbn.bue.common.symbols.Symbol;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import java.util.AbstractSet;
import java.util.Iterator;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

// old code, we don't care if it uses deprecated stuff
@SuppressWarnings("deprecation")
@com.bbn.bue.common.TextGroupPackageImmutable
@Functional
@Value.Immutable
public abstract class _ScoringEventFrame extends AbstractSet<DocLevelEventArg> {
  @Value.Derived
  public Symbol eventType() {
    // not arguments will never be empty by check()
    return checkNotNull(Iterables.getFirst(arguments(), null)).eventType();
  }

  @Value.Parameter
  public abstract ImmutableSet<DocLevelEventArg> arguments();

  @Override
  public final Iterator<DocLevelEventArg> iterator() {
    return arguments().iterator();
  }

  @Override
  public int size() {
    return arguments().size();
  }

  @Value.Check
  protected void check() {
    checkArgument(!arguments().isEmpty(), "Cannot create an event frame with no arguments");
  }
}


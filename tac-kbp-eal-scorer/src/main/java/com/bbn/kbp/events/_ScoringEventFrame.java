package com.bbn.kbp.events;

import com.bbn.bue.common.TextGroupPackageImmutable;

import com.google.common.collect.ImmutableSet;

import org.immutables.func.Functional;
import org.immutables.value.Value;

import java.util.AbstractSet;
import java.util.Iterator;

import static com.google.common.base.Preconditions.checkArgument;

@TextGroupPackageImmutable
@Functional
@Value.Immutable
public abstract class _ScoringEventFrame extends AbstractSet<DocLevelEventArg> {

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


package com.bbn.kbp.events2014.scorer.observers;

import com.bbn.bue.common.annotations.MoveToBUECommon;

import com.google.common.annotations.Beta;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Random;

import static com.google.common.base.Preconditions.checkNotNull;

@Beta
@MoveToBUECommon
public final class BootstrapIterator<ItemType> extends AbstractIterator<List<ItemType>> {

  private final Random rng;
  private final ImmutableList<ItemType> data;

  private BootstrapIterator(Iterable<ItemType> data, Random rng) {
    this.data = ImmutableList.copyOf(data);
    this.rng = checkNotNull(rng);
  }

  public static <ItemType> BootstrapIterator<ItemType> forData(Iterable<ItemType> data,
      Random rng) {
    return new BootstrapIterator<ItemType>(data, rng);
  }

  @Override
  protected List<ItemType> computeNext() {
    final ImmutableList.Builder<ItemType> ret = ImmutableList.builder();

    if (data.isEmpty()) {
      return ImmutableList.of();
    }

    for (int i = 0; i < data.size(); ++i) {
      ret.add(data.get(rng.nextInt(Integer.MAX_VALUE) % data.size()));
    }

    return ret.build();
  }
}

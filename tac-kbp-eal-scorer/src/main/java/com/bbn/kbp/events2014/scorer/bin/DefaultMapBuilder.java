package com.bbn.kbp.events2014.scorer.bin;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

// move to bue common
class DefaultMapBuilder<Key, Value> {

  private final Map<Key, Value> innerMap = Maps.newHashMap();
  private final Set<Key> seeKeys = Sets.newHashSet();
  private final DefaultValueSupplier<Key, Value> supplier;

  DefaultMapBuilder(DefaultValueSupplier<Key, Value> supplier) {
    this.supplier = checkNotNull(supplier);
  }

  public static <Key, Value> DefaultMapBuilder<Key, Value>
  fromSupplier(DefaultValueSupplier<Key, Value> defaultValueSupplier) {
    return new DefaultMapBuilder<Key, Value>(defaultValueSupplier);
  }

  public void put(Key key, Value value) {
    checkNotNull(value);
    seeKeys.add(key);
    innerMap.put(key, value);
  }

  public Value getOrDefault(Key key) {
    if (!seeKeys.contains(key)) {
      final Value val = supplier.initialValueFor(key);
      put(key, val);
      return val;
    } else {
      return innerMap.get(key);
    }
  }

  public Map<Key, Value> asMap() {
    return Collections.unmodifiableMap(innerMap);
  }

  interface DefaultValueSupplier<Key, Value> {

    public Value initialValueFor(Key k);
  }
}

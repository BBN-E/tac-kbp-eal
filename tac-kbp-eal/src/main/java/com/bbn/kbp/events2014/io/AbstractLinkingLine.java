package com.bbn.kbp.events2014.io;

import com.bbn.kbp.events2014.ResponseSet;

import com.google.common.base.Optional;

import org.immutables.value.Value;

/**
 * Created by rgabbard on 6/26/17.
 */
@Value.Immutable
abstract class AbstractLinkingLine {

  @Value.Parameter
  abstract ResponseSet responses();

  @Value.Parameter
  abstract Optional<String> id();
}

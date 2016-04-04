package com.bbn.nlp.events.ontology;

import com.bbn.bue.common.symbols.Symbol;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;

import javax.annotation.Nullable;

/**
 * Canonicalizes values in an existing event ontology according to a particular task definition.
 */
public interface SimpleEventOntologyMapper {

  Optional<Symbol> eventType(@Nullable Symbol eventType);

  ImmutableSet<Symbol> eventTypes();

  Optional<Symbol> eventSubtype(@Nullable Symbol eventSubtype);

  ImmutableSet<Symbol> eventSubtypes();

  Optional<Symbol> eventRole(@Nullable Symbol eventRole);

  ImmutableSet<Symbol> eventRoles();

  Optional<Symbol> entityType(@Nullable Symbol entityType);

  ImmutableSet<Symbol> entityTypes();
}

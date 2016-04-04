package com.bbn.nlp.events.ontology;


import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.symbols.Symbol;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;

import java.io.IOException;
import java.util.Map;

import javax.annotation.Nullable;

public final class EREToKBPEventOntologyMapper implements SimpleEventOntologyMapper {

  private final ImmutableMap<Symbol, Symbol> eventTypes;
  private final ImmutableMap<Symbol, Symbol> eventSubtypes;
  private final ImmutableMap<Symbol, Symbol> roleTypeMapper;
  private final ImmutableMap<Symbol, Symbol> entityTypeMapper;

  private EREToKBPEventOntologyMapper(
      final Map<Symbol, Symbol> eventTypes, final Map<Symbol, Symbol> eventSubtypes,
      final Map<Symbol, Symbol> roleTypeMapper, final Map<Symbol, Symbol> entityTypeMapper) {
    this.entityTypeMapper = ImmutableMap.copyOf(entityTypeMapper);
    this.eventSubtypes = ImmutableMap.copyOf(eventSubtypes);
    this.eventTypes = ImmutableMap.copyOf(eventTypes);
    this.roleTypeMapper = ImmutableMap.copyOf(roleTypeMapper);
  }

  public static EREToKBPEventOntologyMapper create(
      final Map<Symbol, Symbol> eventTypes,
      final Map<Symbol, Symbol> eventSubtypes,
      final Map<Symbol, Symbol> roleTypeMapper,
      final Map<Symbol, Symbol> entityTypeMapper) {
    return new EREToKBPEventOntologyMapper(eventTypes, eventSubtypes, roleTypeMapper,
        entityTypeMapper);

  }

  public static EREToKBPEventOntologyMapper create2016Mapping() throws IOException {
    final ImmutableMap<Symbol, Symbol> eventTypes =
        FileUtils.loadSymbolMap(resourceAsCharsource("2016.event.type.txt"));
    final ImmutableMap<Symbol, Symbol> eventSubtypes =
        FileUtils.loadSymbolMap(resourceAsCharsource("2016.event.subtype.txt"));
    final ImmutableMap<Symbol, Symbol> eventRoles =
        FileUtils.loadSymbolMap(resourceAsCharsource("2016.event.role.type.txt"));
    final ImmutableMap<Symbol, Symbol> entityType =
        FileUtils.loadSymbolMap(resourceAsCharsource("2016.entity.type.txt"));
    return create(eventTypes, eventSubtypes, eventRoles, entityType);
  }

  public static EREToKBPEventOntologyMapper create2015Mapping() throws IOException {
    final ImmutableMap<Symbol, Symbol> eventTypes =
        FileUtils.loadSymbolMap(resourceAsCharsource("2015.event.type.txt"));
    final ImmutableMap<Symbol, Symbol> eventSubtypes =
        FileUtils.loadSymbolMap(resourceAsCharsource("2015.event.subtype.txt"));
    final ImmutableMap<Symbol, Symbol> eventRoles =
        FileUtils.loadSymbolMap(resourceAsCharsource("2015.role.txt"));
    final ImmutableMap<Symbol, Symbol> entityType =
        FileUtils.loadSymbolMap(resourceAsCharsource("2015.entity.type.txt"));
    return create(eventTypes, eventSubtypes, eventRoles, entityType);
  }


  private static CharSource resourceAsCharsource(final String resource) throws IOException {
    return Resources
        .asCharSource(EREToKBPEventOntologyMapper.class.getResource(resource), Charsets.UTF_8);
  }

  @Override
  public Optional<Symbol> eventType(@Nullable final Symbol eventType) {
    return Optional.fromNullable(eventTypes.get(eventType));
  }

  @Override
  public ImmutableSet<Symbol> eventTypes() {
    return ImmutableSet.copyOf(eventTypes.values());
  }

  @Override
  public Optional<Symbol> eventSubtype(@Nullable final Symbol eventSubtype) {
    return Optional.fromNullable(eventSubtypes.get(eventSubtype));
  }

  @Override
  public ImmutableSet<Symbol> eventSubtypes() {
    return ImmutableSet.copyOf(eventSubtypes.values());
  }

  @Override
  public Optional<Symbol> eventRole(@Nullable final Symbol eventRole) {
    return Optional.fromNullable(roleTypeMapper.get(eventRole));
  }

  @Override
  public ImmutableSet<Symbol> eventRoles() {
    return ImmutableSet.copyOf(roleTypeMapper.values());
  }

  @Override
  public Optional<Symbol> entityType(@Nullable final Symbol entityType) {
    return Optional.fromNullable(entityTypeMapper.get(entityType));
  }

  @Override
  public ImmutableSet<Symbol> entityTypes() {
    return ImmutableSet.copyOf(roleTypeMapper.values());
  }
}

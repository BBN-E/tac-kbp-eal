package com.bbn.kbp.events2014.bin.QA.Warnings;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.TypeRoleFillerRealis;
import com.bbn.kbp.events2014.bin.QA.AssessmentQA;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by jdeyoung on 6/4/15.
 */
public class ConflictingTypeWarningRule extends OverlapWarningRule {

  private static final Logger log = LoggerFactory.getLogger(ConflictingTypeWarningRule.class);

  private final ImmutableTable<Symbol, Symbol, Set<Symbol>> argTypeRoleType;

  private ConflictingTypeWarningRule(final Table<Symbol, Symbol, Set<Symbol>> argTypeRoleType) {
    super();
    this.argTypeRoleType = ImmutableTable.copyOf(argTypeRoleType);
  }


  @Override
  protected boolean warningAppliesTo(TypeRoleFillerRealis fst, TypeRoleFillerRealis snd) {
    if (fst == snd || fst.type().equals(snd.type())) {
      return false;
    }
    return true;
  }

  @Override
  protected Multimap<? extends Response, ? extends Warning> findOverlap(
      final TypeRoleFillerRealis fst, final Iterable<Response> first,
      final TypeRoleFillerRealis snd, final Iterable<Response> second) {
    final ImmutableMultimap.Builder<Response, Warning> result =
        ImmutableMultimap.builder();
    for (final Response a : first) {
      final Set<Symbol> atypes = argTypeRoleType.get(a.type(), a.role());
      for (final Response b : second) {
        if (a == b || b.canonicalArgument().string().trim().isEmpty()) {
          continue;
        }
        if (a.canonicalArgument().equals(b.canonicalArgument())) {
          final Set<Symbol> btypes = Sets.newHashSet(argTypeRoleType.get(b.type(), b.role()));
          btypes.retainAll(atypes);
          if (btypes.size() == 0) {
            result.put(b, Warning.create(String
                    .format(
                        "%s has same string as %s but mismatched types %s/%s and %s/%s in trfr %s",
                        a.canonicalArgument().string(),
                        b.canonicalArgument().string(), a.type().asString(), a.role().asString(),
                        b.type().asString(), b.role().asString(), AssessmentQA.readableTRFR(snd)),
                Warning.SEVERITY.MINIOR));
          }
        }

      }
    }
    return result.build();
  }

  public static ConflictingTypeWarningRule create(String argsFile, String rolesFile)
      throws IOException {
    Table<Symbol, Symbol, Set<Symbol>> argTypeRoleType = HashBasedTable.create();
    Multimap<Symbol, Symbol> roleToTypes = HashMultimap.create();
    for (String line : Files.readLines(new File(rolesFile), Charset.defaultCharset())) {
      String[] parts = line.split("\t");
      for (String type : parts[1].split(",\\s+")) {
        roleToTypes.put(Symbol.from(parts[0].trim()), Symbol.from(type.trim()));
      }
    }

    for (String line : Files.readLines(new File(argsFile), Charset.defaultCharset())) {
      String[] parts = line.trim().split("\t");
      Symbol type = Symbol.from(parts[0].trim());
      for (int i = 1; i < parts.length; i++) {
        Symbol roleKey = Symbol.from(parts[i].trim());
        Symbol role = Symbol.from(parts[i].trim().replaceAll("[^A-Za-z-.\\s]", ""));
        log.info("type: {}, roleKey {}, role {}", type, roleKey, role);
        if (argTypeRoleType.contains(type, role)) {
          Set<Symbol> types = new HashSet<Symbol>(argTypeRoleType.get(type, role));
          types.addAll(roleToTypes.get(roleKey));
          argTypeRoleType.put(type, role, ImmutableSet.copyOf(types));
        } else {
          argTypeRoleType.put(type, role, ImmutableSet.copyOf(roleToTypes.get(roleKey)));
        }
      }
    }

    for (Symbol r : roleToTypes.keySet()) {
      log.info("{} -> {}", r, roleToTypes.get(r));
    }

    for (Symbol r : argTypeRoleType.rowKeySet()) {
      for (Symbol c : argTypeRoleType.columnKeySet()) {
        if (c != null) {
          log.info("{}.{}: {}", r, c, argTypeRoleType.get(r, c));
        }
      }
    }

    return new ConflictingTypeWarningRule(ImmutableTable.copyOf(argTypeRoleType));
  }

}

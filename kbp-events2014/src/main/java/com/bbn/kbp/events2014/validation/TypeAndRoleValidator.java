package com.bbn.kbp.events2014.validation;

import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.Response;
import com.google.common.base.Charsets;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.concat;

/**
 * Represents a the valid event types and event arugment roles for a KBP
 * Event argument task.  Used as a predicate, returns true iff a response
 * has a valid event type and role.
 */
public final class TypeAndRoleValidator implements Predicate<Response> {
    private static final Logger log = LoggerFactory.getLogger(TypeAndRoleValidator.class);

    private final ImmutableSet<Symbol> alwaysValidRoles;
    private final ImmutableMultimap<Symbol, Symbol> validRoles;

    public boolean isValidEventType(Response response) {
        return validEventTypes().contains(response.type());
    }

    public boolean isValidArgumentRole(Response response) {
        return isValidEventType(response) &&
                (alwaysValidRoles.contains(response.role()) ||
                validRolesFor(response.type()).contains(response.role()));
    }

    public ImmutableSet<Symbol> validEventTypes() {
        return validRoles.keySet();
    }

    public ImmutableSet<Symbol> validRolesFor(Symbol type) {
        checkNotNull(type);
        return ImmutableSet.copyOf(concat(validRoles.get(type), alwaysValidRoles));
    }

    public boolean apply(Response response) {
        return isValidArgumentRole(response);
    }

    public static TypeAndRoleValidator createFromParameters(Parameters params) throws IOException {
        return createFromValidRolesSource(params.getSymbolSet("alwaysValidRoles"),
                Files.asCharSource(params.getExistingFile("validRoles"), Charsets.UTF_8));
    }

    public static TypeAndRoleValidator createFromValidRolesSource(final Iterable<Symbol> alwaysValidRoles,
                                                                CharSource validRolesSource) throws IOException {
        log.info("Validating types and roles against {}", validRolesSource);
        final Multimap<Symbol, Symbol> validRoles = FileUtils.loadSymbolMultimap(validRolesSource);
        log.info("The following roles are always valid: {}", alwaysValidRoles);
        return new TypeAndRoleValidator(alwaysValidRoles, validRoles);
    }

    public static TypeAndRoleValidator create(Iterable<Symbol> alwaysValidRoles,
                                              Multimap<Symbol, Symbol> validRoles)
    {
        return new TypeAndRoleValidator(alwaysValidRoles, validRoles);
    }

    private TypeAndRoleValidator(Iterable<Symbol> alwaysValidRoles,
                                 Multimap<Symbol, Symbol> validRoles)
    {
        this.alwaysValidRoles = ImmutableSet.copyOf(alwaysValidRoles);
        this.validRoles = ImmutableMultimap.copyOf(validRoles);
    }
}

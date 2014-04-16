package com.bbn.kbp.events2014.validation;

import com.bbn.bue.common.StringUtils;
import com.bbn.bue.common.files.FileUtils;
import com.bbn.bue.common.parameters.Parameters;
import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.Response;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

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

    public ImmutableCollection<Symbol> validRolesFor(Symbol type) {
        checkNotNull(type);
        return validRoles.get(type);
    }

    public boolean apply(Response response) {
        return isValidArgumentRole(response);
    }

    public static TypeAndRoleValidator createFromParameters(Parameters params) throws IOException {
        final File validRolesFile = params.getExistingFile("validRoles");
        log.info("Validating types and roles against {}", validRolesFile);
        final Multimap<Symbol, Symbol> validRoles = FileUtils.loadSymbolMultimap(validRolesFile);
        log.info("Got valid roles for {} event types", validRoles.keySet().size());
        final Set<Symbol> alwaysValidRoles = params.getSymbolSet("alwaysValidRoles");
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

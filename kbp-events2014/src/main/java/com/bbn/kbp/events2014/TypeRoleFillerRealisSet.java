package com.bbn.kbp.events2014;

import com.bbn.bue.common.symbols.Symbol;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;

import static com.google.common.base.Preconditions.checkNotNull;

public final class TypeRoleFillerRealisSet {
    private final ImmutableSet<TypeRoleFillerRealis> coreffedArgs;

    private TypeRoleFillerRealisSet(Iterable<TypeRoleFillerRealis> coreffedArgs) {
        this.coreffedArgs = ImmutableSet.copyOf(coreffedArgs);
    }

    public static TypeRoleFillerRealisSet create(Iterable<TypeRoleFillerRealis> coreffedArgs)
    {
        return new TypeRoleFillerRealisSet(coreffedArgs);
    }

    public ImmutableSet<TypeRoleFillerRealis> asSet() {
        return coreffedArgs;
    }

    public static TypeRoleFillerRealisSet from(Iterable<TypeRoleFillerRealis> items) {
        return new TypeRoleFillerRealisSet(items);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(coreffedArgs);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final TypeRoleFillerRealisSet other = (TypeRoleFillerRealisSet) obj;
        return Objects.equal(this.coreffedArgs, other.coreffedArgs);
    }
}

package com.bbn.kbp.events2014;

import com.bbn.bue.common.symbols.Symbol;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;

import static com.google.common.base.Preconditions.checkNotNull;

public final class TypeRoleFillerRealisSet {
    private final Symbol docID;
    private final ImmutableSet<TypeRoleFillerRealis> coreffedArgs;

    private TypeRoleFillerRealisSet(final Symbol docID, Iterable<TypeRoleFillerRealis> coreffedArgs) {
        this.docID = checkNotNull(docID);
        this.coreffedArgs = ImmutableSet.copyOf(coreffedArgs);
    }

    public Symbol docID() {
        return docID;
    }

    public ImmutableSet<TypeRoleFillerRealis> asSet() {
        return coreffedArgs;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(docID, coreffedArgs);
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
        return Objects.equal(this.docID, other.docID) && Objects.equal(this.coreffedArgs, other.coreffedArgs);
    }
}

package com.bbn.kbp.events2014;

import com.bbn.bue.common.symbols.Symbol;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;

import static com.google.common.base.Preconditions.checkNotNull;

public class ResponseLinking {
    private final Symbol docId;
    private final ImmutableSet<ResponseSet> responseSets;

    private ResponseLinking(Symbol docId, Iterable<ResponseSet> responseSets) {
        this.docId = checkNotNull(docId);
        this.responseSets = ImmutableSet.copyOf(responseSets);
    }

    public static ResponseLinking from(Symbol docId, Iterable<ResponseSet> responseSets) {
        return new ResponseLinking(docId, responseSets);
    }

    public Symbol docID() {
        return docId;
    }

    public ImmutableSet<ResponseSet> responseSets() {
        return responseSets;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(docId, responseSets);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final ResponseLinking other = (ResponseLinking) obj;
        return Objects.equal(this.docId, other.docId) && Objects.equal(this.responseSets, other.responseSets);
    }
}

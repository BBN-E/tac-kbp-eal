package com.bbn.kbp.events2014;

import com.bbn.bue.common.symbols.Symbol;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

import static com.google.common.base.Preconditions.checkNotNull;

public class ResponseLinking {
    private final Symbol docId;
    private final ImmutableSet<ResponseSet> responseSets;
    private final ImmutableSet<Response> incompleteResponses;

    private ResponseLinking(Symbol docId, Iterable<ResponseSet> responseSets,
                            Iterable<Response> incompleteResponses)
    {
        this.docId = checkNotNull(docId);
        this.responseSets = ImmutableSortedSet.copyOf(responseSets);
        this.incompleteResponses = ImmutableSortedSet.copyOf(
                Response.byUniqueIdOrdering(), incompleteResponses);
    }

    public static ResponseLinking from(Symbol docId, Iterable<ResponseSet> responseSets,
                                       Iterable<Response> incompleteResponses)
    {
        return new ResponseLinking(docId, responseSets, incompleteResponses);
    }

    public static ResponseLinking createEmpty(Symbol docId) {
        return from(docId, ImmutableList.<ResponseSet>of(), ImmutableList.<Response>of());
    }

    public Symbol docID() {
        return docId;
    }

    public ImmutableSet<ResponseSet> responseSets() {
        return responseSets;
    }

    public ImmutableSet<Response> incompleteResponses() {
        return incompleteResponses;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(docId, responseSets, incompleteResponses);
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
        return Objects.equal(this.docId, other.docId)
                && Objects.equal(this.responseSets, other.responseSets)
                && Objects.equal(this.incompleteResponses, incompleteResponses);
    }
}

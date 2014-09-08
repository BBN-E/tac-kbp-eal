package com.bbn.kbp.events2014;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;

public final class ResponseSet {
    private final ImmutableSet<Response> responses;
    private final int cachedHashCode;

    private ResponseSet(Iterable<Response> responses) {
        this.responses = ImmutableSet.copyOf(responses);
        this.cachedHashCode = computeHashCode();
    }

    public static ResponseSet from(Iterable<Response> responses) {
        return new ResponseSet(responses);
    }

    public ImmutableSet<Response> asSet() {
        return responses;
    }

    @Override
    public int hashCode() {
        return cachedHashCode;
    }

    private int computeHashCode() {
        return Objects.hashCode(responses);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final ResponseSet other = (ResponseSet) obj;
        return Objects.equal(this.responses, other.responses);
    }
}

package com.bbn.kbp.events2014.linking;

import com.bbn.kbp.events2014.AnswerKey;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.KBPRealis;
import com.bbn.kbp.events2014.Response;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;

import static com.google.common.base.Predicates.and;
import static com.google.common.base.Predicates.compose;
import static com.google.common.base.Predicates.in;

class LinkableResponseFilter2015 implements AnswerKey.Filter {
    private static final ImmutableSet<KBPRealis> linkableRealises =
            ImmutableSet.of(KBPRealis.Actual, KBPRealis.Other);

    private static final Predicate<Response> realisIsLinkable =
            compose(in(linkableRealises), Response.realisFunction());
    private static final Predicate<AssessedResponse> responsesRealisIsLinkable =
            compose(realisIsLinkable, AssessedResponse.Response);

    @Override
    public Predicate<AssessedResponse> assessedFilter() {
        // throw out wrong answers and generic answers
        return and(AssessedResponse.IsCorrectUpToInexactJustifications,
                responsesRealisIsLinkable);
    }

    @Override
    public Predicate<Response> unassessedFilter() {
        // unassessed answers cannot be linked
        return Predicates.alwaysFalse();
    }
}

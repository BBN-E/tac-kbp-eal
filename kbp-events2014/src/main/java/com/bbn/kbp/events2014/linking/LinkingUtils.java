package com.bbn.kbp.events2014.linking;

import com.bbn.kbp.events2014.AnswerKey;

public final class LinkingUtils {
    private LinkingUtils() {
        throw new UnsupportedOperationException();
    }

    public static final LinkableResponseFilter2015 linkableResponseFilter2015 =
            new LinkableResponseFilter2015();

    public static AnswerKey.Filter linkableResponseFilter2015() {
        return linkableResponseFilter2015;
    }
}

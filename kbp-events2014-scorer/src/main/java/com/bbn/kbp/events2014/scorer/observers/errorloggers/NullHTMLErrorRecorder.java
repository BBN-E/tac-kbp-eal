package com.bbn.kbp.events2014.scorer.observers.errorloggers;

import com.bbn.bue.common.scoring.Scored;
import com.bbn.kbp.events2014.AsssessedResponse;
import com.bbn.kbp.events2014.Response;

import java.util.Set;

/**
 * A do-nothing implementation of HTMLErrorRecorder
 */
public final class NullHTMLErrorRecorder implements HTMLErrorRecorder {
    private NullHTMLErrorRecorder() {}
    public static NullHTMLErrorRecorder getInstance() {
        return new NullHTMLErrorRecorder();
    }


    @Override
    public String preamble() {
        return "";
    }

    @Override
    public String vsAnnotated(String clazz, String title, Response headerResponse, Scored<Response> response, AsssessedResponse annotatedResponse) {
        return "";
    }

    @Override
    public String vsAnnotated(String clazz, String title, Response response, Set<AsssessedResponse> annotatedResponses) {
        return "";
    }

    @Override
    public String vsAnnotated(String clazz, String title, Response headerResponse,
                              Iterable<Scored<Response>> scoredResponses, Iterable<AsssessedResponse> annotatedResponses)
    {
        return "";
    }
}

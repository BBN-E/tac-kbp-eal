package com.bbn.kbp.events2014.scorer.observers.errorloggers;

import com.bbn.bue.common.scoring.Scored;
import com.bbn.kbp.events2014.AssessedResponse;
import com.bbn.kbp.events2014.Response;

import java.util.Set;

/**
 * Some observers would like to write HTML output for easy browsing. However, BBN's internal
 * implementation has many dependencies on internal Serif code which can't be distributed,
 * so for public release we just provide an interface with a null implementation. Someone else
 * could write another implementation if they wanted to. This is a bit of a hack.
 */
public interface HTMLErrorRecorder {
    public String preamble();
    public String vsAnnotated(final String clazz, final String title, final Response headerResponse,
                              final Scored<Response> response, final AssessedResponse annotatedResponse);

    public String vsAnnotated(String clazz, String title, Response response, Set<AssessedResponse> annotatedResponses);

    public String vsAnnotated(String clazz, String title, Response headerResponse, Iterable<Scored<Response>> scoredResponses,
                              Iterable<AssessedResponse> annotatedResponses);
}

package com.bbn.com.bbn.kbp.events2014.assessmentDiff.diffLoggers;

import com.bbn.bue.common.symbols.Symbol;
import com.bbn.kbp.events2014.Response;

/**
* Created by rgabbard on 5/20/14.
*/
public final class BasicDiffLogger implements DiffLogger {

    @Override
    public void logDifference(Response response,
                              Symbol leftKey, Symbol rightKey, StringBuilder out)
    {
        out.append(leftKey).append(" vs ").append(rightKey).append("\n")
                .append(response.toString()).append("\n");
    }
}

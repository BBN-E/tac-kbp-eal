package com.bbn.com.bbn.kbp.events2014.assessmentDiff.observers;

import com.bbn.com.bbn.kbp.events2014.assessmentDiff.KBPAssessmentDiff;
import com.bbn.com.bbn.kbp.events2014.assessmentDiff.diffLoggers.DiffLogger;
import com.bbn.kbp.events2014.Response;
import com.bbn.kbp.events2014.ResponseAssessment;

import java.io.File;
import java.io.IOException;

/**
* Created by rgabbard on 5/20/14.
*/
public interface AssessmentPairObserver {
    public void observe(Response response, ResponseAssessment left, ResponseAssessment right);
    public void finish(DiffLogger diffLogger, File outputDir) throws IOException;
}

package com.bbn.kbp.events2014.io;

import com.bbn.kbp.events2014.CorpusEventLinking;

import com.google.common.io.CharSource;

import java.io.IOException;

public interface CorpusEventFrameLoader {

  CorpusEventLinking loadCorpusEventFrames(CharSource source) throws IOException;
}


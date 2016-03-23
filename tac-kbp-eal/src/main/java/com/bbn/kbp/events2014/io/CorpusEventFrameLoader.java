package com.bbn.kbp.events2014.io;

import com.bbn.kbp.events2014.CorpusEventFrame;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;

import java.io.IOException;

public interface CorpusEventFrameLoader {

  ImmutableSet<CorpusEventFrame> loadCorpusEventFrames(CharSource source) throws IOException;
}


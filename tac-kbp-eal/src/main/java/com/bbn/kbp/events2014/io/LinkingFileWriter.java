package com.bbn.kbp.events2014.io;

import com.bbn.kbp.events2014.ResponseLinking;

import com.google.common.io.CharSink;

import java.io.IOException;

/**
 * Created by rgabbard on 6/26/17.
 */
interface LinkingFileWriter {

  void write(ResponseLinking linking, CharSink sink) throws IOException;
}

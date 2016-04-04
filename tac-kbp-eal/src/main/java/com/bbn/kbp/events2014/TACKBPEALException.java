package com.bbn.kbp.events2014;

public class TACKBPEALException extends RuntimeException {

  public TACKBPEALException(String msg) {
    super(msg);
  }

  public TACKBPEALException(String msg, Exception e) {
    super(msg, e);
  }

  public TACKBPEALException(Exception e) {
    super(e);
  }
}


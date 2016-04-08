package com.bbn.kbp;

public final class TACException extends RuntimeException {

  public TACException(String msg) {
    super(msg);
  }

  public TACException(String msg, Throwable cause) {
    super(msg, cause);
  }

  public TACException(Throwable cause) {
    super(cause);
  }
}

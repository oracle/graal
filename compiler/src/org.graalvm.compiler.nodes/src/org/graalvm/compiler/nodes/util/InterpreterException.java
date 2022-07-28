package org.graalvm.compiler.nodes.util;

public class InterpreterException extends RuntimeException {
  public InterpreterException(Throwable cause) {
    super(cause);
  }
}

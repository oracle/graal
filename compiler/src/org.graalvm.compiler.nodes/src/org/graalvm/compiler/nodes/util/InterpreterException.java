package org.graalvm.compiler.nodes.util;

/**
 * This excpetion is thrown when the IR execution throws an exception, it is used
 * to distinguish IR exception with the exception caused by the interpreter malfunciton.
 */
public class InterpreterException extends RuntimeException {
  public InterpreterException(Throwable cause) {
    super(cause);
  }
}

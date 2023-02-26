package org.graalvm.compiler.nodes.util;

/**
 * This exception is thrown when the IR execution throws an exception, it is used
 * to distinguish IR exceptions from exceptions caused by interpreter malfunctions.
 */
@SuppressWarnings("serial")
public class InterpreterException extends RuntimeException {
  public InterpreterException(Throwable cause) {
    super(cause);
  }
}

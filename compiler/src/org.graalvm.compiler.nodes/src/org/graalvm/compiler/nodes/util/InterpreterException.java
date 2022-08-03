package org.graalvm.compiler.nodes.util;

/**
 * This excpetion is thrown when the IR execution throws an exception, it is used
 * to distinguish IR exception with the exception caused by the interpreter malfunciton.
 * 
 * 加上这个异常是为了区分 interpter 在遇到 IR 执行出错时抛出的异常，和 interpter 本身由于出现 bug
 * 而出现异常的行为。例如在 LoadFieldNode 里，如果 object 是 null，那么就无法获取它的 field 的值，
 * 这时要抛出 NullPointerException，但是如果直接抛出 NullPointerException 的话，就无法区分是由于
 * interpter 的bug而出现的异常，还是 interpter主动抛出的异常，所以在 LoadFieldNode 里面会把 
 * NullPointerException 包在 InterpeterException 里面，在 GraalCompilerTest 里面会捕获
 * ItnerpterException。
 */
public class InterpreterException extends RuntimeException {
  public InterpreterException(Throwable cause) {
    super(cause);
  }
}

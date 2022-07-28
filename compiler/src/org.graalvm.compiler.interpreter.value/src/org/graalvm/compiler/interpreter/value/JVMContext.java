package org.graalvm.compiler.interpreter.value;

import java.lang.invoke.MethodHandles;

public interface JVMContext {
  ClassLoader getClassLoader();
  MethodHandles.Lookup getLookup();
}
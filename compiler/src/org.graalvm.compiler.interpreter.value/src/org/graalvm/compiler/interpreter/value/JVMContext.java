package org.graalvm.compiler.interpreter.value;

import java.lang.invoke.MethodHandles;

/**
 * GraalInterpreterTest and classes in this directory belongs to different modules,
 * so InterpreterValueMutableObject cannot load class in GraalInterpreterTest with
 * its own class loader, we need to pass the class loader of GraalInterpreterTest
 * and MethodHandles.Lookup instance instantiated in GraalInterpreterTest to
 * GraalInterpreterTest to enable it to load classes in GraalInterpreterTest and
 * access object fields with VarHandles.
 * 
 * This interface must be put here, or it will cause cyclic reference between modules.
 * 
 */
public interface JVMContext {
  ClassLoader getClassLoader();
  MethodHandles.Lookup getLookup();
}
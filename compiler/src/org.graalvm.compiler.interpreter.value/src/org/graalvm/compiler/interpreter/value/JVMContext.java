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
 * GraalInterpreterTest 和这个文件夹里的类属于不同的module，不同的module有不同的 class
 * loader 和 Lookup，而且一个 module 的 class loader 不能加载另一个 module 的 class，
 * 所以这个文件夹里的 InterpreterValueMutableObject 不能直接用它自己的 class loader 加载
 * GraalInterpreterTest 里面的测试类，需要额外定义这个接口来把 GraalInterpreterTest
 * 的 class loader 和 Lookup 传给 InterpreterValueMutableObject。
 * 
 * 这个接口会在 GraalInterpreter 里由 JVMContextImpl 类实现。这个接口只能放在这里，如果放在其他地方，
 * 比如 org.graalvm.compiler.core，会造成循环依赖：compiler.core 依赖 interpter.value，同时
 * interpter.value 依赖 compiler.core
 */
public interface JVMContext {
  ClassLoader getClassLoader();
  MethodHandles.Lookup getLookup();
}
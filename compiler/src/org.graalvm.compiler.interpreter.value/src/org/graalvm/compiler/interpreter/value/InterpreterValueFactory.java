package org.graalvm.compiler.interpreter.value;

import jdk.vm.ci.meta.ResolvedJavaType;

public interface InterpreterValueFactory {
    InterpreterValueObject createObject(ResolvedJavaType type);

    InterpreterValueArray createArray(ResolvedJavaType elementalType, int length);

    InterpreterValueArray createMultiArray(ResolvedJavaType elementalType, int[] dimensions);

    InterpreterValue createFromObject(Object value);
}

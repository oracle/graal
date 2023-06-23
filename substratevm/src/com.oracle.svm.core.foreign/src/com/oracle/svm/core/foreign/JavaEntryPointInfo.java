package com.oracle.svm.core.foreign;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Objects;

public class JavaEntryPointInfo {
    private final MethodType methodType;

    public JavaEntryPointInfo(MethodType methodType) {
        this.methodType = methodType;
    }

    public MethodType cMethodType() {
        return methodType;
    }

    public MethodType javaMethodType() {
        return methodType.insertParameterTypes(0, MethodHandle.class);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        JavaEntryPointInfo that = (JavaEntryPointInfo) o;
        return Objects.equals(methodType, that.methodType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodType);
    }
}

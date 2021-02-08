package com.oracle.truffle.espresso._native;

import static com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import java.util.Arrays;
import java.util.Objects;

public final class NativeSignature {

    private final NativeType returnType;
    @CompilationFinal(dimensions = 1) //
    private final NativeType[] parameterTypes;

    public NativeSignature(NativeType returnType, NativeType[] parameterTypes) {
        this.returnType = Objects.requireNonNull(returnType);
        this.parameterTypes = Objects.requireNonNull(parameterTypes);
        for (int i = 0; i < parameterTypes.length; i++) {
            NativeType param = parameterTypes[i];
            if (param == NativeType.VOID) {
                throw new IllegalArgumentException("Invalid VOID parameter");
            }
        }
    }

    public NativeType getReturnType() {
        return returnType;
    }

    public NativeType[] getParameterTypes() {
        return parameterTypes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NativeSignature)) {
            return false;
        }
        NativeSignature that = (NativeSignature) other;
        return returnType == that.returnType && Arrays.equals(parameterTypes, that.parameterTypes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(returnType);
        result = 31 * result + Arrays.hashCode(parameterTypes);
        return result;
    }

    @Override
    public String toString() {
        return Arrays.toString(parameterTypes) + " : " + returnType;
    }
}

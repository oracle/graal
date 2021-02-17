package com.oracle.truffle.espresso._native;

/**
 * Represents a valid native signature.
 */
public interface NativeSignature {
    /**
     * Return type.
     */
    NativeType getReturnType();

    /**
     * Number of parameters.
     */
    int getParameterCount();

    /**
     * Returns the i-th (0-based) parameter type, guaranteed to be != {@link NativeType#VOID void}.
     *
     * @throws IndexOutOfBoundsException if the index is negative or >= {@link #getParameterCount()}
     */
    NativeType parameterTypeAt(int index);

    static NativeSignature create(NativeType returnType, NativeType... parameterTypes) {
        return new NativeSignatureImpl(returnType, parameterTypes);
    }
}

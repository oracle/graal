package com.oracle.truffle.api.operation;

import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

public class MetadataKey<T> {
    private final T defaultValue;
    @CompilationFinal private Function<OperationNode, T> getter;

    public MetadataKey(T defaultValue) {
        this.defaultValue = defaultValue;
    }

    void setGetter(Function<OperationNode, T> getter) {
        this.getter = getter;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public T getValue(OperationNode node) {
        if (getter == null) {
            throw new ClassCastException();
        }

        T value = getter.apply(node);
        if (value == null) {
            return defaultValue;
        } else {
            return value;
        }
    }
}

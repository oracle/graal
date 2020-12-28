package com.oracle.truffle.api.operation;

import java.util.NoSuchElementException;

public abstract class OperationPointer {

    protected OperationPointer() {
    }

    public abstract boolean parent();

    public abstract void child(int childIndex);

    public abstract int childCount();

    public abstract boolean isValid();

    public abstract Class<?> get() throws NoSuchElementException;

    public abstract Operation.Kind getKind() throws NoSuchElementException;

    public abstract <T> T getConstant(Class<T> expectedClass, int constantIndex) throws NoSuchElementException;

}
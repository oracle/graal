package com.oracle.truffle.api.operation;

public abstract class OperationsBuilder {

    public abstract OperationsNode build();

    public abstract void reset();
}

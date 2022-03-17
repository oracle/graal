package com.oracle.truffle.api.operation;

import java.util.function.Supplier;

import com.oracle.truffle.api.source.Source;

public abstract class OperationsBuilder {

    public abstract OperationsNode build();

    public abstract void reset();

    public abstract OperationsNode[] collect();

    public abstract void beginSource(Source source);

    public abstract void beginSource(Supplier<Source> supplier);

    public abstract void endSource();

    public abstract void beginSourceSection(int start);

    public abstract void endSourceSection(int length);
}

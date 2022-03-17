package com.oracle.truffle.api.operation;

import java.util.function.Supplier;

import com.oracle.truffle.api.source.Source;

public abstract class OperationsBuilder {

    public abstract OperationsNode build();

    public abstract void reset();

    public abstract OperationsNode[] collect();

    protected String nodeName = null;
    protected boolean isInternal = false;

    public final void setNodeName(String nodeName) {
        if (this.nodeName != null) {
            throw new IllegalStateException("Node name already set");
        }
        this.nodeName = nodeName;
    }

    public final void setInternal() {
        if (isInternal) {
            throw new IllegalStateException("isInternal already set");
        }
        isInternal = true;
    }

    public abstract void beginSource(Source source);

    public abstract void beginSource(Supplier<Source> supplier);

    public abstract void endSource();

    public abstract void beginSourceSection(int start);

    public abstract void endSourceSection(int length);
}

package com.oracle.truffle.api.operation;

import com.oracle.truffle.api.frame.MaterializedFrame;

public final class ContinuationResult {
    private final ContinuationLocation location;
    private final MaterializedFrame frame;
    private final Object result;

    ContinuationResult(ContinuationLocation location, MaterializedFrame frame, Object result) {
        this.location = location;
        this.frame = frame;
        this.result = result;
    }

    public Object continueWith(Object value) {
        return location.getRootNode().getCallTarget().call(frame, value);
    }

    public Object getResult() {
        return result;
    }
}

package com.oracle.truffle.api.operation;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public abstract class ContinuationLocation {
    public abstract RootNode getRootNode();

    // todo: create accessor
    public final ContinuationResult createResult(VirtualFrame frame, Object result) {
        return new ContinuationResult(this, frame.materialize(), result);
    }
}

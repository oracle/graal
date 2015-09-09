package com.oracle.graal.microbenchmarks.graal.util;

import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.common.*;

public class FrameStateAssignmentState extends GraphState {

    public FrameStateAssignmentPhase phase;

    @Override
    protected StructuredGraph preprocessOriginal(StructuredGraph graph) {
        new GuardLoweringPhase().apply(graph, null);
        return graph;
    }

    @Override
    public void beforeInvocation() {
        phase = new FrameStateAssignmentPhase();
        super.beforeInvocation();
    }
}

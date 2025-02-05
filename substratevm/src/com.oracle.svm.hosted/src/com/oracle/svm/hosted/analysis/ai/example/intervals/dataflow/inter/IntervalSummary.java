package com.oracle.svm.hosted.analysis.ai.example.intervals.dataflow.inter;

import com.oracle.svm.hosted.analysis.ai.domain.IntInterval;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

import java.util.Collections;
import java.util.List;

public record IntervalSummary(IntInterval preCondition, IntInterval postCondition,
                              List<IntInterval> actualArguments) implements Summary<IntInterval> {

    @Override
    public boolean subsumes(Summary<IntInterval> other) {
        if (!(other instanceof IntervalSummary)) {
            return false;
        }

        for (int i = 0; i < actualArguments.size(); i++) {
            if (!actualArguments.get(i).equals(other.getActualArguments().get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void applySummary(Invoke invoke, Node invokeNode, AbstractStateMap<IntInterval> callerStateMap) {
        callerStateMap.setPostCondition(invokeNode, postCondition);
    }

    @Override
    public List<IntInterval> getActualArguments() {
        return Collections.unmodifiableList(actualArguments);
    }

}

package com.oracle.svm.hosted.analysis.ai.interpreter.node;

import com.oracle.svm.hosted.analysis.ai.domain.IntInterval;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import jdk.graal.compiler.graph.Node;

public class IntIntervalNodeInterpreter implements NodeInterpreter<IntInterval> {

    @Override
    public void execEdge(Node source, Node destination, AbstractStateMap<IntInterval> abstractStateMap) {

    }

    @Override
    public void execNode(Node node, AbstractStateMap<IntInterval> abstractStateMap) {

    }
}

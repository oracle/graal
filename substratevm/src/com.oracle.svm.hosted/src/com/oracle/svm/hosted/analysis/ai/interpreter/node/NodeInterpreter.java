package com.oracle.svm.hosted.analysis.ai.interpreter.node;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import jdk.graal.compiler.graph.Node;

public interface NodeInterpreter<Domain extends AbstractDomain<Domain>> {

    void execEdge(Node source, Node destination, AbstractStateMap<Domain> abstractStateMap);

    void execNode(Node node, AbstractStateMap<Domain> abstractStateMap);
}

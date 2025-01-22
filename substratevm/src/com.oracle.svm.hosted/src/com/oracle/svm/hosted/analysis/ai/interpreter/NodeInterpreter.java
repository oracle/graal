package com.oracle.svm.hosted.analysis.ai.interpreter;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import jdk.graal.compiler.graph.Node;

/**
 * Interface for interpreting the effects of nodes and edges of Graal IR
 * in the abstract interpretation.
 *
 * @param <Domain> type of the derived {@link AbstractDomain}
 */
public interface NodeInterpreter<Domain extends AbstractDomain<Domain>> {

    void execEdge(Node source, Node destination, AbstractStateMap<Domain> abstractStateMap);

    void execNode(Node node, AbstractStateMap<Domain> abstractStateMap);
}

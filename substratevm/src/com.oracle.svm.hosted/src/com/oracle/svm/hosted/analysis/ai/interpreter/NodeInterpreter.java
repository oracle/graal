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

    /**
     * Execute the edge between two nodes in a CFG of a single method.
     * This method should update the pre-condition of the destination node according to the post-condition of the source.
     *
     * @param source      the node from which the edge originates
     * @param destination the node to which the edge goes
     * @param abstractStateMap current state of the analysis
     * @return the updated abstract domain of the destination node
     */
    Domain execEdge(Node source, Node destination, AbstractStateMap<Domain> abstractStateMap);

    /**
     * Execute the given node in the CFG of a single method.
     * This method should compute the post-condition of the node according to the given Graal IR {@link Node}
     * .
     * @param node the node to analyze
     * @param abstractStateMap current state of the analysis
     * @return the updated abstract domain of the node
     */
    Domain execNode(Node node, AbstractStateMap<Domain> abstractStateMap);
}

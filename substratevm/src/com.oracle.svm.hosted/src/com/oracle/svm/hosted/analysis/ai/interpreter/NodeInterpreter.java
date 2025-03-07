package com.oracle.svm.hosted.analysis.ai.interpreter;

import com.oracle.svm.hosted.analysis.ai.analyzer.call.InvokeCallBack;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import jdk.graal.compiler.graph.Node;

/**
 * This interface provides functionality for executing operations
 * on nodes and edges within a control flow graph (CFG) during program analysis.
 * This interface leverages abstract interpretation techniques to compute or
 * update abstract states associated with nodes and edges in the CFG.
 *
 * @param <Domain> type of the derived {@link AbstractDomain} used in the analysis
 */
public interface NodeInterpreter<Domain extends AbstractDomain<Domain>> {

    /**
     * Execute the edge between two nodes in a CFG of a single analysisMethod.
     * NOTE:
     * This analysisMethod should update the pre-condition of the destination node according to the post-condition of the source.
     *
     * @param source      the node from which the edge originates
     * @param destination the node to which the edge goes
     * @param abstractStateMap of the analyzed method
     * @return the updated abstract domain of the destination node
     */
    Domain execEdge(Node source, Node destination, AbstractStateMap<Domain> abstractStateMap);

    /**
     * Execute the given node in the CFG of a single analysisMethod.
     * NOTE: This analysisMethod should compute the post-condition of the node according to the given Graal IR {@link Node}.
     * @param node to analyze
     * @param abstractStateMap of the analyzed method
     * @param invokeCallBack callback that can be used to analyze the summary of invokes
     * @return the updated post-condition of the node
     */
    Domain execNode(Node node, AbstractStateMap<Domain> abstractStateMap, InvokeCallBack<Domain> invokeCallBack);
}

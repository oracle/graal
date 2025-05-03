package com.oracle.svm.hosted.analysis.ai.interpreter;

import com.oracle.svm.hosted.analysis.ai.analyzer.call.InvokeCallBack;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
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
     * This method should update the pre-condition of the destination node according to the post-condition of the source.
     *
     * @param source        the node from which the edge originates
     * @param target        the node to which the edge goes
     * @param abstractState of the analyzed method
     * @return the updated pre-condition of the target node
     */
    Domain execEdge(Node source, Node target, AbstractState<Domain> abstractState);

    /**
     * Execute the given node in the CFG of a single analysisMethod.
     * NOTE:
     * This method should update the pre-condition of the destination node according to the post-condition of the source.
     *
     * @param node           to analyze
     * @param abstractState  of the analyzed method
     * @param invokeCallBack callback that can be used to analyze the summary of invokes
     * @return the updated post-condition of the target node
     */
    Domain execNode(Node node, AbstractState<Domain> abstractState, InvokeCallBack<Domain> invokeCallBack);
}

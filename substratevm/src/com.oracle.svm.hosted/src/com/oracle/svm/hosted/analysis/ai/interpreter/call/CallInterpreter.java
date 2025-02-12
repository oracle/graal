package com.oracle.svm.hosted.analysis.ai.interpreter.call;

import com.oracle.svm.hosted.analysis.ai.analyzer.payload.AnalysisPayload;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

/**
 * Interface for interpreting invokes within the context of abstract interpretation.
 *
 * @param <Domain> type of the derived {@link AbstractDomain}
 */
public interface CallInterpreter<Domain extends AbstractDomain<Domain>> {

    /**
     * Execute the given invoke in the CFG of a single method.
     *
     * @param invoke the invoke information
     * @param invokeNode the node
     * @param abstractStateMap the current state of the analysis
     * @param payload the analysis payload
     */
    void execInvoke(Invoke invoke,
                    Node invokeNode,
                    AbstractStateMap<Domain> abstractStateMap,
                    AnalysisPayload<Domain> payload);
}
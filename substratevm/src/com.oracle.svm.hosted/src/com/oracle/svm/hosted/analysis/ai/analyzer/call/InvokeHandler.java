package com.oracle.svm.hosted.analysis.ai.analyzer.call;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalysisOutcome;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

/**
 * Interface for analyzing invokes in the context of abstract interpretation.
 *
 * @param <Domain> type of the derived {@link AbstractDomain} used in the analysis
 */
public interface InvokeHandler<Domain extends AbstractDomain<Domain>> {

    /**
     * Handles the invocation of a method during abstract interpretation.
     * This method should update the post-condition of the {@param invokeNode}.
     *
     * @param invoke      the representation of the invocation to be handled
     * @param invokeNode  the graph node corresponding to the invocation
     * @param callerState the abstract state of the caller at the point of the {@param invoke}
     * @return the outcome of the analysis of the call (status, + summary if status is ok)
     */
    AnalysisOutcome<Domain> handleInvoke(Invoke invoke,
                                         Node invokeNode,
                                         AbstractState<Domain> callerState);

    /**
     * The starting point of the analysis.to md
     * We receive an {@link AnalysisMethod} and we start our abstract interpretation from this analysisMethod as the starting point.
     *
     * @param root the root {@link AnalysisMethod} that the abstract interpretation analysis starts from
     */
    void handleRootInvoke(AnalysisMethod root);
}

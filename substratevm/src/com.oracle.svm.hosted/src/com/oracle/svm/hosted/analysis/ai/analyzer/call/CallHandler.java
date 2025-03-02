package com.oracle.svm.hosted.analysis.ai.analyzer.call;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalysisOutcome;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

/**
 * Interface for analyzing methods the context of abstract interpretation.
 *
 * @param <Domain> type of the derived {@link AbstractDomain} used in the analysis
 */
public interface CallHandler<Domain extends AbstractDomain<Domain>> {

    /**
     * Handles the invocation of a analysisMethod during abstract interpretation.
     * This analysisMethod should update the post-condition of the {@param invokeNode}.
     * In intra-procedural analysis, the post-condition is set to the top value of the abstract domain.
     * In inter-procedural analysis, we try to fetch a summary for this analysisMethod, possibly computing it in the process,
     * and apply this summary to the abstract state at the call site ({@param invokeNode}).
     *
     * @param invoke the representation of the call to be handled
     * @param invokeNode the graph node corresponding to the call invocation
     * @param callerStateMap the abstract context of the caller at the point of the {@param invoke}
     * @return the outcome of the analysis of the call ( status, + summary if status is ok )
     */
    AnalysisOutcome<Domain> handleCall(Invoke invoke,
                                       Node invokeNode,
                                       AbstractStateMap<Domain> callerStateMap);

    /**
     * The starting point of the analysis.to md
     * We receive an {@link AnalysisMethod} and we start our abstract interpretation from this analysisMethod as the starting point.
     *
     * @param root the root {@link AnalysisMethod} that the abstract interpretation starts from
     * @param debug the debug context for the analysis
     */
    void handleRootCall(AnalysisMethod root, DebugContext debug);
}

package com.oracle.svm.hosted.analysis.ai.analyzer.call;

import com.oracle.svm.hosted.analysis.ai.analyzer.AnalysisOutcome;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

/**
 * Callback interface for handling method invocations during abstract interpretation.
 * Developers can use this callback when they want to analyze the effects of method calls,
 *
 * @param <Domain> the type of the derived {@link AbstractDomain} used in the analysis
 */
@FunctionalInterface
public interface InvokeCallBack<Domain extends AbstractDomain<Domain>> {

    /**
     * Handles the invocation of a method during abstract interpretation.
     *
     * @param invoke        the representation of the invocation to be handled
     * @param node          the graph node corresponding to the invocation
     * @param abstractState the abstract state of the caller at the point of the {@param invoke}
     * @return the outcome of the analysis of the call (status, + summary if status is ok)
     */
    AnalysisOutcome<Domain> handleInvoke(Invoke invoke, Node node, AbstractState<Domain> abstractState);
}
